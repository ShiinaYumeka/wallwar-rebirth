package shina.wallwarRebirth.building

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.ItemTags
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState

object BuildingMining {
    fun progress(
        state: BlockState,
        player: Player,
        level: BlockGetter,
        pos: BlockPos,
        vanilla: () -> Float,
    ): Float {
        val core = resolve(level, pos, state) ?: return 0f
        if (core.isAlly(player) || !player.mainHandItem.`is`(ItemTags.PICKAXES)) return 0f
        return vanilla()
    }

    fun finish(level: Level, player: Player, pos: BlockPos, state: BlockState): Boolean {
        if (state.block !is SiteCoreBlock && state.block !is SiteStructureBlock && state.block !is SitePartBlock) {
            return true
        }
        val core = resolve(level, pos, state) ?: return false
        if (core.isAlly(player) || !player.mainHandItem.`is`(ItemTags.PICKAXES)) return false
        core.damage(1)
        return false
    }

    fun resolve(level: BlockGetter, pos: BlockPos, state: BlockState): SiteCoreBlockEntity? {
        if (state.block is SiteCoreBlock) return level.getBlockEntity(pos) as? SiteCoreBlockEntity
        if (state.block is SiteStructureBlock) {
            val part = level.getBlockEntity(pos) as? SiteStructureBlockEntity ?: return null
            return level.getBlockEntity(part.corePos) as? SiteCoreBlockEntity
        }
        if (state.block is SitePartBlock && level is ServerLevel) {
            return SiteCores.all().firstOrNull { core ->
                core.level === level && manhattan(core.blockPos, pos) <= 3
            }
        }
        return null
    }

    private fun manhattan(origin: BlockPos, pos: BlockPos): Int {
        val dx = origin.x - pos.x
        val dy = origin.y - pos.y
        val dz = origin.z - pos.z
        return kotlin.math.abs(dx) + kotlin.math.abs(dy) + kotlin.math.abs(dz)
    }
}
