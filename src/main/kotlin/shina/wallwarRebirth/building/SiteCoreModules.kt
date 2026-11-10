package shina.wallwarRebirth.building

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Containers
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import shina.wallwarRebirth.rules.MatchRules

internal fun SiteCoreBlockEntity.borrowed(slot: ModuleSlot): Boolean {
    val origin = Vec3.atCenterOf(blockPos)
    for (other in SiteCores.all()) {
        if (other === this || other.isRemoved || other.health <= 0 || other.level !== level) continue
        if (other.buildingId != Buildings.WALL.id) continue
        if (team.isEmpty() || other.team != team) continue
        if (other.diffuse.isEmpty || other.moduleStack(slot).isEmpty) continue
        if (origin.distanceTo(Vec3.atCenterOf(other.blockPos)) > MatchRules.DIFFUSION_RANGE) continue
        return true
    }
    return false
}

internal fun SiteCoreBlockEntity.setModule(slot: ModuleSlot, stack: ItemStack) {
    val definition = Buildings.byId(buildingId)
    when (slot) {
        ModuleSlot.PRIMARY -> {
            reinforce = stack
            if (stack.isEmpty) reinforceMineral = ""
        }
        ModuleSlot.SECONDARY -> {
            val had = !reinforceTwo.isEmpty
            reinforceTwo = stack
            if (stack.isEmpty) reinforceTwoMineral = ""
            if (!stack.isEmpty && buildingId == Buildings.BASE.id) healAge = 0
            if (had && buildingId == Buildings.BARRACKS.id) {
                val world = level as? ServerLevel
                if (world != null) BarracksWork.dumpStock(this, world)
            }
        }
        ModuleSlot.DIFFUSION -> {
            diffuse = stack
            if (buildingId == Buildings.BARRACKS.id) healAge = 0
            if (buildingId == Buildings.BASE.id && !stack.isEmpty) {
                val world = level as? ServerLevel
                if (world != null) shina.wallwarRebirth.shop.match.PortableShop.tick(world.server)
            }
        }
    }
    if (definition != null) sanitizeMinerals(definition)
    setChanged()
}

internal fun SiteCoreBlockEntity.sanitizeMinerals(definition: BuildingDefinition) {
    if (buildingId == Buildings.BASE.id) reinforceTwoMineral = ""
    val unlocked = definition.minerals().filter { buildingLevel >= it.minLevel }.map { SiteCoreBlockEntity.itemKey(it.item) }.toSet()
    if (reinforceMineral.isNotEmpty() && reinforceMineral !in unlocked) reinforceMineral = ""
}

internal fun SiteCoreBlockEntity.clearModules() {
    reinforce = ItemStack.EMPTY
    reinforceTwo = ItemStack.EMPTY
    diffuse = ItemStack.EMPTY
    reinforceMineral = ""
    reinforceTwoMineral = ""
    healAge = 0
    releaseCount = 0
    lastHurtAge = -1
    dripAge = 0
    barracksStock.clear()
    dripQueue.clear()
}

internal fun SiteCoreBlockEntity.dropModule(world: Level, pos: BlockPos, stack: ItemStack) {
    if (!stack.isEmpty) Containers.dropItemStack(world, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, stack.copy())
}

internal fun SiteCoreBlockEntity.storedSetMineral(slot: ModuleSlot, item: Item) {
    val definition = Buildings.byId(buildingId) ?: return
    val key = SiteCoreBlockEntity.itemKey(item)
    when (slot) {
        ModuleSlot.PRIMARY -> reinforceMineral = key
        ModuleSlot.SECONDARY -> reinforceTwoMineral = key
        ModuleSlot.DIFFUSION -> return
    }
    sanitizeMinerals(definition)
    setChanged()
}

internal fun SiteCoreBlockEntity.storedModuleRefusal(slot: ModuleSlot, stack: ItemStack): String? {
    val definition = Buildings.byId(buildingId) ?: return "这座建筑不能装模块"
    if (!definition.acceptsModules) return "这座建筑不能装模块"
    if (slot == ModuleSlot.DIFFUSION && !definition.acceptsDiffusion) return "这座建筑不能装扩散模块"
    if (stack.isEmpty || !stack.`is`(slot.item)) return "这个槽不能放这种模块"
    return null
}

internal fun SiteCoreBlockEntity.storedExchangeModule(slot: ModuleSlot, incoming: ItemStack): ItemStack {
    val previous = moduleStack(slot).copy()
    setModule(slot, incoming.copyWithCount(1))
    return previous
}

internal fun SiteCoreBlockEntity.storedMineralLabel(slot: ModuleSlot): String {
    val key = when (slot) {
        ModuleSlot.PRIMARY -> reinforceMineral
        ModuleSlot.SECONDARY -> reinforceTwoMineral
        ModuleSlot.DIFFUSION -> ""
    }
    if (key.isEmpty()) return "未选择"
    val definition = Buildings.byId(buildingId) ?: return "未选择"
    return definition.minerals().firstOrNull { SiteCoreBlockEntity.itemKey(it.item) == key }?.name ?: "未选择"
}

internal fun SiteCoreBlockEntity.storedTakeModule(slot: ModuleSlot, player: ServerPlayer): String? {
    val stack = moduleStack(slot)
    if (stack.isEmpty) return null
    if (!player.inventory.add(stack.copy())) return "背包放不下"
    setModule(slot, ItemStack.EMPTY)
    return null
}

internal fun SiteCoreBlockEntity.storedModuleActive(slot: ModuleSlot): Boolean {
    if (!moduleStack(slot).isEmpty) return true
    if (buildingId != Buildings.WALL.id || slot == ModuleSlot.DIFFUSION) return false
    return borrowed(slot)
}

internal fun SiteCoreBlockEntity.storedModuleStack(slot: ModuleSlot): ItemStack = when (slot) {
    ModuleSlot.PRIMARY -> reinforce
    ModuleSlot.SECONDARY -> reinforceTwo
    ModuleSlot.DIFFUSION -> diffuse
}

internal fun SiteCoreBlockEntity.storedChoicesFor(slot: ModuleSlot, definition: BuildingDefinition): List<MineralOutput> {
    val unlocked = definition.minerals().filter { buildingLevel >= it.minLevel }
    return when (slot) {
        ModuleSlot.PRIMARY -> unlocked
        ModuleSlot.SECONDARY -> emptyList()
        ModuleSlot.DIFFUSION -> emptyList()
    }
}

