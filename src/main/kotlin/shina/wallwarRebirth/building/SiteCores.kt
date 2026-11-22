package shina.wallwarRebirth.building

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level

object SiteCores {
    private val cores = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<SiteCoreBlockEntity, Boolean>())

    fun add(core: SiteCoreBlockEntity) {
        cores.add(core)
    }

    fun remove(core: SiteCoreBlockEntity) {
        cores.remove(core)
    }

    fun all(): List<SiteCoreBlockEntity> {
        cores.removeIf { it.isRemoved }
        return ArrayList(cores)
    }

    fun find(level: Level, pos: BlockPos): SiteCoreBlockEntity? {
        for (core in cores) {
            if (core.isRemoved || core.level !== level || core.blockPos != pos) continue
            return core
        }
        return null
    }

    fun ownsBlock(level: Level, pos: BlockPos): Boolean =
        cores.any { it.level === level && !it.isRemoved && (it.blockPos == pos || pos in it.partPositions) }

    fun insideBuilding(level: Level, pos: BlockPos): Boolean {
        for (core in all()) {
            if (core.level !== level || core.isRemoved || core.buildingId.isEmpty()) continue
            if (core.encloses(pos)) return true
        }
        return false
    }

    fun hasLiveWorkshop(level: ServerLevel, team: String): Boolean {
        if (team.isEmpty()) return false
        return all().any { core ->
            core.level === level &&
                !core.isRemoved &&
                core.buildingId == Buildings.WORKSHOP.id &&
                core.team == team &&
                core.health > 0
        }
    }
}
