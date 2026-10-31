package shina.wallwarRebirth.monster

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
}
