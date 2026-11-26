package shina.wallwarRebirth.world

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import org.slf4j.LoggerFactory
import shina.wallwarRebirth.rules.MatchRules
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

internal object ArenaOres {
    private const val STARTER_PER_ORE = 16
    private const val STARTER_DISTANCE_GAP = 2
    private const val SCATTER_DISTANCE_GAP = 4
    private const val CENTER_DISTANCE_GAP = 4
    private const val LANDMARK_SEARCH_MARGIN = 8
    private const val REACTOR_REACH_SQUARED =
        MatchRules.MINERAL_REACTOR_CAPTURE_RANGE * MatchRules.MINERAL_REACTOR_CAPTURE_RANGE
    private val logger = LoggerFactory.getLogger("wallwar-rebirth")

    data class Corner(val oreX: Int, val oreZ: Int, val reactorX: Int, val reactorZ: Int)

    /** Keep the default amount at 160 blocks and round to complete reflected two-layer columns. */
    fun scaledAmount(base: Int, size: Int): Int = (base * size / (MatchRules.MAP_SIZE * 4.0)).roundToInt() * 4

    fun corners(min: Int, max: Int, size: Int): List<Corner> {
        val inset = MatchRules.BASE_INSET
        val reactorMargin = MatchRules.MINERAL_REACTOR_RADIUS
        val radius = radius(scaledAmount(MatchRules.CORNER_ORE_IRON + MatchRules.CORNER_ORE_COPPER, size))
        val gap = radius + reactorMargin + 1
        val reactorX = (min + inset - gap).coerceAtLeast(min + reactorMargin)
        val reactorZ = min + max - reactorX
        var oreX = min + inset
        while (hypot((oreX - reactorX).toDouble(), (oreX - reactorX).toDouble()) < gap) oreX++
        val oreZ = min + max - oreX
        return listOf(Corner(oreX, oreZ, reactorX, reactorZ), Corner(oreZ, oreX, reactorZ, reactorX))
    }

    fun generate(
        level: ServerLevel,
        size: Int,
        seed: Long,
        redBase: BlockPos,
        blueBase: BlockPos,
        redSpawn: BlockPos,
        blueSpawn: BlockPos,
        corners: List<Corner>,
        reactors: List<BlockPos>,
    ): Boolean = Plan(level, size, seed, redBase, blueBase, redSpawn, blueSpawn).generate(corners, reactors)

    private fun radius(blocks: Int): Int = ceil(sqrt(blocks / (2.0 * Math.PI))).toInt()

    private class Plan(
        private val level: ServerLevel,
        private val size: Int,
        private val seed: Long,
        private val redBase: BlockPos,
        private val blueBase: BlockPos,
        redSpawn: BlockPos,
        blueSpawn: BlockPos,
    ) {
        private val min = -size / 2 + 1
        private val max = size / 2 - 2
        private val divider = min + max
        private val surface = ArenaRoutes(level, min, max)
        private val redStart = surface.cell(redSpawn.x, redSpawn.z)
        private val blueStart = surface.cell(blueSpawn.x, blueSpawn.z)
        private val random = Random(seed xor 0x4F524553L)
        private val protected = HashSet<Int>()
        private val used = HashSet<Int>()
        private val deposits = ArrayList<Deposit>()
        private val approaches = ArrayList<Approach>()
        private var redPaths = surface.pathsFrom(redStart)
        private var bluePaths = surface.pathsFrom(blueStart)

        private data class Deposit(val floor: BlockPos, val ore: Block, val stacked: Boolean)
        private data class Approach(val name: String, val red: Int, val blue: Int, val tolerance: Int)

        fun generate(corners: List<Corner>, reactors: List<BlockPos>): Boolean {
            protectBase(redBase)
            protectBase(blueBase)
            for (core in reactors) reserve(core.x, core.z, MatchRules.MINERAL_REACTOR_RADIUS)

            val centerX = Math.floorDiv(divider, 2)
            val centerZ = divider - centerX
            val centerIron = scaledAmount(MatchRules.CENTER_ORE_IRON, size)
            val centerCopper = scaledAmount(MatchRules.CENTER_ORE_COPPER, size)
            val entry = radius(centerIron + centerCopper) + 1
            val west = (-1..2).map { surface.cell(centerX - entry, centerZ + it) }
            val north = (-1..2).map { surface.cell(centerX + it, centerZ - entry) }
            if (!approach("center-west", west, CENTER_DISTANCE_GAP) ||
                !approach("center-north", north, CENTER_DISTANCE_GAP)
            ) return fail("central approaches")

            for (core in reactors) {
                val target = Vec3.atCenterOf(core)
                val cells = ArrayList<Int>()
                val margin = MatchRules.MINERAL_REACTOR_RADIUS
                for (x in core.x - margin..core.x + margin) {
                    for (z in core.z - margin..core.z + margin) {
                        if (!surface.inside(x, z) || x + z >= divider) continue
                        val cell = surface.cell(x, z)
                        if (Vec3.atBottomCenterOf(surface.ground(cell).above()).distanceToSqr(target) > REACTOR_REACH_SQUARED) continue
                        val other = surface.opposite(cell)
                        if (Vec3.atBottomCenterOf(surface.ground(other).above()).distanceToSqr(target) > REACTOR_REACH_SQUARED) continue
                        cells.add(cell)
                    }
                }
                if (!approach("reactor-${core.x}-${core.z}", cells, null)) return fail("reactor approaches")
            }

            used.addAll(protected)
            val cornerIron = scaledAmount(MatchRules.CORNER_ORE_IRON, size)
            val cornerCopper = scaledAmount(MatchRules.CORNER_ORE_COPPER, size)
            if (!landmark(centerX, centerZ, centerIron, centerCopper)) return fail("central ores")
            for (corner in corners) {
                if (!landmark(corner.oreX, corner.oreZ, cornerIron, cornerCopper)) return fail("corner ores")
            }

            val redRear = rearCells(redBase, redPaths.distances, towardMin = true)
            val blueRear = rearCells(blueBase, bluePaths.distances, towardMin = false)
            val starterFrom = deposits.size
            for (ore in listOf(Blocks.IRON_ORE, Blocks.COPPER_ORE)) {
                if (veins(redRear, blueRear, ore, STARTER_PER_ORE, STARTER_DISTANCE_GAP) != STARTER_PER_ORE) {
                    return fail("starter ores")
                }
            }
            val starter = deposits.subList(starterFrom, deposits.size).toList()
            // Rear deposits are the guaranteed start. Scatter ores have their own budget.
            val reds = bands(redPaths.distances, red = true)
            val blues = bands(bluePaths.distances, red = false)
            val scatter = ArrayList<Int>(reds.size)
            for (band in reds.indices) {
                val quota = minOf(reds[band].size, blues[band].size) / (2 * MatchRules.SURFACE_ORE_EVERY)
                val iron = veins(reds[band], blues[band], Blocks.IRON_ORE, quota, SCATTER_DISTANCE_GAP)
                val copper = veins(reds[band], blues[band], Blocks.COPPER_ORE, quota, SCATTER_DISTANCE_GAP)
                scatter.add(iron + copper)
            }

            if (!place()) return fail("ore blocks")
            val red = surface.pathsFrom(redStart).distances
            val blue = surface.pathsFrom(blueStart).distances
            if (approaches.any { red[it.red] < 0 || blue[it.blue] < 0 }) return fail("final walking routes")
            val starterRoutes = listOf(Blocks.IRON_ORE, Blocks.COPPER_ORE).map { ore ->
                val pairs = starter.filter { it.ore == ore }.chunked(2)
                val redDistance = pairs.minOfOrNull { red[surface.cell(it[0].floor.x, it[0].floor.z)] } ?: -1
                val blueDistance = pairs.minOfOrNull { blue[surface.cell(it[1].floor.x, it[1].floor.z)] } ?: -1
                if (redDistance < 0 || blueDistance < 0 || abs(redDistance - blueDistance) > STARTER_DISTANCE_GAP) {
                    return fail("final starter routes")
                }
                "$redDistance/$blueDistance"
            }
            val distances = approaches.joinToString { "${it.name}=${red[it.red]}/${blue[it.blue]}" }
            if (approaches.any { abs(red[it.red] - blue[it.blue]) > it.tolerance }) {
                logger.warn("竞技场 {}（种子 {}）有争夺点的最短步行距离超出目标差值：{}", size, seed, distances)
            }
            logger.info(
                "竞技场 {}（种子 {}）矿石：中央铁/铜 {}/{}，每座角矿 {}/{}，每队后方 16/16，近/中/远散矿 {}，后矿步行 {}，争夺点步行 {}",
                size, seed, centerIron, centerCopper, cornerIron, cornerCopper, scatter, starterRoutes, distances,
            )
            return true
        }

        private fun fail(part: String): Boolean {
            logger.error("竞技场 {}（种子 {}）生成校验失败：{}", size, seed, part)
            return false
        }

        private fun protectBase(base: BlockPos) {
            // Also protects the four-block starting tree diagonally behind the base.
            reserve(base.x, base.z, MatchRules.BASE_FORBID)
        }

        private fun reserve(x: Int, z: Int, radius: Int) {
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    if (surface.inside(x + dx, z + dz)) protected.add(surface.cell(x + dx, z + dz))
                }
            }
        }

        private fun approach(name: String, candidates: List<Int>, maxGap: Int?): Boolean {
            fun tolerance(distance: Int): Int = maxGap ?: (distance / 10).coerceAtLeast(1)
            val viable = candidates.filter { redPaths.distances[it] >= 0 && bluePaths.distances[surface.opposite(it)] >= 0 }
            val balanced = viable.filter {
                val red = redPaths.distances[it]
                val blue = bluePaths.distances[surface.opposite(it)]
                abs(red - blue) <= tolerance(minOf(red, blue))
            }.minByOrNull { redPaths.distances[it] + bluePaths.distances[surface.opposite(it)] }
            val selected: Int
            if (balanced != null) {
                selected = balanced
            } else {
                val paired = surface.pathsFrom(redStart, paired = true, protected = protected)
                selected = candidates.filter { paired.distances[it] >= 0 }.minByOrNull { paired.distances[it] }
                    ?: viable.minByOrNull { abs(redPaths.distances[it] - bluePaths.distances[surface.opposite(it)]) }
                    ?: return false
                val route = paired.routeTo(selected)
                if (route.isNotEmpty()) {
                    surface.clearWalkingRoute(route)
                    surface.clearWalkingRoute(route.map(surface::opposite))
                    redPaths = surface.pathsFrom(redStart)
                    bluePaths = surface.pathsFrom(blueStart)
                }
            }
            val other = surface.opposite(selected)
            val red = redPaths.distances[selected]
            val blue = bluePaths.distances[other]
            if (red < 0 || blue < 0) return false
            protected.addAll(redPaths.routeTo(selected))
            protected.addAll(bluePaths.routeTo(other))
            approaches.add(Approach(name, selected, other, tolerance(minOf(red, blue))))
            return true
        }

        private fun landmark(x: Int, z: Int, iron: Int, copper: Int): Boolean {
            val ores = (List(iron / 4) { Blocks.DEEPSLATE_IRON_ORE } +
                List(copper / 4) { Blocks.DEEPSLATE_COPPER_ORE }).shuffled(random)
            val reach = radius(iron + copper) + LANDMARK_SEARCH_MARGIN
            val choices = ArrayList<Pair<Int, Double>>()
            for (dx in -reach..reach) {
                for (dz in -reach..reach) {
                    if (!surface.inside(x + dx, z + dz) || x + dx + z + dz >= divider) continue
                    val cell = surface.cell(x + dx, z + dz)
                    val other = surface.opposite(cell)
                    if (!available(cell) || !available(other)) continue
                    if (redPaths.distances[cell] < 0 || bluePaths.distances[other] < 0) continue
                    choices.add(cell to (dx * dx + dz * dz + random.nextDouble(8.0)))
                }
            }
            if (choices.size < ores.size) return false
            choices.sortBy { it.second }
            for ((index, ore) in ores.withIndex()) {
                val cell = choices[index].first
                deposit(cell, ore, stacked = true)
                deposit(surface.opposite(cell), ore, stacked = true)
            }
            return true
        }

        private fun rearCells(base: BlockPos, distances: IntArray, towardMin: Boolean): List<Int> =
            surface.cells.filter { cell ->
                if (distances[cell] < 0 || !available(cell)) return@filter false
                val dx = surface.x(cell) - base.x
                val dz = surface.z(cell) - base.z
                maxOf(abs(dx), abs(dz)) <= MatchRules.BASE_INSET &&
                    if (towardMin) dx + dz < -MatchRules.BASE_FORBID else dx + dz > MatchRules.BASE_FORBID
            }

        private fun bands(distances: IntArray, red: Boolean): List<List<Int>> {
            val result = List(3) { ArrayList<Int>() }
            for (cell in surface.cells) {
                val distance = distances[cell]
                if (distance < 0 || !available(cell)) continue
                val side = surface.x(cell) + surface.z(cell) - divider
                if (side == 0 || (side < 0) != red) continue
                val band = (distance / (size / 5)).coerceAtMost(result.lastIndex)
                result[band].add(cell)
            }
            return result
        }

        private fun veins(reds: List<Int>, blues: List<Int>, ore: Block, quota: Int, gap: Int): Int {
            if (quota <= 0) return 0
            val redDistances = redPaths.distances
            val blueDistances = bluePaths.distances
            val redCells = reds.toHashSet()
            val blueCells = blues.toHashSet()
            val byDistance = blues.groupBy { blueDistances[it] }
            var placed = 0
            for (red in reds.shuffled(random)) {
                if (placed >= quota) break
                if (red in used) continue
                val distance = redDistances[red]
                val matches = (distance - gap..distance + gap).flatMap { byDistance[it].orEmpty() }
                    .filter { it !in used }
                if (matches.isEmpty()) continue
                val blue = matches.random(random)
                val limit = minOf(random.nextInt(8, MatchRules.SURFACE_ORE_VEIN + 1), quota - placed)
                val left = grow(red, redCells, redDistances, limit)
                val right = grow(blue, blueCells, blueDistances, limit)
                val count = minOf(left.size, right.size)
                for (index in 0 until count) {
                    deposit(left[index], ore)
                    deposit(right[index], ore)
                }
                placed += count
            }
            return placed
        }

        private fun grow(start: Int, cells: Set<Int>, distances: IntArray, limit: Int): List<Int> {
            val frontier = arrayListOf(start)
            val seen = hashSetOf(start)
            val vein = ArrayList<Int>(limit)
            val distance = distances[start]
            while (frontier.isNotEmpty() && vein.size < limit) {
                val cell = frontier.removeAt(random.nextInt(frontier.size))
                vein.add(cell)
                surface.neighbors(cell) { next ->
                    if (next in cells && next !in used && distances[next] >= distance && seen.add(next)) frontier.add(next)
                }
            }
            return vein
        }

        private fun available(cell: Int): Boolean = cell !in used && surface.mineable(cell)

        private fun deposit(cell: Int, ore: Block, stacked: Boolean = false) {
            used.add(cell)
            deposits.add(Deposit(surface.ground(cell), ore, stacked))
        }

        private fun place(): Boolean {
            for ((floor, ore, stacked) in deposits) {
                val state = ore.defaultBlockState()
                if (!level.setBlock(floor, state, Block.UPDATE_NONE)) return false
                if (stacked && !level.setBlock(floor.above(), state, Block.UPDATE_NONE)) return false
                if (stacked) surface.refresh(surface.cell(floor.x, floor.z))
            }
            return deposits.all { (floor, ore, stacked) ->
                level.getBlockState(floor).`is`(ore) && (!stacked || level.getBlockState(floor.above()).`is`(ore))
            }
        }
    }
}
