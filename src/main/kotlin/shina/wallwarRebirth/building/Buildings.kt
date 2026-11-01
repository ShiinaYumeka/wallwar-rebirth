package shina.wallwarRebirth.building

import net.minecraft.world.item.Items
import shina.wallwarRebirth.WallwarMod
import shina.wallwarRebirth.match.MatchRules
import kotlin.math.ceil

object Buildings {
    val BASE = BuildingDefinition(
        id = "base",
        displayName = "基地",
        category = BuildingCategory.CORE,
        maxLevel = 1,
        structure = { level -> WallwarMod.idOf("building/base/level_$level") },
        upgrades = emptyList(),
        production = emptyList(),
        healthByLevel = listOf(MatchRules.BASE_HEALTH),
        icon = Items.BEACON,
        summary = "胜负目标，也是商店入口。不能购买、升级、放置或拆除。",
    )

    val REACTOR = BuildingDefinition(
        id = "reactor",
        displayName = "矿物反应堆",
        category = BuildingCategory.NORMAL,
        maxLevel = 3,
        structure = { level -> WallwarMod.idOf("building/reactor/level_$level") },
        upgrades = listOf(
            UpgradeCost(
                2,
                listOf(
                    ItemAmount(Items.COPPER_INGOT, MatchRules.REACTOR_UPGRADE_2_COPPER, "铜锭"),
                    ItemAmount(Items.IRON_INGOT, MatchRules.REACTOR_UPGRADE_2_IRON, "铁锭"),
                ),
            ),
            UpgradeCost(
                3,
                listOf(
                    ItemAmount(Items.IRON_INGOT, MatchRules.REACTOR_UPGRADE_3_IRON, "铁锭"),
                    ItemAmount(Items.COPPER_INGOT, MatchRules.REACTOR_UPGRADE_3_COPPER, "铜锭"),
                ),
            ),
        ),
        production = listOf(
            ProductionRule(
                1,
                MatchRules.REACTOR_IRON_TICKS,
                listOf(
                    ItemAmount(Items.IRON_INGOT, MatchRules.REACTOR_IRON_COUNT, "铁锭"),
                    ItemAmount(Items.COPPER_INGOT, MatchRules.REACTOR_COPPER_COUNT, "铜锭"),
                ),
            ),
            ProductionRule(
                2,
                MatchRules.REACTOR_REDSTONE_TICKS,
                listOf(
            ItemAmount(Items.REDSTONE, MatchRules.REACTOR_REDSTONE_COUNT, "红石"),
            ItemAmount(Items.LAPIS_LAZULI, MatchRules.REACTOR_LAPIS_COUNT, "青金石"),
                ),
            ),
            ProductionRule(
                3,
                MatchRules.REACTOR_DIAMOND_TICKS,
                listOf(ItemAmount(Items.DIAMOND, MatchRules.REACTOR_DIAMOND_COUNT, "钻石")),
            ),
        ),
        healthByLevel = MatchRules.REACTOR_HEALTH,
        icon = Items.BLAST_FURNACE,
        summary = "产出进入自己的容器。",
        showsContainer = true,
        acceptsModules = true,
    )

    val WALL = BuildingDefinition(
        id = "wall",
        displayName = "城墙",
        category = BuildingCategory.NORMAL,
        maxLevel = 3,
        structure = { level -> WallwarMod.idOf("building/wall/level_$level") },
        upgrades = listOf(
            UpgradeCost(2, listOf(ItemAmount(Items.COPPER_INGOT, MatchRules.WALL_UPGRADE_2_COPPER, "铜锭"))),
            UpgradeCost(3, listOf(ItemAmount(Items.COPPER_INGOT, MatchRules.WALL_UPGRADE_3_COPPER, "铜锭"))),
        ),
        production = emptyList(),
        healthByLevel = MatchRules.WALL_HEALTH,
        icon = Items.STONE_BRICKS,
        summary = "一截有碰撞的墙，用来让敌方怪物先打它，而不是直接打到后面的核心。没有攻击，没有产出。",
        footprint = Footprint.WALL_9,
    )

    val TOWER = BuildingDefinition(
        id = "tower",
        displayName = "弩架",
        category = BuildingCategory.NORMAL,
        maxLevel = 3,
        structure = { level -> WallwarMod.idOf("building/tower/level_$level") },
        upgrades = listOf(
            UpgradeCost(2, listOf(ItemAmount(Items.COPPER_INGOT, MatchRules.TOWER_UPGRADE_2_COPPER, "铜锭"))),
            UpgradeCost(3, listOf(ItemAmount(Items.COPPER_INGOT, MatchRules.TOWER_UPGRADE_3_COPPER, "铜锭"))),
        ),
        production = emptyList(),
        healthByLevel = MatchRules.TOWER_HEALTH,
        icon = Items.CROSSBOW,
        summary = "核心方块上平放一把弩，水平转向当前目标。射出不会被捡起的箭。优先最近的敌方唤灵怪物，没有怪物时打最近的敌方玩家。不打我方，不拆建筑，不消耗箭。",
        footprint = Footprint.SQUARE_1,
        details = listOf(
            LevelNote(1, towerNote(0)),
            LevelNote(2, towerNote(1)),
            LevelNote(3, towerNote(2)),
        ),
    )

    val BARRACKS = BuildingDefinition(
        id = "barracks",
        displayName = "兵营",
        category = BuildingCategory.NORMAL,
        maxLevel = 3,
        structure = { level -> WallwarMod.idOf("building/barracks/level_$level") },
        upgrades = listOf(
            UpgradeCost(2, listOf(ItemAmount(Items.IRON_INGOT, MatchRules.BARRACKS_UPGRADE_2_IRON, "铁锭"))),
            UpgradeCost(
                3,
                listOf(
                    ItemAmount(Items.IRON_INGOT, MatchRules.BARRACKS_UPGRADE_3_IRON, "铁锭"),
                    ItemAmount(Items.DIAMOND, MatchRules.BARRACKS_UPGRADE_3_DIAMOND, "钻石"),
                ),
            ),
        ),
        production = emptyList(),
        healthByLevel = MatchRules.BARRACKS_HEALTH,
        icon = Items.IRON_BARS,
        summary = "用左侧材料生产选定的唤灵。出征会立刻放出，休整则把刷怪蛋留在右侧。材料不够时不计时。",
        details = listOf(
            LevelNote(
                1,
                "僵尸和骷髅 ${MatchRules.ZOMBIE_PRODUCTION_TICKS / MatchRules.TICKS_PER_SECOND} 秒，铁傀儡 ${MatchRules.GOLEM_PRODUCTION_TICKS / MatchRules.TICKS_PER_SECOND} 秒",
            ),
            LevelNote(2, "产出速度加快 50%，间隔变为原来的 2/3"),
            LevelNote(3, "产出速度加快 100%，间隔变为原来的一半"),
        ),
    )

    private val all = listOf(BASE, REACTOR, WALL, TOWER, BARRACKS)

    fun byId(id: String): BuildingDefinition? = all.firstOrNull { it.id == id }

    fun ofCategory(category: BuildingCategory): List<BuildingDefinition> = all.filter { it.category == category }

    private fun towerNote(index: Int): String {
        val range = MatchRules.TOWER_RANGE[index].toInt()
        val speed = MatchRules.TOWER_SPEED[index]
        val damage = ceil(speed * MatchRules.TOWER_ARROW_BASE_DAMAGE).toInt()
        val seconds = MatchRules.TOWER_INTERVAL[index].toDouble() / MatchRules.TICKS_PER_SECOND
        val interval = if (seconds == seconds.toInt().toDouble()) seconds.toInt().toString() else seconds.toString()
        return "射程 $range，箭速 ${speed.toInt()}，盔甲前伤害 $damage，间隔 $interval 秒"
    }
}
