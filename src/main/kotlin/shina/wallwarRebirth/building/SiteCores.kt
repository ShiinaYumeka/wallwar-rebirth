package shina.wallwarRebirth.building

import net.minecraft.core.BlockPos
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

    fun insideCoreBuilding(level: Level, pos: BlockPos): Boolean {
        for (core in all()) {
            if (core.level !== level || core.isRemoved || core.buildingId.isEmpty()) continue
            val definition = Buildings.byId(core.buildingId) ?: continue
            if (definition.category != BuildingCategory.CORE) continue
            if (core.encloses(pos)) return true
        }
        return false
    }
}
