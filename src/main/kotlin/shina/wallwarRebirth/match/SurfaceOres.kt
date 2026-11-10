package shina.wallwarRebirth.match

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
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
        val inset = MatchRules.BASE_INSET
        val used = HashSet<Long>()
        val random = level.random
        landmark(
            level,
            innerMin + inset,
            innerMax - inset,
            columns(MatchRules.CORNER_ORE_IRON, MatchRules.CORNER_ORE_COPPER),
            innerMin,
            innerMax,
            used,
            random,
        )
        landmark(
            level,
            innerMax - inset,
            innerMin + inset,
            columns(MatchRules.CORNER_ORE_IRON, MatchRules.CORNER_ORE_COPPER),
            innerMin,
            innerMax,
            used,
            random,
        )
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
        val reach = kotlin.math.ceil(kotlin.math.sqrt(order.size / Math.PI)).toInt() + 8
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
        val extra = member != null &&
            Classes.isMiner(member.classLocked, member.classId) &&
            world.random.nextFloat() < MatchRules.MINER_BONUS_CHANCE
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

    fun nearestPile(level: ServerLevel, from: Vec3): List<BlockPos> {
        val ores = ArrayList<BlockPos>()
        val size = Match.mapSize
        val min = -size / 2
        val max = min + size - 1
        for (x in min + 1 until max) {
            for (z in min + 1 until max) {
                val top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)
                for (y in top - 2..top + 1) {
                    val pos = BlockPos(x, y, z)
                    if (isOre(level.getBlockState(pos).block)) ores.add(pos)
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
