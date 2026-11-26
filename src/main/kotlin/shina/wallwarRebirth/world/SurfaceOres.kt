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
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.Heightmap

object SurfaceOres {
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
