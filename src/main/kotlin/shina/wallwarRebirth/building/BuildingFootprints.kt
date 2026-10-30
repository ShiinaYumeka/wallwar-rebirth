package shina.wallwarRebirth.building

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel

object BuildingFootprints {
    fun cells(definition: BuildingDefinition, origin: BlockPos, facing: Direction): Set<BlockPos> {
        val cells = HashSet<BlockPos>()
        if (definition.footprint == Footprint.WALL_9) {
            val alongX = facing.axis == Direction.Axis.Z
            for (index in -4..4) {
                cells.add(if (alongX) origin.offset(index, 0, 0) else origin.offset(0, 0, index))
            }
        } else {
            for (dx in -3..3) {
                for (dz in -3..3) cells.add(origin.offset(dx, 0, dz))
            }
        }
        return cells
    }

    fun rejection(
        level: ServerLevel,
        origin: BlockPos,
        definition: BuildingDefinition,
        facing: Direction,
    ): String? {
        val cells = cells(definition, origin, facing)
        for (core in SiteCores.all()) {
            if (core.level !== level || core.blockPos == origin || core.buildingId.isEmpty()) continue
            val other = Buildings.byId(core.buildingId) ?: continue
            val theirs = cells(other, core.blockPos, core.facingDirection())
            if (cells.any { it in theirs }) return "占地和另一座建筑重叠"
        }
        return null
    }

    fun inside(definition: BuildingDefinition, x: Int, z: Int): Boolean = when (definition.footprint) {
        Footprint.SQUARE_7 -> x in -3..3 && z in -3..3
        Footprint.WALL_9 -> (z == 0 && x in -4..4) || (x == 0 && z in -4..4)
    }
}
