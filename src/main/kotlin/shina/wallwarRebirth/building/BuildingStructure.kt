package shina.wallwarRebirth.building

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings

object BuildingStructure {
    const val PLACEHOLDER_RADIUS = 2

    fun place(level: ServerLevel, pos: BlockPos, definition: BuildingDefinition, rank: Int) {
        val template = level.structureManager.get(definition.structure(rank)).orElse(null)
        if (template == null) {
            placePlaceholder(level, pos)
            return
        }
        val existing = level.getBlockEntity(pos) as? SiteCoreBlockEntity
        val snapshot = existing?.snapshot()
        existing?.storage?.clearContent()
        val size = template.size
        val origin = pos.offset(-size.x / 2, -size.y / 2, -size.z / 2)
        template.placeInWorld(level, origin, origin, StructurePlaceSettings(), level.random, Block.UPDATE_CLIENTS)
        level.setBlock(pos, ModSites.CORE.defaultBlockState(), Block.UPDATE_ALL)
        val restored = level.getBlockEntity(pos) as? SiteCoreBlockEntity
        if (snapshot != null && restored != null) restored.restore(snapshot)
    }

    private fun placePlaceholder(level: ServerLevel, pos: BlockPos) {
        val stone = Blocks.SMOOTH_STONE.defaultBlockState()
        for (dx in -PLACEHOLDER_RADIUS..PLACEHOLDER_RADIUS) {
            for (dz in -PLACEHOLDER_RADIUS..PLACEHOLDER_RADIUS) {
                if (dx == 0 && dz == 0) continue
                val target = pos.offset(dx, 0, dz)
                if (level.getBlockState(target).isAir) {
                    level.setBlock(target, stone, Block.UPDATE_ALL)
                }
            }
        }
    }
}
