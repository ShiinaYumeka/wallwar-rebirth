package shina.wallwarRebirth.building

import net.minecraft.world.item.Items
import shina.wallwarRebirth.WallwarMod

object Buildings {
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
                400,
                listOf(
                    ItemAmount(Items.REDSTONE_BLOCK, 1, "红石块"),
                    ItemAmount(Items.LAPIS_BLOCK, 1, "青金石块"),
                ),
            ),
            ProductionRule(
                3,
                1200,
                listOf(ItemAmount(Items.DIAMOND, 1, "钻石")),
            ),
        ),
        healthByLevel = listOf(20, 30, 40),
        icon = Items.BLAST_FURNACE,
        summary = "产出进入自己的容器。",
        showsContainer = true,
    )

    val WALL = BuildingDefinition(
        id = "wall",
        displayName = "城墙",
        category = BuildingCategory.NORMAL,
        maxLevel = 3,
        structure = { level -> WallwarMod.idOf("building/wall/level_$level") },
        upgrades = listOf(
            UpgradeCost(2, listOf(ItemAmount(Items.IRON_INGOT, 32, "铁锭"))),
            UpgradeCost(3, listOf(ItemAmount(Items.REDSTONE_BLOCK, 8, "红石块"))),
        ),
        production = emptyList(),
        healthByLevel = listOf(40, 70, 100),
        icon = Items.STONE_BRICKS,
        summary = "一截有碰撞的墙，用来让敌方怪物先打它，而不是直接打到后面的核心。没有攻击，没有产出。",
    )

    val TOWER = BuildingDefinition(
        id = "tower",
        displayName = "箭塔",
        category = BuildingCategory.NORMAL,
        maxLevel = 3,
        structure = { level -> WallwarMod.idOf("building/tower/level_$level") },
        upgrades = listOf(
            UpgradeCost(
                2,
                listOf(
                    ItemAmount(Items.IRON_INGOT, 16, "铁锭"),
                    ItemAmount(Items.REDSTONE_BLOCK, 8, "红石块"),
                ),
            ),
            UpgradeCost(3, listOf(ItemAmount(Items.DIAMOND, 4, "钻石"))),
        ),
        production = emptyList(),
        healthByLevel = listOf(20, 30, 40),
        icon = Items.ARROW,
        summary = "攻击范围内的敌人，优先最近的敌方怪物，没有怪物时打最近的敌方玩家。不打我方，不拆建筑，不消耗箭。",
        details = listOf(
            LevelNote(1, "射程 12，伤害 4，间隔 1 秒"),
            LevelNote(2, "射程 16，伤害 6，间隔 1 秒"),
            LevelNote(3, "射程 20，伤害 8，间隔 0.75 秒"),
        ),
    )

    val BARRACKS = BuildingDefinition(
        id = "barracks",
        displayName = "兵营",
        category = BuildingCategory.NORMAL,
        maxLevel = 3,
        structure = { level -> WallwarMod.idOf("building/barracks/level_$level") },
        upgrades = listOf(
            UpgradeCost(2, listOf(ItemAmount(Items.REDSTONE_BLOCK, 16, "红石块"))),
            UpgradeCost(3, listOf(ItemAmount(Items.DIAMOND, 4, "钻石"))),
        ),
        production = emptyList(),
        healthByLevel = listOf(24, 24, 36),
        icon = Items.IRON_BARS,
        summary = "按间隔在建筑朝向敌方基地的一侧放出一只唤灵·僵尸，队伍与兵营相同。因为怪物没有数量上限，间隔就是它的强度。",
        details = listOf(
            LevelNote(1, "间隔 30 秒"),
            LevelNote(2, "间隔 20 秒"),
            LevelNote(3, "间隔 15 秒"),
        ),
    )

    private val all = listOf(REACTOR, WALL, TOWER, BARRACKS)

    fun byId(id: String): BuildingDefinition? = all.firstOrNull { it.id == id }

    fun ofCategory(category: BuildingCategory): List<BuildingDefinition> = all.filter { it.category == category }
}
