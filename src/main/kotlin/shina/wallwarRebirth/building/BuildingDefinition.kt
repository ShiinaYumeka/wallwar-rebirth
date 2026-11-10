package shina.wallwarRebirth.building

import net.minecraft.resources.Identifier
import net.minecraft.world.item.Item

data class ItemAmount(val item: Item, val count: Int, val name: String)

data class ProductionRule(
    val minLevel: Int,
    val everyTicks: Int,
    val outputs: List<ItemAmount>,
    val chance: Int = 1,
    val chancePercent: Int = 0,
) {
    fun describe(): String {
        val seconds = everyTicks / 20
        val parts = outputs.joinToString("、") { "${it.count} ${it.name}" }
        val odds = when {
            chancePercent > 0 -> "有 $chancePercent% 概率 "
            chance <= 1 -> ""
            else -> "有 1/$chance 概率 "
        }
        return "每 $seconds 秒 $odds$parts"
    }
}

data class UpgradeCost(val toLevel: Int, val costs: List<ItemAmount>) {
    fun describe(): String {
        val parts = costs.joinToString("、") { "${it.count} ${it.name}" }
        return "升到${levelMark(toLevel)}级：$parts"
    }
}

data class LevelNote(val minLevel: Int, val text: String)

enum class Footprint {
    SQUARE_7,
    SQUARE_3,
    SQUARE_1,
    WALL_9,
}

data class MineralOutput(
    val item: Item,
    val count: Int,
    val name: String,
    val baseTicks: Int,
    val minLevel: Int,
    val chance: Int = 1,
    val chancePercent: Int = 0,
)

fun levelMark(level: Int): String = when (level) {
    1 -> "一"
    2 -> "二"
    3 -> "三"
    else -> level.toString()
}

data class BuildingDefinition(
    val id: String,
    val displayName: String,
    val category: BuildingCategory,
    val maxLevel: Int,
    val structure: (Int) -> Identifier,
    val upgrades: List<UpgradeCost>,
    val production: List<ProductionRule>,
    val healthByLevel: List<Int>,
    val icon: Item,
    val summary: String,
    val details: List<LevelNote> = emptyList(),
    val showsContainer: Boolean = false,
    val footprint: Footprint = Footprint.SQUARE_7,
    val acceptsModules: Boolean = false,
    val acceptsDiffusion: Boolean = false,
    val defensive: Boolean = false,
) {
    fun categoryText(): String = if (defensive) "${category.label}（防御）" else category.label

    fun upgradeTo(level: Int): UpgradeCost? = upgrades.firstOrNull { it.toLevel == level }

    fun activeProduction(level: Int): List<ProductionRule> = production.filter { level >= it.minLevel }

    fun minerals(): List<MineralOutput> = production.flatMap { rule ->
        rule.outputs.map {
            MineralOutput(it.item, it.count, it.name, rule.everyTicks, rule.minLevel, rule.chance, rule.chancePercent)
        }
    }

    fun maxHealth(level: Int): Int = healthByLevel.getOrElse(level - 1) { 0 }
}
