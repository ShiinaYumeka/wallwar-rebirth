package shina.wallwarRebirth.building

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import shina.wallwarRebirth.rules.MatchRules
import shina.wallwarRebirth.shop.TeamWarehouses
import kotlin.math.min

internal fun SiteCoreBlockEntity.rolled(world: ServerLevel, mineral: MineralOutput): Boolean = when {
    mineral.chancePercent > 0 -> world.random.nextInt(100) < mineral.chancePercent
    mineral.chance <= 1 -> true
    else -> world.random.nextInt(mineral.chance) == 0
}

internal fun SiteCoreBlockEntity.insert(stack: ItemStack): ItemStack {
    val remaining = stack.copy()
    for (slot in 0 until storage.containerSize) {
        if (remaining.isEmpty) break
        val existing = storage.getItem(slot)
        if (existing.isEmpty) {
            storage.setItem(slot, remaining)
            return ItemStack.EMPTY
        }
        if (!ItemStack.isSameItemSameComponents(existing, remaining)) continue
        val space = existing.maxStackSize - existing.count
        if (space <= 0) continue
        val move = minOf(space, remaining.count)
        existing.grow(move)
        remaining.shrink(move)
    }
    return remaining
}

internal fun SiteCoreBlockEntity.intervalOf(definition: BuildingDefinition, mineral: MineralOutput): Int? {
    if (buildingLevel < mineral.minLevel) return null
    val key = SiteCoreBlockEntity.itemKey(mineral.item)
    val multiplier = if (reinforce.isEmpty.not() && reinforceMineral == key) 2 else 1
    return maxOf(MatchRules.MIN_PRODUCTION_TICKS, mineral.baseTicks / multiplier)
}

internal fun SiteCoreBlockEntity.storedProduceCycle() {
    val definition = Buildings.byId(buildingId) ?: return
    if (buildingId != Buildings.BASE.id) return
    val world = level as? ServerLevel ?: return
    for (mineral in definition.minerals()) {
        if (intervalOf(definition, mineral) == null) continue
        if (!rolled(world, mineral)) continue
        TeamWarehouses.acceptAll(team, ItemStack(mineral.item, mineral.count))
    }
    setChanged()
}

internal fun SiteCoreBlockEntity.storedOffer(stack: ItemStack) {
    val remaining = stack.copy()
    while (!remaining.isEmpty) {
        val piece = remaining.copy()
        val max = piece.maxStackSize
        if (piece.count > max) piece.count = max
        val leftover = insert(piece)
        val accepted = piece.count - leftover.count
        if (accepted <= 0) return
        remaining.shrink(accepted)
    }
}

internal fun SiteCoreBlockEntity.storedProductionLines(): List<Pair<String, Boolean>> {
    val definition = Buildings.byId(buildingId) ?: return emptyList()
    return definition.minerals().map { mineral ->
        val interval = intervalOf(definition, mineral)
        val unlocked = buildingLevel >= mineral.minLevel
        val text = when {
            !unlocked -> "${mineral.name}：${mineral.describeBase()}"
            interval == null -> "${mineral.name}：不生成"
            else -> "${mineral.name}：${mineral.describe(interval)}"
        }
        text to (unlocked && interval != null)
    }
}

internal fun SiteCoreBlockEntity.storedGeneratableMinerals(definition: BuildingDefinition): List<MineralOutput> =
    definition.minerals().filter { buildingLevel >= it.minLevel }

