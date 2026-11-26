package shina.wallwarRebirth.match.lobby

import eu.pb4.sgui.api.elements.GuiElement
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.ChatFormatting
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import shina.wallwarRebirth.copy.DescriptionStyle
import shina.wallwarRebirth.copy.GameCopy
import shina.wallwarRebirth.building.BuildingDefinition
import shina.wallwarRebirth.building.BuildingModuleEffects
import shina.wallwarRebirth.building.Buildings
import shina.wallwarRebirth.building.Footprint
import shina.wallwarRebirth.building.ItemAmount
import shina.wallwarRebirth.building.ModuleSlot
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

private data class GuideOption(
    val item: Item,
    val name: String,
    val lore: List<String> = emptyList(),
    val count: Int? = null,
    val stack: ItemStack? = null,
)

class BuildingHomeBoard(private val viewer: ServerPlayer) : SimpleGui(MenuType.GENERIC_9x6, viewer, false) {
    init {
        title = line("建筑", ChatFormatting.YELLOW)
        fillGlass(this)
        for ((index, entry) in guideBuildings().withIndex()) {
            val lore = if (entry.wonder) listOf("未实装", "图鉴占位", "点击查看") else listOf("点击查看说明")
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
                    named(Items.BOOK, "说明", ChatFormatting.WHITE, loreComponents = GameCopy.buildingLines("wonder").map { DescriptionStyle.styled(it) }),
                ) { _, _, _, _ -> },
            )
        } else {
            setSlot(9, GuiElement(named(Items.NAME_TAG, "类别 ${definition.categoryText()}", ChatFormatting.WHITE)) { _, _, _, _ -> })
            setSlot(10, GuiElement(named(Items.GRASS_BLOCK, "占地 ${footprintText(definition.footprint)}", ChatFormatting.WHITE)) { _, _, _, _ -> })
            setSlot(11, GuiElement(named(Items.BOOK, "说明", ChatFormatting.WHITE, loreComponents = GameCopy.buildingLines(definition.id).map { DescriptionStyle.styled(it) })) { _, _, _, _ -> })
            placeOptions(12, costOptions(definition), 6)
            placeOptions(18, moduleOptions(definition), 3)
            val seek = seekLines(definition)
            if (seek.isNotEmpty()) {
                setSlot(21, GuiElement(named(Items.TARGET, "索敌", ChatFormatting.WHITE, seek)) { _, _, _, _ -> })
            }
            val levelCount = if (definition.maxLevel <= 1) 1 else definition.maxLevel
            val levelStarts = listOf(27, 36, 46)
            for (level in 1..levelCount) {
                val start = levelStarts.getOrElse(level - 1) { 27 + (level - 1) * 9 }
                val limit = if (start == 46) 7 else 9
                placeOptions(start, levelOptions(definition, level, icon), limit)
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

    private fun placeOptions(start: Int, options: List<GuideOption>, limit: Int) {
        for ((index, option) in options.withIndex()) {
            if (index >= limit) break
            setSlot(
                start + index,
                GuiElement(
                    named(
                        option.item,
                        option.name,
                        ChatFormatting.WHITE,
                        option.lore,
                        stack = option.stack ?: ItemStack(option.item),
                        count = option.count,
                    ),
                ) { _, _, _, _ -> },
            )
        }
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
    GuideBuilding(Buildings.WORKSHOP.id, Buildings.WORKSHOP.displayName, Buildings.WORKSHOP, Items.BLAST_FURNACE),
    GuideBuilding(Buildings.CRYSTAL_FURNACE.id, Buildings.CRYSTAL_FURNACE.displayName, Buildings.CRYSTAL_FURNACE, Items.FURNACE),
    GuideBuilding(Buildings.GUIDED_CANNON.id, Buildings.GUIDED_CANNON.displayName, Buildings.GUIDED_CANNON, Items.TNT),
    GuideBuilding(Buildings.TELEPORT_SPIRE.id, Buildings.TELEPORT_SPIRE.displayName, Buildings.TELEPORT_SPIRE, Items.AMETHYST_SHARD),
    GuideBuilding(Buildings.QUAKE.id, Buildings.QUAKE.displayName, Buildings.QUAKE, Items.LODESTONE),
    GuideBuilding(Buildings.MINERAL_REACTOR.id, Buildings.MINERAL_REACTOR.displayName, Buildings.MINERAL_REACTOR, Buildings.MINERAL_REACTOR.icon),
    GuideBuilding("wonder", "奇观", null, Items.NETHER_STAR, wonder = true),
)

private fun footprintText(footprint: Footprint): String = when (footprint) {
    Footprint.SQUARE_7 -> "7×7"
    Footprint.SQUARE_9 -> "9×9"
    Footprint.SQUARE_5 -> "5×5"
    Footprint.SQUARE_3 -> "3×3"
    Footprint.SQUARE_1 -> "1×1"
    Footprint.WALL_9 -> "1×9"
}

private fun costOptions(definition: BuildingDefinition): List<GuideOption> {
    val purchase = if (definition.id == Buildings.BASE.id) {
        listOf("开局由地图放好，每队一座。不能购买、放置或拆除。")
    } else {
        val costs = purchaseCosts(definition)
        if (costs.isEmpty()) listOf("不能购买") else costs.map { "${it.name} ×${it.count}" }
    }
    val options = ArrayList<GuideOption>()
    options.add(GuideOption(Items.GOLD_INGOT, "1 级", purchase, 1))
    for (upgrade in definition.upgrades) {
        options.add(
            GuideOption(
                Items.GOLD_INGOT,
                "${upgrade.toLevel} 级",
                upgrade.costs.map { "${it.name} ×${it.count}" },
                upgrade.toLevel,
            ),
        )
    }
    return options
}

private fun purchaseCosts(definition: BuildingDefinition): List<ItemAmount> = when (definition.id) {
    Buildings.WALL.id -> listOf(ItemAmount(Items.COPPER_INGOT, MatchRules.WALL_PRICE_COPPER, "铜锭"))
    Buildings.TOWER.id -> listOf(ItemAmount(Items.COPPER_INGOT, MatchRules.TOWER_PRICE_COPPER, "铜锭"))
    Buildings.TESLA.id -> listOf(ItemAmount(Items.COPPER_INGOT, MatchRules.TESLA_PRICE_COPPER, "铜锭"))
    Buildings.BARRACKS.id -> listOf(ItemAmount(Items.IRON_INGOT, MatchRules.BARRACKS_PRICE_IRON, "铁锭"))
    Buildings.CANNON.id -> listOf(
        ItemAmount(Items.COPPER_INGOT, MatchRules.CANNON_PRICE_COPPER, "铜锭"),
    )
    Buildings.RELAY.id -> listOf(
        ItemAmount(Items.IRON_INGOT, MatchRules.RELAY_PRICE_IRON, "铁锭"),
        ItemAmount(Items.COPPER_INGOT, MatchRules.RELAY_PRICE_COPPER, "铜锭"),
    )
    Buildings.FARMLAND.id -> listOf(
        ItemAmount(Items.IRON_INGOT, MatchRules.FARMLAND_PRICE_IRON, "铁锭"),
        ItemAmount(Items.COPPER_INGOT, MatchRules.FARMLAND_PRICE_COPPER, "铜锭"),
    )
    Buildings.PILLAGER.id -> listOf(ItemAmount(Items.IRON_INGOT, MatchRules.PILLAGER_TOWER_PRICE_IRON, "铁锭"))
    Buildings.WORKSHOP.id -> listOf(
        ItemAmount(Items.DIAMOND, MatchRules.WORKSHOP_PRICE_DIAMOND, "钻石"),
        ItemAmount(Items.LAPIS_LAZULI, MatchRules.WORKSHOP_PRICE_LAPIS, "青金石"),
    )
    Buildings.CRYSTAL_FURNACE.id -> listOf(
        ItemAmount(Items.IRON_INGOT, MatchRules.CRYSTAL_FURNACE_PRICE_IRON, "铁锭"),
        ItemAmount(Items.COPPER_INGOT, MatchRules.CRYSTAL_FURNACE_PRICE_COPPER, "铜锭"),
    )
    Buildings.GUIDED_CANNON.id -> listOf(
        ItemAmount(Items.COPPER_INGOT, MatchRules.GUIDED_CANNON_PRICE_COPPER, "铜锭"),
        ItemAmount(Items.IRON_INGOT, MatchRules.GUIDED_CANNON_PRICE_IRON, "铁锭"),
        ItemAmount(Items.DIAMOND, MatchRules.GUIDED_CANNON_PRICE_DIAMOND, "钻石"),
    )
    Buildings.TELEPORT_SPIRE.id -> listOf(
        ItemAmount(Items.IRON_INGOT, MatchRules.TELEPORT_SPIRE_PRICE_IRON, "铁锭"),
        ItemAmount(Items.COPPER_INGOT, MatchRules.TELEPORT_SPIRE_PRICE_COPPER, "铜锭"),
        ItemAmount(Items.DIAMOND, MatchRules.TELEPORT_SPIRE_PRICE_DIAMOND, "钻石"),
    )
    Buildings.QUAKE.id -> listOf(ItemAmount(Items.DIAMOND, MatchRules.QUAKE_PRICE_DIAMOND, "钻石"))
    else -> emptyList()
}

private fun moduleOptions(definition: BuildingDefinition): List<GuideOption> {
    if (!definition.acceptsModules) return listOf(GuideOption(Items.BARRIER, "模块", listOf("无模块槽")))
    return listOf(
        GuideOption(
            Items.LIGHT_BLUE_GLAZED_TERRACOTTA,
            "强化模块",
            BuildingModuleEffects.lines(definition, ModuleSlot.PRIMARY),
        ),
        GuideOption(
            Items.LIGHT_GRAY_GLAZED_TERRACOTTA,
            "强化模块 2",
            BuildingModuleEffects.lines(definition, ModuleSlot.SECONDARY),
        ),
        GuideOption(
            Items.PURPLE_GLAZED_TERRACOTTA,
            "扩散模块",
            BuildingModuleEffects.lines(definition, ModuleSlot.DIFFUSION),
        ),
    )
}

private fun levelOptions(definition: BuildingDefinition, level: Int, icon: ItemStack): List<GuideOption> {
    val health = definition.maxHealth(level)
    val index = (level - 1).coerceAtLeast(0)
    val combat = combatOptions(definition, index)
    val notes = ArrayList<String>()
    if (combat.isEmpty()) {
        notes.addAll(definition.details.filter { it.minLevel == level }.map { it.text })
        notes.addAll(definition.activeProduction(level).filter { it.minLevel == level }.map { it.describe() })
    }
    if (definition.maxLevel <= 1) notes.add("不能升级")
    val options = ArrayList<GuideOption>()
    options.add(
        GuideOption(
            definition.icon,
            "${level} 级",
            notes,
            count = level,
            stack = icon.copy(),
        ),
    )
    options.add(GuideOption(Items.APPLE, "生命 $health"))
    options.addAll(combat)
    return options
}

private fun combatOptions(definition: BuildingDefinition, index: Int): List<GuideOption> = when (definition.id) {
    Buildings.TOWER.id -> {
        val range = MatchRules.TOWER_RANGE[index].toInt()
        val speed = MatchRules.TOWER_SPEED[index].toInt()
        val damage = ceil(MatchRules.TOWER_SPEED[index] * MatchRules.TOWER_ARROW_BASE_DAMAGE).toInt()
        val intervalTicks = MatchRules.TOWER_INTERVAL[index]
        listOf(
            GuideOption(Items.ENDER_EYE, "射程 $range", count = range),
            GuideOption(Items.ARROW, "箭速 $speed", count = speed),
            GuideOption(Items.SPECTRAL_ARROW, "盔甲前伤害 $damage", count = damage),
            intervalOption(intervalTicks),
        )
    }
    Buildings.TESLA.id -> {
        val range = MatchRules.TESLA_RANGE.toInt()
        val damage = MatchRules.TESLA_DAMAGE[index].toInt()
        val wind = MatchRules.TESLA_WIND_TICKS
        val playerWind = MatchRules.TESLA_WIND_TICKS / 2
        listOf(
            GuideOption(Items.ENDER_EYE, "射程 $range", count = range),
            GuideOption(Items.LIGHTNING_ROD, "闪电伤害 $damage", count = damage),
            GuideOption(Items.WIND_CHARGE, "蓄风 ${MatchRules.secondsText(wind)} 秒", count = wholeSeconds(wind)),
            GuideOption(Items.SUGAR, "玩家蓄风 ${MatchRules.secondsText(playerWind)} 秒", count = wholeSeconds(playerWind)),
            intervalOption(MatchRules.TESLA_INTERVAL[index]),
        )
    }
    Buildings.CANNON.id -> {
        val range = MatchRules.CANNON_RANGE.toInt()
        val center = MatchRules.CANNON_CENTER_DAMAGE[index].toInt()
        val building = floor(MatchRules.CANNON_CENTER_DAMAGE[index] * MatchRules.CANNON_BUILDING_FRACTION + 0.5).toInt()
        listOf(
            GuideOption(Items.ENDER_EYE, "射程 $range", count = range),
            intervalOption(MatchRules.CANNON_INTERVAL),
            GuideOption(Items.TNT, "中心伤害 $center", count = center),
            GuideOption(Items.STONE_BRICKS, "建筑伤害 $building", count = building),
        )
    }
    Buildings.CRYSTAL_FURNACE.id -> {
        val rate = MatchRules.CRYSTAL_FURNACE_SUCCESS_RATE[index]
        val ticks = MatchRules.CRYSTAL_FURNACE_CONVERT_TICKS
        listOf(
            GuideOption(Items.CLOCK, "转化 ${MatchRules.secondsText(ticks)} 秒", count = wholeSeconds(ticks)),
            GuideOption(Items.DIAMOND, "成功率 $rate%", count = rate),
        )
    }
    Buildings.GUIDED_CANNON.id -> {
        val cooldown = MatchRules.GUIDED_CANNON_COOLDOWN[index]
        val center = MatchRules.GUIDED_CANNON_CENTER_DAMAGE.toInt()
        val building = floor(MatchRules.GUIDED_CANNON_CENTER_DAMAGE * MatchRules.CANNON_BUILDING_FRACTION + 0.5).toInt()
        listOf(
            GuideOption(Items.CLOCK, "冷却 ${MatchRules.secondsText(cooldown)} 秒", count = wholeSeconds(cooldown)),
            GuideOption(Items.TNT, "中心伤害 $center", count = center),
            GuideOption(Items.STONE_BRICKS, "建筑伤害 $building", count = building),
        )
    }
    Buildings.TELEPORT_SPIRE.id -> {
        val cooldown = MatchRules.TELEPORT_SPIRE_COOLDOWN[index]
        listOf(GuideOption(Items.CLOCK, "冷却 ${MatchRules.secondsText(cooldown)} 秒", count = wholeSeconds(cooldown)))
    }
    Buildings.QUAKE.id -> {
        val range = MatchRules.QUAKE_RANGE.toInt()
        listOf(
            GuideOption(Items.ENDER_EYE, "射程 $range", count = range),
            intervalOption(MatchRules.QUAKE_INTERVAL[index]),
            GuideOption(Items.IRON_SWORD, "基础伤害 ${MatchRules.QUAKE_DAMAGE}", count = MatchRules.QUAKE_DAMAGE),
            GuideOption(Items.FIRE_CHARGE, "溅射 ${MatchRules.QUAKE_SPLASH.toInt()}", count = MatchRules.QUAKE_SPLASH.toInt()),
        )
    }
    else -> emptyList()
}

private fun intervalOption(ticks: Int): GuideOption =
    GuideOption(Items.CLOCK, "间隔 ${MatchRules.secondsText(ticks)} 秒", count = wholeSeconds(ticks))

private fun wholeSeconds(ticks: Int): Int? =
    if (ticks > 0 && ticks % MatchRules.TICKS_PER_SECOND == 0) ticks / MatchRules.TICKS_PER_SECOND else null

private fun seekLines(definition: BuildingDefinition): List<String> = when (definition.id) {
    Buildings.TOWER.id -> listOf("优先最近的、没有被实心方块挡住的敌方唤灵，没有再打没有被挡住的敌方玩家。不打我方，不拆建筑。")
    Buildings.TESLA.id -> listOf("16 格内优先最近的、没有蓄风的敌方玩家，没有再打没有蓄风的敌方唤灵。已有蓄风的不选。不拆建筑。旁观与创造不选。")
    Buildings.CANNON.id -> listOf("射程内先锁最近的敌方唤灵，没有再锁最近的敌方玩家，两类都没有时锁敌方建筑（基地也算）。")
    Buildings.GUIDED_CANNON.id -> listOf("冷却就绪后在面板开炮，以旁观视角从天上选定地表，左键选定后再按左键确认。潜行取消，不进入冷却。爆炸不破坏方块，不伤友方、无队伍和自身。")
    Buildings.TELEPORT_SPIRE.id -> listOf("冷却就绪后在面板传送，选定方式与晶核制导炮相同。确认后玩家传到安全落点，正在跟随的同队唤灵一起传；没有落点的唤灵留下。")
    Buildings.QUAKE.id -> listOf("不要求视线。优先最大生命值最高的敌方，然后更近，然后当前生命更低，然后更小标识。溅射伤害相等、无衰减。只打敌方玩家和唤灵。")
    else -> emptyList()
}
