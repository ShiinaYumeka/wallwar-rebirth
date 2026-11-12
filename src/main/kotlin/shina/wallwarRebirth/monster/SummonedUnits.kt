package shina.wallwarRebirth.monster

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity

object SummonedUnits {
    private val units = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<SummonedUnit, Boolean>())

    fun add(unit: SummonedUnit) {
        units.add(unit)
    }

    fun remove(unit: SummonedUnit) {
        units.remove(unit)
    }

    fun all(): List<SummonedUnit> {
        units.removeIf { unit ->
            val entity = unit as Entity
            entity.isRemoved || !entity.isAlive
        }
        return ArrayList(units)
    }

    fun rebindHomes(level: ServerLevel, from: BlockPos, to: BlockPos) {
        for (unit in all()) {
            if (unit.living().level() !== level) continue
            unit.mind.rebindHome(from, to)
        }
    }

    fun reassertHome(site: shina.wallwarRebirth.building.SiteCoreBlockEntity) {
        val world = site.level as? ServerLevel ?: return
        for (unit in all()) {
            if (unit.living().level() !== world) continue
            unit.mind.reassertIfHome(site.blockPos)
        }
    }
}
