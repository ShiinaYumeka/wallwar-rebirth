package shina.wallwarRebirth.building

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks

object BuildingStructure {
    private const val RADIUS = 2

    fun placeUnselected(level: ServerLevel, pos: BlockPos) {
        setForm(level, pos, CoreForm.BEACON)
    }

    fun clearLegacy(level: ServerLevel, pos: BlockPos) {
        for (dx in -RADIUS..RADIUS) {
            for (dz in -RADIUS..RADIUS) {
                val floor = pos.offset(dx, -1, dz)
                if (!level.isOutsideBuildHeight(floor) && level.getBlockState(floor).block is SitePartBlock) {
                    level.setBlock(floor, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)
                }
                if (dx == 0 && dz == 0) continue
                val top = pos.offset(dx, 0, dz)
                if (!level.isOutsideBuildHeight(top) && level.getBlockState(top).block is SitePartBlock) {
                    level.setBlock(top, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)
                }
            }
        }
    }

    private fun setForm(level: ServerLevel, pos: BlockPos, form: CoreForm) {
        val state = level.getBlockState(pos)
        if (state.block !is SiteCoreBlock || state.getValue(SiteCoreBlock.FORM) == form) return
        level.setBlock(pos, state.setValue(SiteCoreBlock.FORM, form), Block.UPDATE_ALL)
    }
}
