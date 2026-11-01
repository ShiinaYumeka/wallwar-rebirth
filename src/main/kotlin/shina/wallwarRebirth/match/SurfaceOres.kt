package shina.wallwarRebirth.match

import net.minecraft.core.BlockPos
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
        val columns = HashMap<Long, BlockPos>()
        for (x in min + 1 until max) {
            for (z in min + 1 until max) {
                if (!betweenBases(x, z, red, blue)) continue
                val ground = ground(level, x, z) ?: continue
                columns[BlockPos.asLong(x, 0, z)] = ground
            }
        }
        val random = level.random
        val target = columns.size / MatchRules.SURFACE_ORE_EVERY
        val open = columns.keys.toMutableList()
        val used = HashSet<Long>()
        var placed = 0
        while (placed < target && open.isNotEmpty()) {
            val start = open.removeAt(random.nextInt(open.size))
            if (start in used || beside(start, used, emptySet())) continue
            val ore = kinds[random.nextInt(kinds.size)]
            val vein = HashSet<Long>()
            val frontier = ArrayList<Long>()
            frontier.add(start)
            var grown = 0
            while (frontier.isNotEmpty() && grown < MatchRules.SURFACE_ORE_VEIN && placed < target) {
                val key = frontier.removeAt(random.nextInt(frontier.size))
                if (!vein.add(key) || key !in columns || key in used) continue
                if (beside(key, used, vein)) continue
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

    private fun beside(key: Long, used: Set<Long>, vein: Set<Long>): Boolean {
        for (near in around(key)) {
            if (near in used && near !in vein) return true
        }
        return false
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

    private fun betweenBases(x: Int, z: Int, red: BlockPos, blue: BlockPos): Boolean {
        val midX = (red.x + blue.x) / 2.0
        val midZ = (red.z + blue.z) / 2.0
        val px = x + 0.5
        val pz = z + 0.5
        val toMid = sq(px - midX) + sq(pz - midZ)
        val toRed = sq(px - (red.x + 0.5)) + sq(pz - (red.z + 0.5))
        val toBlue = sq(px - (blue.x + 0.5)) + sq(pz - (blue.z + 0.5))
        return toMid < toRed && toMid < toBlue
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

    private fun sq(value: Double): Double = value * value
}
