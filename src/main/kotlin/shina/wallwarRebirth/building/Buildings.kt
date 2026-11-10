package shina.wallwarRebirth.building

import net.minecraft.world.item.Items
import shina.wallwarRebirth.WallwarMod
import shina.wallwarRebirth.bootstrap.LegacyDatapacks
import shina.wallwarRebirth.rules.MatchRules
import kotlin.math.ceil
import kotlin.math.floor

object Buildings {
    val BASE = BuildingDefinition(
        id = "base",
        displayName = "基地",
        category = BuildingCategory.CORE,
        maxLevel = 3,
        structure = { level -> WallwarMod.idOf("building/base/level_$level") },
        upgrades = listOf(
            UpgradeCost(
                2,
                listOf(
                    ItemAmount(Items.COPPER_INGOT, MatchRules.BASE_UPGRADE_2_COPPER, "铜锭"),
                    ItemAmount(Items.IRON_INGOT, MatchRules.BASE_UPGRADE_2_IRON, "铁锭"),
                ),
            ),
            UpgradeCost(
                3,
                listOf(
                    ItemAmount(Items.IRON_INGOT, MatchRules.BASE_UPGRADE_3_IRON, "铁锭"),
                    ItemAmount(Items.COPPER_INGOT, MatchRules.BASE_UPGRADE_3_COPPER, "铜锭"),
                ),
            ),
        ),
        production = listOf(
            ProductionRule(
                1,
                MatchRules.BASE_IRON_TICKS,
                listOf(
                    ItemAmount(Items.IRON_INGOT, MatchRules.BASE_IRON_COUNT, "铁锭"),
                    ItemAmount(Items.COPPER_INGOT, MatchRules.BASE_COPPER_COUNT, "铜锭"),
                ),
            ),
            ProductionRule(
                2,
                MatchRules.BASE_REDSTONE_TICKS,
                listOf(
                    ItemAmount(Items.REDSTONE, MatchRules.BASE_REDSTONE_COUNT, "红石"),
                    ItemAmount(Items.LAPIS_LAZULI, MatchRules.BASE_LAPIS_COUNT, "青金石"),
                ),
            ),
            ProductionRule(
                3,
                MatchRules.BASE_DIAMOND_TICKS,
                listOf(ItemAmount(Items.DIAMOND, MatchRules.BASE_DIAMOND_COUNT, "钻石")),
                chancePercent = MatchRules.BASE_DIAMOND_PERCENT,
            ),
        ),
        healthByLevel = List(3) { MatchRules.BASE_HEALTH },
        icon = Items.BEACON,
        summary = "胜负目标，也是商店入口。产出进入本队团队仓库。不能购买、放置或拆除。",
        acceptsModules = true,
        acceptsDiffusion = true,
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
        summary = "一截有碰撞的墙，用来让敌方怪物先打它，而不是直接打到后面的核心。唤灵·蜘蛛不会把它当目标。没有攻击，没有产出。",
        footprint = Footprint.WALL_9,
        acceptsModules = true,
        acceptsDiffusion = true,
        defensive = true,
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
        acceptsModules = true,
        acceptsDiffusion = true,
    )

    val TESLA = BuildingDefinition(
        id = "tesla",
        displayName = "特斯拉塔",
        category = BuildingCategory.NORMAL,
        maxLevel = 3,
        structure = { _ -> LegacyDatapacks.TESLA },
        upgrades = listOf(
            UpgradeCost(2, listOf(ItemAmount(Items.COPPER_INGOT, MatchRules.TESLA_UPGRADE_2_COPPER, "铜锭"))),
            UpgradeCost(3, listOf(ItemAmount(Items.COPPER_INGOT, MatchRules.TESLA_UPGRADE_3_COPPER, "铜锭"))),
        ),
        production = emptyList(),
        healthByLevel = MatchRules.TESLA_HEALTH,
        icon = Items.LIGHTNING_ROD,
        summary = "每 5 秒对射程内最近的敌方玩家造成闪电伤害，并施加虚弱、减速和失明。不打唤灵，不拆建筑。",
        footprint = Footprint.SQUARE_3,
        details = listOf(
            LevelNote(1, teslaNote(0)),
            LevelNote(2, teslaNote(1)),
            LevelNote(3, teslaNote(2)),
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
        summary = "用左侧材料生产选定的唤灵，生产出来立刻放出。材料不够时不计时。",
        details = listOf(
            LevelNote(
                1,
                "僵尸、骷髅和蜘蛛 ${MatchRules.ZOMBIE_PRODUCTION_TICKS / MatchRules.TICKS_PER_SECOND} 秒，尸壳、骷髅死士、苦力怕和僵尸战士同样是 ${MatchRules.ZOMBIE_PRODUCTION_TICKS / MatchRules.TICKS_PER_SECOND} 秒但要 2 级兵营，矿工僵尸 ${MatchRules.MINER_ZOMBIE_PRODUCTION_TICKS / MatchRules.TICKS_PER_SECOND} 秒，铁傀儡 ${MatchRules.GOLEM_PRODUCTION_TICKS / MatchRules.TICKS_PER_SECOND} 秒",
            ),
            LevelNote(2, "产出速度加快 50%，间隔变为原来的 2/3"),
            LevelNote(3, "产出速度加快 100%，间隔变为原来的一半"),
        ),
        acceptsModules = true,
        acceptsDiffusion = true,
    )

    val CANNON = BuildingDefinition(
        id = "cannon",
        displayName = "炮台",
        category = BuildingCategory.NORMAL,
        maxLevel = 3,
        structure = { level -> WallwarMod.idOf("building/cannon/level_$level") },
        upgrades = listOf(
            UpgradeCost(
                2,
                listOf(
                    ItemAmount(Items.COPPER_INGOT, MatchRules.CANNON_UPGRADE_2_COPPER, "铜锭"),
                    ItemAmount(Items.IRON_INGOT, MatchRules.CANNON_UPGRADE_2_IRON, "铁锭"),
                ),
            ),
            UpgradeCost(
                3,
                listOf(
                    ItemAmount(Items.COPPER_INGOT, MatchRules.CANNON_UPGRADE_3_COPPER, "铜锭"),
                    ItemAmount(Items.IRON_INGOT, MatchRules.CANNON_UPGRADE_3_IRON, "铁锭"),
                ),
            ),
        ),
        production = emptyList(),
        healthByLevel = MatchRules.CANNON_HEALTH,
        icon = Items.DISPENSER,
        summary = "朝上的投掷器。发射带重力的 TNT，不消耗背包里的 TNT。先打最近的敌方唤灵，没有再打最近的敌方玩家，两类都没有时打敌方建筑。",
        footprint = Footprint.SQUARE_1,
        details = listOf(
            LevelNote(1, cannonNote(0)),
            LevelNote(2, cannonNote(1)),
            LevelNote(3, cannonNote(2)),
        ),
        acceptsModules = true,
        acceptsDiffusion = true,
    )

    val RELAY = BuildingDefinition(
        id = "relay",
        displayName = "传输装置",
        category = BuildingCategory.NORMAL,
        maxLevel = 1,
        structure = { _ -> LegacyDatapacks.RELAY },
        upgrades = emptyList(),
        production = emptyList(),
        healthByLevel = listOf(MatchRules.RELAY_HEALTH),
        icon = Items.HOPPER,
        summary = "矿工交矿时的更近落点。可以打开基地商店，也可以召集矿工。",
        footprint = Footprint.SQUARE_3,
    )

    private val all = listOf(BASE, WALL, TOWER, TESLA, BARRACKS, CANNON, RELAY)

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

    private fun teslaNote(index: Int): String {
        val range = MatchRules.TESLA_RANGE[index].toInt()
        val weakness = MatchRules.TESLA_WEAKNESS_TICKS / MatchRules.TICKS_PER_SECOND
        val slowness = MatchRules.TESLA_SLOWNESS_TICKS / MatchRules.TICKS_PER_SECOND
        val blindness = MatchRules.TESLA_BLINDNESS_TICKS / MatchRules.TICKS_PER_SECOND
        val seconds = MatchRules.TESLA_INTERVAL / MatchRules.TICKS_PER_SECOND
        return "射程 $range，闪电伤害 ${MatchRules.TESLA_DAMAGE.toInt()}，间隔 $seconds 秒，虚弱 $weakness 秒，减速 I $slowness 秒，失明 $blindness 秒"
    }

    private fun cannonNote(index: Int): String {
        val center = MatchRules.CANNON_CENTER_DAMAGE[index]
        val building = floor(center * MatchRules.CANNON_BUILDING_FRACTION + 0.5).toInt()
        val seconds = MatchRules.CANNON_INTERVAL / MatchRules.TICKS_PER_SECOND
        return "射程 ${MatchRules.CANNON_RANGE.toInt()}，间隔 $seconds 秒，中心伤害 ${center.toInt()}，建筑伤害 $building"
    }
}
