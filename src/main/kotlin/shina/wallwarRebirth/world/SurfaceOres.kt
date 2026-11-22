package shina.wallwarRebirth.world
import shina.wallwarRebirth.match.Match
import shina.wallwarRebirth.profession.Classes
import shina.wallwarRebirth.rules.MatchRules

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffects
import net.minecraft.stats.Stats
import net.minecraft.tags.BlockTags
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.Heightmap
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.sqrt

object SurfaceOres {
    private val kinds = listOf(
        Blocks.IRON_ORE,
        Blocks.COPPER_ORE,
    )

    fun scatter(level: ServerLevel, red: BlockPos, blue: BlockPos) {
        val size = Match.mapSize
        val min = -size / 2
        val max = min + size - 1
        val innerMin = min + 1
        val innerMax = max - 1
        val used = HashSet<Long>()
        val random = level.random
        val margin = MatchRules.MINERAL_REACTOR_RADIUS
        for (plan in cornerPlans(innerMin, innerMax)) {
            for (dx in -margin..margin) {
                for (dz in -margin..margin) {
                    used.add(BlockPos.asLong(plan.reactorX + dx, 0, plan.reactorZ + dz))
                }
            }
            landmark(
                level,
                plan.oreX,
                plan.oreZ,
                columns(MatchRules.CORNER_ORE_IRON, MatchRules.CORNER_ORE_COPPER),
                innerMin,
                innerMax,
                used,
                random,
            )
        }
        landmark(
            level,
            Math.round((red.x + blue.x) / 2.0).toInt(),
            Math.round((red.z + blue.z) / 2.0).toInt(),
            columns(MatchRules.CENTER_ORE_IRON, MatchRules.CENTER_ORE_COPPER),
            innerMin,
            innerMax,
            used,
            random,
        )
        placeRearVeins(level, red, blue, innerMin, innerMax, used, random)
        val columns = HashMap<Long, BlockPos>()
        for (x in innerMin..innerMax) {
            for (z in innerMin..innerMax) {
                val key = BlockPos.asLong(x, 0, z)
                if (key in used || besideBase(x, z, red, blue)) continue
                val ground = ground(level, x, z) ?: continue
                columns[key] = ground
            }
        }
        val target = columns.size / MatchRules.SURFACE_ORE_EVERY
        val open = columns.keys.toMutableList()
        var placed = 0
        while (placed < target && open.isNotEmpty()) {
            val start = open.removeAt(random.nextInt(open.size))
            if (start in used) continue
            val ore = kinds[random.nextInt(kinds.size)]
            val vein = HashSet<Long>()
            val frontier = ArrayList<Long>()
            frontier.add(start)
            var grown = 0
            while (frontier.isNotEmpty() && grown < MatchRules.SURFACE_ORE_VEIN && placed < target) {
                val key = frontier.removeAt(random.nextInt(frontier.size))
                if (!vein.add(key) || key !in columns || key in used) continue
                val pos = columns[key] ?: continue
                val room = minOf(MatchRules.SURFACE_ORE_VEIN - grown, target - placed)
                val count = pile(level, pos, ore, random, room)
                if (count == 0) continue
                used.add(key)
                placed += count
                grown += count
                for (near in around(key)) {
                    if (near in columns && near !in used && near !in vein) frontier.add(near)
                }
            }
        }
    }

    data class CornerPlan(val oreX: Int, val oreZ: Int, val reactorX: Int, val reactorZ: Int)

    fun cornerPlans(innerMin: Int, innerMax: Int): List<CornerPlan> {
        val inset = MatchRules.BASE_INSET
        val margin = MatchRules.MINERAL_REACTOR_RADIUS
        val minCore = innerMin + margin
        val maxCore = innerMax - margin
        val columnCount = MatchRules.CORNER_ORE_IRON / 2 + MatchRules.CORNER_ORE_COPPER / 2
        val oreRadius = ceil(sqrt(columnCount / Math.PI)).toInt()
        val gap = oreRadius + margin + 1
        val origins = listOf(
            innerMin + inset to innerMax - inset,
            innerMax - inset to innerMin + inset,
        )
        return origins.map { (centerX, centerZ) ->
            val dirX = if (centerX < 0) -1 else 1
            val dirZ = if (centerZ < 0) -1 else 1
            val reactorX = (centerX + dirX * gap).coerceIn(minCore, maxCore)
            val reactorZ = (centerZ + dirZ * gap).coerceIn(minCore, maxCore)
            var oreX = centerX
            var oreZ = centerZ
            val needed = (oreRadius + margin + 1).toDouble()
            var guard = 0
            while (hypot((oreX - reactorX).toDouble(), (oreZ - reactorZ).toDouble()) < needed && guard++ < 64) {
                var moved = false
                val nextX = oreX - dirX
                val nextZ = oreZ - dirZ
                if (nextX in innerMin..innerMax) {
                    oreX = nextX
                    moved = true
                }
                if (nextZ in innerMin..innerMax) {
                    oreZ = nextZ
                    moved = true
                }
                if (!moved) break
            }
            CornerPlan(oreX, oreZ, reactorX, reactorZ)
        }
    }

    private fun placeRearVeins(
        level: ServerLevel,
        red: BlockPos,
        blue: BlockPos,
        innerMin: Int,
        innerMax: Int,
        used: MutableSet<Long>,
        random: net.minecraft.util.RandomSource,
    ) {
        placeRearVein(level, red, blue, innerMin, innerMax, used, random)
        placeRearVein(level, blue, red, innerMin, innerMax, used, random)
    }

    private fun placeRearVein(
        level: ServerLevel,
        core: BlockPos,
        other: BlockPos,
        innerMin: Int,
        innerMax: Int,
        used: MutableSet<Long>,
        random: net.minecraft.util.RandomSource,
    ) {
        val stepX = direction(core.x - other.x)
        val stepZ = direction(core.z - other.z)
        if (stepX == 0 && stepZ == 0) return
        val treeDistance = MatchRules.PLATFORM_RADIUS + 1
        val startDist = treeDistance + 3
        var startX = core.x + stepX * startDist
        var startZ = core.z + stepZ * startDist
        var retract = 0
        while ((startX !in innerMin..innerMax || startZ !in innerMin..innerMax) && retract++ < startDist - treeDistance) {
            startX -= stepX
            startZ -= stepZ
        }
        if (startX !in innerMin..innerMax || startZ !in innerMin..innerMax) return
        val local = HashMap<Long, BlockPos>()
        val reach = 8
        for (dx in -reach..reach) {
            for (dz in -reach..reach) {
                val x = startX + dx
                val z = startZ + dz
                if (x !in innerMin..innerMax || z !in innerMin..innerMax) continue
                if (besideBase(x, z, core, other)) continue
                val key = BlockPos.asLong(x, 0, z)
                if (key in used) continue
                val ground = ground(level, x, z) ?: continue
                local[key] = ground
            }
        }
        val start = chooseRearStart(core, stepX, stepZ, treeDistance, startDist, startX, startZ, local) ?: return
        val ore = kinds[random.nextInt(kinds.size)]
        val vein = HashSet<Long>()
        val frontier = ArrayList<Long>()
        frontier.add(start)
        var grown = 0
        while (frontier.isNotEmpty() && grown < MatchRules.SURFACE_ORE_VEIN) {
            val key = frontier.removeAt(random.nextInt(frontier.size))
            if (!vein.add(key) || key !in local || key in used) continue
            val pos = local[key] ?: continue
            val room = MatchRules.SURFACE_ORE_VEIN - grown
            val count = pile(level, pos, ore, random, room)
            if (count == 0) continue
            used.add(key)
            grown += count
            for (near in around(key)) {
                if (near in local && near !in used && near !in vein) frontier.add(near)
            }
        }
    }

    private fun chooseRearStart(
        core: BlockPos,
        stepX: Int,
        stepZ: Int,
        treeDistance: Int,
        startDist: Int,
        startX: Int,
        startZ: Int,
        local: Map<Long, BlockPos>,
    ): Long? {
        val preferred = BlockPos.asLong(startX, 0, startZ)
        if (preferred in local) return preferred
        for (extra in 0..6) {
            val key = BlockPos.asLong(core.x + stepX * (startDist + extra), 0, core.z + stepZ * (startDist + extra))
            if (key in local) return key
        }
        for (back in 1..(startDist - treeDistance - 1).coerceAtLeast(0)) {
            val key = BlockPos.asLong(core.x + stepX * (startDist - back), 0, core.z + stepZ * (startDist - back))
            if (key in local) return key
        }
        return local.keys.minByOrNull { key ->
            val pos = BlockPos.of(key)
            val dx = pos.x - startX
            val dz = pos.z - startZ
            dx * dx + dz * dz
        }
    }

    private fun direction(delta: Int): Int = when {
        delta > 0 -> 1
        delta < 0 -> -1
        else -> 0
    }

    private fun columns(iron: Int, copper: Int): List<Block> {
        val list = ArrayList<Block>(iron / 2 + copper / 2)
        repeat(iron / 2) { list.add(Blocks.DEEPSLATE_IRON_ORE) }
        repeat(copper / 2) { list.add(Blocks.DEEPSLATE_COPPER_ORE) }
        return list
    }

    private fun landmark(
        level: ServerLevel,
        x: Int,
        z: Int,
        ores: List<Block>,
        innerMin: Int,
        innerMax: Int,
        used: MutableSet<Long>,
        random: net.minecraft.util.RandomSource,
    ) {
        val order = ores.toMutableList()
        for (index in order.lastIndex downTo 1) {
            val swap = random.nextInt(index + 1)
            val kept = order[index]
            order[index] = order[swap]
            order[swap] = kept
        }
        val reach = ceil(sqrt(order.size / Math.PI)).toInt() + 8
        val spots = ArrayList<Long>()
        for (dx in -reach..reach) {
            for (dz in -reach..reach) {
                val columnX = x + dx
                val columnZ = z + dz
                if (columnX !in innerMin..innerMax || columnZ !in innerMin..innerMax) continue
                spots.add(BlockPos.asLong(columnX, 0, columnZ))
            }
        }
        spots.sortWith(compareBy<Long> { distanceSquared(it, x, z) }.thenBy { BlockPos.of(it).x }.thenBy { BlockPos.of(it).z })
        var placed = 0
        for (key in spots) {
            if (placed >= order.size || key in used) continue
            val column = BlockPos.of(key)
            val ground = ground(level, column.x, column.z) ?: continue
            if (!stack(level, ground, order[placed])) continue
            used.add(key)
            placed++
        }
    }

    private fun distanceSquared(key: Long, x: Int, z: Int): Int {
        val pos = BlockPos.of(key)
        val dx = pos.x - x
        val dz = pos.z - z
        return dx * dx + dz * dz
    }

    private fun stack(level: ServerLevel, pos: BlockPos, ore: Block): Boolean {
        val above = pos.above()
        val cover = level.getBlockState(above)
        if (!cover.isAir && !plant(cover) && !cover.`is`(BlockTags.SNOW) && !cover.`is`(BlockTags.LEAVES)) return false
        if (!cover.isAir) level.setBlock(above, Blocks.AIR.defaultBlockState(), Block.UPDATE_NONE)
        level.setBlock(pos, ore.defaultBlockState(), Block.UPDATE_NONE)
        level.setBlock(above, ore.defaultBlockState(), Block.UPDATE_NONE)
        return true
    }

    private fun pile(level: ServerLevel, pos: BlockPos, ore: Block, random: net.minecraft.util.RandomSource, room: Int): Int {
        if (room <= 0) return 0
        val above = pos.above()
        val cover = level.getBlockState(above)
        if (!cover.isAir && !plant(cover) && !cover.`is`(BlockTags.SNOW) && !cover.`is`(BlockTags.LEAVES)) return 0
        if (!cover.isAir) level.setBlock(above, Blocks.AIR.defaultBlockState(), Block.UPDATE_NONE)
        val mode = random.nextInt(3)
        val embed = mode != 1
        val rise = mode != 0
        var placed = 0
        if (embed) {
            level.setBlock(pos, ore.defaultBlockState(), Block.UPDATE_NONE)
            placed++
        }
        if (rise && placed < room) {
            level.setBlock(above, ore.defaultBlockState(), Block.UPDATE_NONE)
            placed++
        }
        return placed
    }

    private fun around(key: Long): List<Long> {
        val pos = BlockPos.of(key)
        val found = ArrayList<Long>(8)
        for (dx in -1..1) {
            for (dz in -1..1) {
                if (dx == 0 && dz == 0) continue
                found.add(BlockPos.asLong(pos.x + dx, 0, pos.z + dz))
            }
        }
        return found
    }

    fun harvest(world: Level, player: ServerPlayer, pos: BlockPos, state: BlockState): Boolean {
        if (world !is ServerLevel || !Match.isArena(world)) return true
        if (!handles(state.block)) return true
        if (!player.hasCorrectToolForDrops(state)) return true
        val tool = player.mainHandItem
        val drops = Block.getDrops(state, world, pos, null, player, tool)
        player.awardStat(Stats.BLOCK_MINED.get(state.block))
        player.causeFoodExhaustion(0.005f)
        world.destroyBlock(pos, false, player)
        val member = Match.member(player.uuid)
        val miner = member != null && Classes.isMiner(member.classLocked, member.classId)
        val extra = miner && (
            player.hasEffect(MobEffects.HASTE) ||
                world.random.nextFloat() < MatchRules.MINER_BONUS_CHANCE
        )
        for (stack in drops) {
            val smelted = smelt(stack)
            Block.popResource(world, pos, smelted)
            if (extra) Block.popResource(world, pos, smelted.copy())
        }
        state.spawnAfterBreak(world, pos, tool, true)
        if (!tool.isEmpty) tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND)
        return false
    }

    private fun besideBase(x: Int, z: Int, red: BlockPos, blue: BlockPos): Boolean {
        val limit = MatchRules.BASE_FORBID
        return chebyshev(x, z, red) <= limit || chebyshev(x, z, blue) <= limit
    }

    private fun chebyshev(x: Int, z: Int, core: BlockPos): Int {
        val dx = kotlin.math.abs(x - core.x)
        val dz = kotlin.math.abs(z - core.z)
        return maxOf(dx, dz)
    }

    private fun ground(level: ServerLevel, x: Int, z: Int): BlockPos? {
        val top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1
        var pos = BlockPos(x, top, z)
        var state = level.getBlockState(pos)
        if (state.`is`(BlockTags.LOGS)) return null
        if (plant(state) || state.`is`(BlockTags.LEAVES) || state.`is`(BlockTags.SNOW)) {
            pos = pos.below()
            state = level.getBlockState(pos)
        }
        if (!state.`is`(Blocks.GRASS_BLOCK) && !state.`is`(Blocks.DIRT)) return null
        return pos
    }

    private fun plant(state: BlockState): Boolean =
        state.`is`(BlockTags.FLOWERS) ||
            state.`is`(BlockTags.SAPLINGS) ||
            state.`is`(Blocks.SHORT_GRASS) ||
            state.`is`(Blocks.TALL_GRASS) ||
            state.`is`(Blocks.FERN) ||
            state.`is`(Blocks.LARGE_FERN) ||
            state.`is`(Blocks.BUSH)

    fun isMineableOre(level: ServerLevel, pos: BlockPos): Boolean =
        isOre(level.getBlockState(pos).block) && !shina.wallwarRebirth.building.SiteCores.ownsBlock(level, pos)

    fun nearestPile(level: ServerLevel, from: Vec3, ignore: Set<Long> = emptySet()): List<BlockPos> {
        val ores = ArrayList<BlockPos>()
        val size = Match.mapSize
        val min = -size / 2
        val max = min + size - 1
        for (x in min + 1 until max) {
            for (z in min + 1 until max) {
                val top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)
                for (y in top - 2..top + 1) {
                    val pos = BlockPos(x, y, z)
                    if (pos.asLong() !in ignore && isMineableOre(level, pos)) ores.add(pos)
                }
            }
        }
        if (ores.isEmpty()) return emptyList()
        val start = ores.minBy { from.distanceToSqr(Vec3.atCenterOf(it)) }
        val left = ores.map { it.asLong() }.toMutableSet()
        val pile = ArrayList<BlockPos>()
        val queue = ArrayDeque<BlockPos>()
        queue.add(start)
        left.remove(start.asLong())
        while (queue.isNotEmpty()) {
            val pos = queue.removeFirst()
            pile.add(pos)
            for (dx in -1..1) {
                for (dz in -1..1) {
                    for (dy in -1..1) {
                        if (dx == 0 && dy == 0 && dz == 0) continue
                        val near = pos.offset(dx, dy, dz)
                        if (left.remove(near.asLong())) queue.add(near)
                    }
                }
            }
        }
        return pile
    }

    fun isOre(block: Block): Boolean = handles(block)

    private fun handles(block: Block): Boolean = when (block) {
        Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
        Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE,
        Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
        Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
        Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
        -> true
        else -> false
    }

    private fun smelt(stack: ItemStack): ItemStack {
        val ingot = when (stack.item) {
            Items.RAW_IRON -> Items.IRON_INGOT
            Items.RAW_COPPER -> Items.COPPER_INGOT
            Items.RAW_GOLD -> Items.GOLD_INGOT
            else -> return stack
        }
        return ItemStack(ingot, stack.count)
    }
}
