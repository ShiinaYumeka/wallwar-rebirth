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
                1,
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
        summary = "产出基础资源，直接进入本队团队仓库。",
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
        summary = "有碰撞的墙，没有攻击，也没有产出。唤灵·蜘蛛不会把它当目标。",
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
        summary = "每隔 2 秒，向 16 格内的敌人射出箭矢速度为 2/3/4 的箭矢，造成 4/6/8 点伤害",
        footprint = Footprint.SQUARE_1,
        details = listOf(
            LevelNote(1, towerNote(0)),
            LevelNote(2, towerNote(1)),
            LevelNote(3, towerNote(2)),
        ),
        acceptsModules = true,
        acceptsDiffusion = true,
        defensive = true,
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
        summary = "每隔 5/4/3 秒，电击 16 格内没有蓄风的敌人，造成 6/10/14 点闪电伤害，并给予10秒蓄风（玩家 5 秒）。命中还会施加失明 1 秒、缓慢 V 1 秒、挖掘疲劳 I 10 秒、虚弱 I 5 秒。不拆建筑。",
        footprint = Footprint.SQUARE_3,
        details = listOf(
            LevelNote(1, teslaNote(0)),
            LevelNote(2, teslaNote(1)),
            LevelNote(3, teslaNote(2)),
        ),
        acceptsModules = true,
        acceptsDiffusion = true,
        defensive = true,
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
        summary = "用材料生产唤灵，做好就放出。材料不够时不计时。2 级产出间隔变为原来的 2/3，3 级变为原来的一半。1 级可生产僵尸、蜘蛛（15 秒）和骷髅（30 秒）。2 级解锁尸壳、苦力怕、僵尸战士（15 秒）、骷髅死士（15 秒，一轮 3 只）、矿工僵尸（60 秒）、村民（30 秒）。3 级解锁高压苦力怕、铁傀儡（30 秒）。",
        details = listOf(
            LevelNote(
                1,
                "僵尸和蜘蛛 ${MatchRules.ZOMBIE_PRODUCTION_TICKS / MatchRules.TICKS_PER_SECOND} 秒，骷髅 ${MatchRules.SKELETON_PRODUCTION_TICKS / MatchRules.TICKS_PER_SECOND} 秒，尸壳、骷髅死士、苦力怕和僵尸战士同样是 ${MatchRules.ZOMBIE_PRODUCTION_TICKS / MatchRules.TICKS_PER_SECOND} 秒但要 2 级兵营，高压苦力怕 ${MatchRules.CHARGED_CREEPER_PRODUCTION_TICKS / MatchRules.TICKS_PER_SECOND} 秒但要 3 级兵营，矿工僵尸 ${MatchRules.MINER_ZOMBIE_PRODUCTION_TICKS / MatchRules.TICKS_PER_SECOND} 秒但要 2 级兵营，铁傀儡 ${MatchRules.GOLEM_PRODUCTION_TICKS / MatchRules.TICKS_PER_SECOND} 秒，村民 ${MatchRules.VILLAGER_PRODUCTION_TICKS / MatchRules.TICKS_PER_SECOND} 秒但要 2 级兵营",
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
            UpgradeCost(2, listOf(ItemAmount(Items.COPPER_INGOT, MatchRules.CANNON_UPGRADE_2_COPPER, "铜锭"))),
            UpgradeCost(3, listOf(ItemAmount(Items.COPPER_INGOT, MatchRules.CANNON_UPGRADE_3_COPPER, "铜锭"))),
        ),
        production = emptyList(),
        healthByLevel = MatchRules.CANNON_HEALTH,
        icon = Items.DISPENSER,
        summary = "每隔 10 秒，向 32 格内发射带重力的 TNT，不消耗背包里的 TNT。中心伤害 20/30/40，建筑伤害 5/8/10。普通炮弹不拆方块。",
        footprint = Footprint.SQUARE_1,
        details = listOf(
            LevelNote(1, cannonNote(0)),
            LevelNote(2, cannonNote(1)),
            LevelNote(3, cannonNote(2)),
        ),
        acceptsModules = true,
        acceptsDiffusion = true,
        defensive = true,
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
        summary = "可以打开基地商店，也可以召集最近的我方矿工。矿工赶到后交掉身上的矿，再去挖。放置后在旁边额外生成一只本队矿工僵尸。不能升级。",
        footprint = Footprint.SQUARE_3,
    )

    val FARMLAND = BuildingDefinition(
        id = "farmland",
        displayName = "农田",
        category = BuildingCategory.NORMAL,
        maxLevel = 1,
        structure = { _ -> WallwarMod.idOf("building/farmland/level_1") },
        upgrades = emptyList(),
        production = emptyList(),
        healthByLevel = listOf(MatchRules.FARMLAND_HEALTH),
        icon = Items.COMPOSTER,
        summary = "农田没人干活时，村民也会自己走过去转职。农民最多带 20 个小麦，满了先去交。小麦约每 20 秒成熟，收割间隔 3 秒。不能升级。",
        realWorldParts = true,
    )

    val WORKSHOP = BuildingDefinition(
        id = "workshop",
        displayName = "晶核工坊",
        category = BuildingCategory.NORMAL,
        maxLevel = 1,
        structure = { _ -> WallwarMod.idOf("building/workshop/level_1") },
        upgrades = emptyList(),
        production = emptyList(),
        healthByLevel = listOf(MatchRules.WORKSHOP_HEALTH),
        icon = Items.BLAST_FURNACE,
        summary = "本队有已放下的晶核工坊时，可购买晶化反应炉、晶核制导炮、传送尖塔、晶震装置，并开始制作唤灵·重甲尸壳。同时把基地产出钻石的基础概率从 10% 提升到 12%。每队不限制座数。不能升级。占地 9×9。",
        footprint = Footprint.SQUARE_9,
        coverBelowLayers = 2,
        realWorldParts = true,
    )

    val CRYSTAL_FURNACE = BuildingDefinition(
        id = "crystal_furnace",
        displayName = "晶化反应炉",
        category = BuildingCategory.NORMAL,
        maxLevel = 3,
        structure = { level -> WallwarMod.idOf("building/crystal_furnace/level_$level") },
        upgrades = listOf(
            UpgradeCost(
                2,
                listOf(
                    ItemAmount(Items.IRON_INGOT, MatchRules.CRYSTAL_FURNACE_UPGRADE_IRON, "铁锭"),
                    ItemAmount(Items.COPPER_INGOT, MatchRules.CRYSTAL_FURNACE_UPGRADE_COPPER, "铜锭"),
                ),
            ),
            UpgradeCost(
                3,
                listOf(
                    ItemAmount(Items.IRON_INGOT, MatchRules.CRYSTAL_FURNACE_UPGRADE_IRON, "铁锭"),
                    ItemAmount(Items.COPPER_INGOT, MatchRules.CRYSTAL_FURNACE_UPGRADE_COPPER, "铜锭"),
                    ItemAmount(Items.DIAMOND, MatchRules.CRYSTAL_FURNACE_UPGRADE_DIAMOND, "钻石"),
                ),
            ),
        ),
        production = emptyList(),
        healthByLevel = List(3) { MatchRules.CRYSTAL_FURNACE_HEALTH },
        icon = Items.FURNACE,
        summary = "开启后自动从本队团队仓库扣料，铜锭和铁锭各 10 个一批，每 20 秒转化一次。成功得到 1 个钻石；失败时一半得到 4 个红石，一半得到 4 个青金石。产物进入团队仓库，放不下就作废。关闭后不再开始新批次，正在转化的那批会做完。成功率 20/30/40%。每队不限制座数。",
        details = listOf(
            LevelNote(1, furnaceNote(0)),
            LevelNote(2, furnaceNote(1)),
            LevelNote(3, furnaceNote(2)),
        ),
        coverBelowLayers = 3,
        requiresWorkshop = true,
        realWorldParts = true,
    )

    val GUIDED_CANNON = BuildingDefinition(
        id = "guided_cannon",
        displayName = "晶核制导炮",
        category = BuildingCategory.SUPERWEAPON,
        maxLevel = 3,
        structure = { level -> WallwarMod.idOf("building/guided_cannon/level_$level") },
        upgrades = listOf(
            UpgradeCost(
                2,
                listOf(
                    ItemAmount(Items.COPPER_INGOT, MatchRules.GUIDED_CANNON_UPGRADE_COPPER, "铜锭"),
                    ItemAmount(Items.IRON_INGOT, MatchRules.GUIDED_CANNON_UPGRADE_IRON, "铁锭"),
                    ItemAmount(Items.DIAMOND, MatchRules.GUIDED_CANNON_UPGRADE_DIAMOND, "钻石"),
                ),
            ),
            UpgradeCost(
                3,
                listOf(
                    ItemAmount(Items.COPPER_INGOT, MatchRules.GUIDED_CANNON_UPGRADE_COPPER, "铜锭"),
                    ItemAmount(Items.IRON_INGOT, MatchRules.GUIDED_CANNON_UPGRADE_IRON, "铁锭"),
                    ItemAmount(Items.DIAMOND, MatchRules.GUIDED_CANNON_UPGRADE_DIAMOND, "钻石"),
                ),
            ),
        ),
        production = emptyList(),
        healthByLevel = List(3) { MatchRules.GUIDED_CANNON_HEALTH },
        icon = Items.TNT,
        summary = "每队同时只能有一座。中心伤害 120，威力半径 16 格，对建筑为 25%（30 点）。不伤友方、无队伍和自身。会拆威力半径内的非建筑木板并掉落，不拆建筑方块，不引燃。冷却 600/500/400 秒。占地 9×9。冷却就绪后在面板开炮，从天上选定地表，左键选定后再按左键确认。潜行取消，不进入冷却。",
        footprint = Footprint.SQUARE_9,
        details = listOf(
            LevelNote(1, guidedNote(0)),
            LevelNote(2, guidedNote(1)),
            LevelNote(3, guidedNote(2)),
        ),
        coverBelowLayers = 3,
        requiresWorkshop = true,
        teamUnique = true,
        realWorldParts = true,
    )

    val TELEPORT_SPIRE = BuildingDefinition(
        id = "teleport_spire",
        displayName = "传送尖塔",
        category = BuildingCategory.SUPERWEAPON,
        maxLevel = 3,
        structure = { level -> WallwarMod.idOf("building/teleport_spire/level_$level") },
        upgrades = listOf(
            UpgradeCost(
                2,
                listOf(
                    ItemAmount(Items.IRON_INGOT, MatchRules.TELEPORT_SPIRE_UPGRADE_IRON, "铁锭"),
                    ItemAmount(Items.COPPER_INGOT, MatchRules.TELEPORT_SPIRE_UPGRADE_COPPER, "铜锭"),
                    ItemAmount(Items.DIAMOND, MatchRules.TELEPORT_SPIRE_UPGRADE_DIAMOND, "钻石"),
                ),
            ),
            UpgradeCost(
                3,
                listOf(
                    ItemAmount(Items.IRON_INGOT, MatchRules.TELEPORT_SPIRE_UPGRADE_IRON, "铁锭"),
                    ItemAmount(Items.COPPER_INGOT, MatchRules.TELEPORT_SPIRE_UPGRADE_COPPER, "铜锭"),
                    ItemAmount(Items.DIAMOND, MatchRules.TELEPORT_SPIRE_UPGRADE_DIAMOND, "钻石"),
                ),
            ),
        ),
        production = emptyList(),
        healthByLevel = List(3) { MatchRules.TELEPORT_SPIRE_HEALTH },
        icon = Items.AMETHYST_SHARD,
        summary = "每队同时只能有一座。挤不下的唤灵不传，留在原地。冷却 480/400/320 秒。占地 9×9。冷却就绪后在面板传送，选定方式与晶核制导炮相同。确认后玩家传到安全落点，正在跟随的同队唤灵一起传。",
        footprint = Footprint.SQUARE_9,
        details = listOf(
            LevelNote(1, spireNote(0)),
            LevelNote(2, spireNote(1)),
            LevelNote(3, spireNote(2)),
        ),
        coverBelowLayers = 3,
        requiresWorkshop = true,
        teamUnique = true,
        realWorldParts = true,
    )

    val QUAKE = BuildingDefinition(
        id = "quake",
        displayName = "晶震装置",
        category = BuildingCategory.NORMAL,
        maxLevel = 3,
        structure = { level -> WallwarMod.idOf("building/quake/level_$level") },
        upgrades = listOf(
            UpgradeCost(2, listOf(ItemAmount(Items.DIAMOND, MatchRules.QUAKE_UPGRADE_DIAMOND, "钻石"))),
            UpgradeCost(3, listOf(ItemAmount(Items.DIAMOND, MatchRules.QUAKE_UPGRADE_DIAMOND, "钻石"))),
        ),
        production = emptyList(),
        healthByLevel = List(3) { MatchRules.QUAKE_HEALTH },
        icon = Items.LODESTONE,
        summary = "每隔 3/2/1 秒，在 24 格内对目标周围溅射 3 格的敌方造成 5 点伤害。只打敌方玩家和唤灵，不打建筑。被命中后易伤每次 +1，最多 30 层，由本队所有晶震装置共用。占地 5×5。",
        footprint = Footprint.SQUARE_5,
        details = listOf(
            LevelNote(1, quakeNote(0)),
            LevelNote(2, quakeNote(1)),
            LevelNote(3, quakeNote(2)),
        ),
        acceptsModules = true,
        acceptsDiffusion = true,
        defensive = true,
        coverBelowLayers = 1,
        requiresWorkshop = true,
        realWorldParts = true,
    )

    val PILLAGER = BuildingDefinition(
        id = "pillager",
        displayName = "掠夺者哨塔",
        category = BuildingCategory.NORMAL,
        maxLevel = 3,
        structure = { level -> WallwarMod.idOf("building/pillager/level_$level") },
        upgrades = listOf(
            UpgradeCost(2, listOf(ItemAmount(Items.IRON_INGOT, MatchRules.PILLAGER_TOWER_UPGRADE_2_IRON, "铁锭"))),
            UpgradeCost(
                3,
                listOf(
                    ItemAmount(Items.IRON_INGOT, MatchRules.PILLAGER_TOWER_UPGRADE_3_IRON, "铁锭"),
                    ItemAmount(Items.DIAMOND, MatchRules.PILLAGER_TOWER_UPGRADE_3_DIAMOND, "钻石"),
                ),
            ),
        ),
        production = emptyList(),
        healthByLevel = MatchRules.PILLAGER_TOWER_HEALTH,
        icon = Items.WHITE_BANNER,
        summary = "生产规则与兵营相同。商店不出售这些唤灵。2 级产出间隔变为原来的 2/3，3 级变为原来的一半。1 级可生产掠夺者（15 秒）。2 级解锁卫道士、幻术师（15 秒）。3 级解锁唤魔者、劫掠兽（30 秒）。",
        details = listOf(
            LevelNote(
                1,
                "掠夺者 ${MatchRules.PILLAGER_PRODUCTION_TICKS / MatchRules.TICKS_PER_SECOND} 秒，卫道士和幻术师 ${MatchRules.ILLUSIONER_PRODUCTION_TICKS / MatchRules.TICKS_PER_SECOND} 秒且要 2 级，唤魔者和劫掠兽 ${MatchRules.EVOKER_PRODUCTION_TICKS / MatchRules.TICKS_PER_SECOND} 秒且要 3 级",
            ),
            LevelNote(2, "产出速度加快 50%，间隔变为原来的 2/3"),
            LevelNote(3, "产出速度加快 100%，间隔变为原来的一半"),
        ),
        acceptsModules = true,
        acceptsDiffusion = true,
    )

    val MINERAL_REACTOR = BuildingDefinition(
        id = "mineral_reactor",
        displayName = "矿物反应堆",
        category = BuildingCategory.NORMAL,
        maxLevel = 3,
        structure = { _ -> WallwarMod.idOf("building/mineral_reactor/level_3") },
        upgrades = emptyList(),
        production = BASE.production,
        healthByLevel = List(3) { MatchRules.MINERAL_REACTOR_HEALTH },
        icon = Items.FURNACE,
        summary = "开局出现在两侧角矿靠边界一侧。固定 3 级，不能升级，开局无主。点击占领后，最近的本队无职业村民会寻路过来，到达后村民消失并完成占领。可被再占领。打掉后本局消失。产出与 3 级基地相同，进入本队团队仓库。不能开商店。占地 5×5。",
        footprint = Footprint.SQUARE_5,
        acceptsModules = true,
        acceptsDiffusion = false,
        coverBelowLayers = 2,
        realWorldParts = true,
        neutral = true,
    )

    private val all = listOf(BASE, WALL, TOWER, TESLA, BARRACKS, CANNON, RELAY, PILLAGER, FARMLAND, WORKSHOP, CRYSTAL_FURNACE, GUIDED_CANNON, TELEPORT_SPIRE, QUAKE, MINERAL_REACTOR)

    fun byId(id: String): BuildingDefinition? = all.firstOrNull { it.id == id }

    fun neutral(id: String): Boolean = byId(id)?.neutral == true

    fun isCombatDefense(id: String): Boolean =
        id == TOWER.id || id == TESLA.id || id == CANNON.id || id == QUAKE.id

    private fun towerNote(index: Int): String {
        val range = MatchRules.TOWER_RANGE[index].toInt()
        val speed = MatchRules.TOWER_SPEED[index]
        val damage = ceil(speed * MatchRules.TOWER_ARROW_BASE_DAMAGE).toInt()
        val seconds = MatchRules.TOWER_INTERVAL[index].toDouble() / MatchRules.TICKS_PER_SECOND
        val interval = if (seconds == seconds.toInt().toDouble()) seconds.toInt().toString() else seconds.toString()
        return "射程 $range，箭速 ${speed.toInt()}，盔甲前伤害 $damage，间隔 $interval 秒"
    }

    private fun teslaNote(index: Int): String {
        val wind = MatchRules.secondsText(MatchRules.TESLA_WIND_TICKS)
        val playerWind = MatchRules.secondsText(MatchRules.TESLA_WIND_TICKS / 2)
        val seconds = MatchRules.TESLA_INTERVAL[index] / MatchRules.TICKS_PER_SECOND
        return "射程 ${MatchRules.TESLA_RANGE.toInt()}，闪电伤害 ${MatchRules.TESLA_DAMAGE[index].toInt()}，间隔 $seconds 秒，蓄风 $wind 秒，玩家 $playerWind 秒"
    }

    private fun guidedNote(index: Int): String {
        val seconds = MatchRules.GUIDED_CANNON_COOLDOWN[index] / MatchRules.TICKS_PER_SECOND
        return "冷却 $seconds 秒，中心伤害 ${MatchRules.GUIDED_CANNON_CENTER_DAMAGE.toInt()}，建筑伤害 ${floor(MatchRules.GUIDED_CANNON_CENTER_DAMAGE * MatchRules.CANNON_BUILDING_FRACTION + 0.5).toInt()}"
    }

    private fun spireNote(index: Int): String {
        val seconds = MatchRules.TELEPORT_SPIRE_COOLDOWN[index] / MatchRules.TICKS_PER_SECOND
        return "冷却 $seconds 秒"
    }

    private fun furnaceNote(index: Int): String {
        val rate = MatchRules.CRYSTAL_FURNACE_SUCCESS_RATE[index]
        val seconds = MatchRules.CRYSTAL_FURNACE_CONVERT_TICKS / MatchRules.TICKS_PER_SECOND
        return "成功率 $rate%，转化 $seconds 秒"
    }

    private fun quakeNote(index: Int): String {
        val seconds = MatchRules.QUAKE_INTERVAL[index] / MatchRules.TICKS_PER_SECOND
        return "射程 ${MatchRules.QUAKE_RANGE.toInt()}，间隔 $seconds 秒，基础伤害 ${MatchRules.QUAKE_DAMAGE}，溅射 ${MatchRules.QUAKE_SPLASH}"
    }

    private fun cannonNote(index: Int): String {
        val center = MatchRules.CANNON_CENTER_DAMAGE[index]
        val building = floor(center * MatchRules.CANNON_BUILDING_FRACTION + 0.5).toInt()
        val seconds = MatchRules.CANNON_INTERVAL / MatchRules.TICKS_PER_SECOND
        return "射程 ${MatchRules.CANNON_RANGE.toInt()}，间隔 $seconds 秒，中心伤害 ${center.toInt()}，建筑伤害 $building"
    }
}
