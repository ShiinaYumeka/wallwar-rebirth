package shina.wallwarRebirth.building

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks

object BuildingStructure {
    const val RADIUS = 2

    fun placeUnselected(level: ServerLevel, pos: BlockPos) {
        layFloor(level, pos)
        setForm(level, pos, CoreForm.BEACON)
        clearDecorations(level, pos)
    }

    fun place(level: ServerLevel, pos: BlockPos, definition: BuildingDefinition, rank: Int) {
        if (definition.id == Buildings.REACTOR.id) {
            applyReactor(level, pos, rank)
        } else {
            placeUnselected(level, pos)
        }
    }

    fun clear(level: ServerLevel, pos: BlockPos) {
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
        if (level.getBlockState(pos).block is SiteCoreBlock) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)
        }
    }

    private fun applyReactor(level: ServerLevel, pos: BlockPos, rank: Int) {
        layFloor(level, pos)
        setForm(level, pos, CoreForm.BLAST_FURNACE)
        val ores = reactorOres(rank)
        for (dx in -RADIUS..RADIUS) {
            for (dz in -RADIUS..RADIUS) {
                if (dx == 0 && dz == 0) continue
                val target = pos.offset(dx, 0, dz)
                if (level.isOutsideBuildHeight(target)) continue
                val ore = ores[dx to dz]
                if (ore != null) {
                    level.setBlock(target, ore.defaultBlockState(), Block.UPDATE_ALL)
                } else if (level.getBlockState(target).block is SitePartBlock) {
                    level.setBlock(target, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)
                }
            }
        }
    }

    private fun layFloor(level: ServerLevel, pos: BlockPos) {
        val stone = ModSites.SMOOTH_STONE.defaultBlockState()
        for (dx in -RADIUS..RADIUS) {
            for (dz in -RADIUS..RADIUS) {
                val target = pos.offset(dx, -1, dz)
                if (!level.isOutsideBuildHeight(target)) {
                    level.setBlock(target, stone, Block.UPDATE_ALL)
                }
            }
        }
    }

    private fun clearDecorations(level: ServerLevel, pos: BlockPos) {
        for (dx in -RADIUS..RADIUS) {
            for (dz in -RADIUS..RADIUS) {
                if (dx == 0 && dz == 0) continue
                val target = pos.offset(dx, 0, dz)
                if (!level.isOutsideBuildHeight(target) && level.getBlockState(target).block is SitePartBlock) {
                    level.setBlock(target, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)
                }
            }
        }
    }

    private fun setForm(level: ServerLevel, pos: BlockPos, form: CoreForm) {
        val state = level.getBlockState(pos)
        if (state.block !is SiteCoreBlock || state.getValue(SiteCoreBlock.FORM) == form) return
        level.setBlock(pos, state.setValue(SiteCoreBlock.FORM, form), Block.UPDATE_ALL)
    }

    private fun reactorOres(rank: Int): Map<Pair<Int, Int>, Block> {
        val iron = if (rank >= 3) ModSites.DEEPSLATE_IRON_ORE else ModSites.IRON_ORE
        val copper = if (rank >= 3) ModSites.DEEPSLATE_DIAMOND_ORE else ModSites.COPPER_ORE
        val placed = mutableMapOf(
            (-2 to 0) to iron,
            (2 to 0) to copper,
            (0 to -2) to copper,
            (0 to 2) to iron,
        )
        if (rank >= 2) {
            val redstone = if (rank >= 3) ModSites.DEEPSLATE_REDSTONE_ORE else ModSites.REDSTONE_ORE
            val lapis = if (rank >= 3) ModSites.DEEPSLATE_LAPIS_ORE else ModSites.LAPIS_ORE
            placed[-2 to -2] = redstone
            placed[2 to -2] = lapis
            placed[-2 to 2] = lapis
            placed[2 to 2] = redstone
        }
        return placed
    }
}
