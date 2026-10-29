package shina.wallwarRebirth.building

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
}
