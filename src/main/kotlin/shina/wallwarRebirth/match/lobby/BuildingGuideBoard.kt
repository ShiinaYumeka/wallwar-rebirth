package shina.wallwarRebirth.match.lobby

import eu.pb4.sgui.api.elements.GuiElement
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.ChatFormatting
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import shina.wallwarRebirth.building.BuildingDefinition
import shina.wallwarRebirth.building.Buildings
import shina.wallwarRebirth.building.Footprint
import shina.wallwarRebirth.building.OminousBanners
import shina.wallwarRebirth.rules.MatchRules
import kotlin.math.ceil
import kotlin.math.floor

private data class GuideBuilding(
    val id: String,
    val displayName: String,
    val definition: BuildingDefinition?,
    val icon: Item,
    val wonder: Boolean = false,
)

class BuildingHomeBoard(private val viewer: ServerPlayer) : SimpleGui(MenuType.GENERIC_9x6, viewer, false) {
    init {
        title = line("建筑", ChatFormatting.YELLOW)
        fillGlass(this)
        for ((index, entry) in guideBuildings().withIndex()) {
            val lore = if (entry.wonder) listOf("图标待定", "首版不做具体内容", "点击查看") else listOf("点击查看数值")
            setSlot(
                9 + index,
                GuiElement(
                    named(entry.icon, entry.displayName, ChatFormatting.WHITE, lore, stack = iconStack(entry)),
                ) { _, click, _, _ ->
                    if (!click.isLeft || click.shift) return@GuiElement
                    close()
                    BuildingDetailBoard(viewer, entry).open()
                },
            )
        }
        placeBack(this, viewer)
    }

    private fun iconStack(entry: GuideBuilding): ItemStack {
        val stack = ItemStack(entry.icon)
        if (entry.id == Buildings.PILLAGER.id) OminousBanners.apply(stack, viewer.registryAccess())
        return stack
    }
}

private class BuildingDetailBoard(
    private val viewer: ServerPlayer,
    private val entry: GuideBuilding,
) : SimpleGui(MenuType.GENERIC_9x6, viewer, false) {
    init {
        title = line("建筑 · ${entry.displayName}", ChatFormatting.YELLOW)
        fillGlass(this)
        val icon = ItemStack(entry.icon).also {
            if (entry.id == Buildings.PILLAGER.id) OminousBanners.apply(it, viewer.registryAccess())
        }
        setSlot(0, GuiElement(named(entry.icon, entry.displayName, ChatFormatting.WHITE, stack = icon)) { _, _, _, _ -> })
        val definition = entry.definition
        if (entry.wonder || definition == null) {
            setSlot(
                10,
                GuiElement(
                    named(Items.BOOK, "功能", ChatFormatting.WHITE, listOf("首版不做具体内容。全图同时只能有一座奇观。")),
                ) { _, _, _, _ -> },
            )
        } else {
            setSlot(9, GuiElement(named(Items.NAME_TAG, "类别 ${definition.categoryText()}", ChatFormatting.WHITE)) { _, _, _, _ -> })
            setSlot(10, GuiElement(named(Items.GRASS_BLOCK, "占地 ${footprintText(definition.footprint)}", ChatFormatting.WHITE)) { _, _, _, _ -> })
            setSlot(11, GuiElement(named(Items.BOOK, "功能", ChatFormatting.WHITE, listOf(definition.summary))) { _, _, _, _ -> })
            setSlot(12, GuiElement(named(Items.EMERALD, "获得方式", ChatFormatting.WHITE, obtainLines(definition))) { _, _, _, _ -> })
            setSlot(13, GuiElement(named(Items.PURPLE_GLAZED_TERRACOTTA, "模块", ChatFormatting.WHITE, moduleLines(definition))) { _, _, _, _ -> })
            val levelCount = if (definition.maxLevel <= 1) 1 else definition.maxLevel
            for (level in 1..levelCount) {
                setSlot(
                    17 + level,
                    GuiElement(
                        named(
                            entry.icon,
                            "${level} 级",
                            ChatFormatting.WHITE,
                            listOf(levelStats(definition, level)) + if (definition.maxLevel <= 1) listOf("不能升级") else emptyList(),
                            stack = icon.copy(),
                        ),
                    ) { _, _, _, _ -> },
                )
            }
            val seek = seekLines(definition)
            if (seek.isNotEmpty()) {
                setSlot(
                    18 + levelCount,
                    GuiElement(named(Items.TARGET, "索敌", ChatFormatting.WHITE, seek)) { _, _, _, _ -> },
                )
            }
        }
        setSlot(
            45,
            GuiElement(named(Items.OAK_DOOR, "返回", ChatFormatting.WHITE)) { _, click, _, _ ->
                if (!click.isLeft || click.shift) return@GuiElement
                close()
                BuildingHomeBoard(viewer).open()
            },
        )
    }
}

private fun guideBuildings(): List<GuideBuilding> = listOf(
    GuideBuilding(Buildings.BASE.id, Buildings.BASE.displayName, Buildings.BASE, Items.BEACON),
    GuideBuilding(Buildings.WALL.id, Buildings.WALL.displayName, Buildings.WALL, Items.STONE_BRICKS),
    GuideBuilding(Buildings.TOWER.id, Buildings.TOWER.displayName, Buildings.TOWER, Items.CROSSBOW),
    GuideBuilding(Buildings.TESLA.id, Buildings.TESLA.displayName, Buildings.TESLA, Items.LIGHTNING_ROD),
    GuideBuilding(Buildings.BARRACKS.id, Buildings.BARRACKS.displayName, Buildings.BARRACKS, Items.CRAFTER),
    GuideBuilding(Buildings.CANNON.id, Buildings.CANNON.displayName, Buildings.CANNON, Items.DISPENSER),
    GuideBuilding(Buildings.RELAY.id, Buildings.RELAY.displayName, Buildings.RELAY, Items.HOPPER),
    GuideBuilding(Buildings.FARMLAND.id, Buildings.FARMLAND.displayName, Buildings.FARMLAND, Items.COMPOSTER),
    GuideBuilding(Buildings.PILLAGER.id, Buildings.PILLAGER.displayName, Buildings.PILLAGER, Items.WHITE_BANNER),
    GuideBuilding("wonder", "奇观", null, Items.NETHER_STAR, wonder = true),
)

private fun footprintText(footprint: Footprint): String = when (footprint) {
    Footprint.SQUARE_7 -> "7×7"
    Footprint.SQUARE_3 -> "3×3"
    Footprint.SQUARE_1 -> "1×1"
    Footprint.WALL_9 -> "1×9"
}

private fun obtainLines(definition: BuildingDefinition): List<String> {
    if (definition.id == Buildings.BASE.id) {
        return listOf("开局由地图放好，每队一座。不能购买、放置或拆除。") + upgradeLines(definition)
    }
    val buy = when (definition.id) {
        Buildings.WALL.id -> "购买：铜锭 ×${MatchRules.WALL_PRICE_COPPER}"
        Buildings.TOWER.id -> "购买：铜锭 ×${MatchRules.TOWER_PRICE_COPPER}"
        Buildings.TESLA.id -> "购买：铜锭 ×${MatchRules.TESLA_PRICE_COPPER}"
        Buildings.BARRACKS.id -> "购买：铁锭 ×${MatchRules.BARRACKS_PRICE_IRON}"
        Buildings.CANNON.id -> "购买：铁锭 ×${MatchRules.CANNON_PRICE_IRON}、铜锭 ×${MatchRules.CANNON_PRICE_COPPER}"
        Buildings.RELAY.id -> "购买：铁锭 ×${MatchRules.RELAY_PRICE_IRON}、铜锭 ×${MatchRules.RELAY_PRICE_COPPER}"
        Buildings.FARMLAND.id -> "购买：铁锭 ×${MatchRules.FARMLAND_PRICE_IRON}、铜锭 ×${MatchRules.FARMLAND_PRICE_COPPER}"
        Buildings.PILLAGER.id -> "购买：铁锭 ×${MatchRules.PILLAGER_TOWER_PRICE_IRON}"
        else -> "购买方式见商店"
    }
    return listOf(buy) + upgradeLines(definition)
}

private fun upgradeLines(definition: BuildingDefinition): List<String> =
    definition.upgrades.map { it.describe() }

private fun moduleLines(definition: BuildingDefinition): List<String> {
    if (!definition.acceptsModules) return listOf("无模块槽")
    val lines = arrayListOf("有强化槽、强化槽 II 和扩散槽，效果见强化模块。")
    if (definition.id == Buildings.TESLA.id || definition.id == Buildings.PILLAGER.id) {
        lines.add("该建筑的模块效果仍待定或暂定。")
    }
    return lines
}

private fun levelStats(definition: BuildingDefinition, level: Int): String {
    val health = definition.maxHealth(level)
    val index = (level - 1).coerceAtLeast(0)
    val extra = when (definition.id) {
        Buildings.BASE.id -> definition.activeProduction(level).joinToString("；") { it.describe() }
        Buildings.TOWER.id -> {
            val range = MatchRules.TOWER_RANGE[index].toInt()
            val speed = MatchRules.TOWER_SPEED[index]
            val damage = ceil(speed * MatchRules.TOWER_ARROW_BASE_DAMAGE).toInt()
            val seconds = MatchRules.TOWER_INTERVAL[index].toDouble() / MatchRules.TICKS_PER_SECOND
            val interval = if (seconds == seconds.toInt().toDouble()) seconds.toInt().toString() else seconds.toString()
            "射程 $range，箭速 ${speed.toInt()}，伤害 $damage，间隔 $interval 秒"
        }
        Buildings.TESLA.id -> {
            val range = MatchRules.TESLA_RANGE[index].toInt()
            "射程 $range，闪电伤害 ${MatchRules.TESLA_DAMAGE.toInt()}，间隔 ${MatchRules.TESLA_INTERVAL / MatchRules.TICKS_PER_SECOND} 秒"
        }
        Buildings.CANNON.id -> {
            val center = MatchRules.CANNON_CENTER_DAMAGE[index]
            val building = floor(center * MatchRules.CANNON_BUILDING_FRACTION + 0.5).toInt()
            "射程 ${MatchRules.CANNON_RANGE.toInt()}，间隔 ${MatchRules.CANNON_INTERVAL / MatchRules.TICKS_PER_SECOND} 秒，中心伤害 ${center.toInt()}，建筑伤害 $building"
        }
        else -> ""
    }
    return if (extra.isEmpty()) "生命 $health" else "生命 $health，$extra"
}

private fun seekLines(definition: BuildingDefinition): List<String> = when (definition.id) {
    Buildings.TOWER.id -> listOf("优先最近的敌方唤灵，没有再打最近的敌方玩家，不打我方，不拆建筑。")
    Buildings.TESLA.id -> listOf("只选射程内最近的敌方玩家，不打唤灵，不拆建筑，旁观与创造不选。")
    Buildings.CANNON.id -> listOf("射程内先锁最近的敌方唤灵，没有再锁最近的敌方玩家，两类都没有时锁敌方建筑（基地也算）。")
    else -> emptyList()
}
