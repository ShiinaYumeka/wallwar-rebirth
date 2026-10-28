package shina.wallwarRebirth.building

import net.minecraft.world.item.Items
import shina.wallwarRebirth.WallwarMod

object Buildings {
    val REACTOR = BuildingDefinition(
        id = "reactor",
        displayName = "矿物反应堆",
        category = BuildingCategory.CORE,
        maxLevel = 3,
        structure = { level -> WallwarMod.idOf("building/reactor/level_$level") },
        upgrades = listOf(
            UpgradeCost(
                2,
                listOf(
                    ItemAmount(Items.IRON_INGOT, 30, "铁锭"),
                    ItemAmount(Items.COPPER_INGOT, 30, "铜锭"),
                ),
            ),
            UpgradeCost(
                3,
                listOf(
                    ItemAmount(Items.REDSTONE_BLOCK, 16, "红石块"),
                    ItemAmount(Items.LAPIS_BLOCK, 16, "青金石块"),
                ),
            ),
        ),
        production = listOf(
            ProductionRule(
                1,
                200,
                listOf(
                    ItemAmount(Items.IRON_INGOT, 2, "铁锭"),
                    ItemAmount(Items.COPPER_INGOT, 2, "铜锭"),
                ),
            ),
            ProductionRule(
                2,
                200,
                listOf(
                    ItemAmount(Items.REDSTONE, 4, "红石"),
                    ItemAmount(Items.LAPIS_LAZULI, 4, "青金石"),
                ),
            ),
            ProductionRule(
                3,
                1200,
                listOf(ItemAmount(Items.DIAMOND, 1, "钻石")),
            ),
        ),
    )

    private val all = listOf(REACTOR)

    fun byId(id: String): BuildingDefinition? = all.firstOrNull { it.id == id }

    fun ofCategory(category: BuildingCategory): List<BuildingDefinition> = all.filter { it.category == category }
}
