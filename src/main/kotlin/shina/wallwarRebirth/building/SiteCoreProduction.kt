package shina.wallwarRebirth.building

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import shina.wallwarRebirth.rules.MatchRules
import shina.wallwarRebirth.shop.match.TeamWarehouses

internal fun rolled(world: ServerLevel, mineral: MineralOutput, percent: Int = mineral.chancePercent): Boolean = when {
    percent > 0 -> world.random.nextInt(100) < percent
    mineral.chance <= 1 -> true
    else -> world.random.nextInt(mineral.chance) == 0
}

internal fun SiteCoreBlockEntity.diamondPercent(mineral: MineralOutput): Int {
    if (buildingId != Buildings.BASE.id || mineral.item != Items.DIAMOND || mineral.chancePercent <= 0) {
        return mineral.chancePercent
    }
    val world = level as? ServerLevel ?: return mineral.chancePercent
    return if (SiteCores.hasLiveWorkshop(world, team)) MatchRules.WORKSHOP_DIAMOND_PERCENT else mineral.chancePercent
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

internal fun SiteCoreBlockEntity.intervalOf(mineral: MineralOutput): Int? {
    if (buildingLevel < mineral.minLevel) return null
    val boosted = (buildingId == Buildings.BASE.id || buildingId == Buildings.MINERAL_REACTOR.id) && !reinforce.isEmpty
    val levelOneGem = buildingId == Buildings.BASE.id &&
        buildingLevel == 1 &&
        (mineral.item == Items.REDSTONE || mineral.item == Items.LAPIS_LAZULI)
    val ticks = when {
        levelOneGem && boosted -> MatchRules.BASE_LEVEL1_GEM_BOOSTED_TICKS
        levelOneGem -> MatchRules.BASE_LEVEL1_GEM_TICKS
        boosted -> MatchRules.BASE_BOOSTED_TICKS
        else -> mineral.baseTicks
    }
    return maxOf(MatchRules.MIN_PRODUCTION_TICKS, ticks)
}

internal fun SiteCoreBlockEntity.storedProduceCycle() {
    val definition = Buildings.byId(buildingId) ?: return
    if (buildingId != Buildings.BASE.id && buildingId != Buildings.MINERAL_REACTOR.id) return
    if (buildingId == Buildings.MINERAL_REACTOR.id && team.isEmpty()) return
    val world = level as? ServerLevel ?: return
    for (mineral in definition.minerals()) {
        if (intervalOf(mineral) == null) continue
        if (!rolled(world, mineral, diamondPercent(mineral))) continue
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
        val interval = intervalOf(mineral)
        val unlocked = buildingLevel >= mineral.minLevel
        val text = when {
            !unlocked -> "${mineral.name}：${mineral.describeBase()}"
            interval == null -> "${mineral.name}：不生成"
            else -> "${mineral.name}：${mineral.describe(interval, diamondPercent(mineral))}"
        }
        text to (unlocked && interval != null)
    }
}

