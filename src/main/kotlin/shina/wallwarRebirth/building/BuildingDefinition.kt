package shina.wallwarRebirth.building

import net.minecraft.resources.Identifier
import net.minecraft.world.item.Item

data class ItemAmount(val item: Item, val count: Int, val name: String)

data class ProductionRule(val minLevel: Int, val everyTicks: Int, val outputs: List<ItemAmount>)

data class UpgradeCost(val toLevel: Int, val costs: List<ItemAmount>)

data class BuildingDefinition(
    val id: String,
    val displayName: String,
    val category: BuildingCategory,
    val maxLevel: Int,
    val structure: (Int) -> Identifier,
    val upgrades: List<UpgradeCost>,
    val production: List<ProductionRule>,
) {
    fun upgradeTo(level: Int): UpgradeCost? = upgrades.firstOrNull { it.toLevel == level }

    fun activeProduction(level: Int): List<ProductionRule> = production.filter { level >= it.minLevel }
}
