package shina.wallwarRebirth.monster

object SummonedUnits {
    private val units = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<SummonedZombie, Boolean>())

    fun add(unit: SummonedZombie) {
        units.add(unit)
    }

    fun remove(unit: SummonedZombie) {
        units.remove(unit)
    }

    fun all(): List<SummonedZombie> {
        units.removeIf { it.isRemoved || !it.isAlive }
        return ArrayList(units)
    }
}
