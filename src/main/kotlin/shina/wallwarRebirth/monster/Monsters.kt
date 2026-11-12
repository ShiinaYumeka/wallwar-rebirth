package shina.wallwarRebirth.monster

import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import shina.wallwarRebirth.item.ModItems
import shina.wallwarRebirth.rules.MatchRules

object Monsters {
    val ZOMBIE = MonsterDefinition(
        id = "zombie",
        displayName = "唤灵·僵尸",
        rank = 1,
        health = MatchRules.ZOMBIE_HEALTH,
        armor = MatchRules.ZOMBIE_ARMOR,
        playerAttack = MatchRules.ZOMBIE_ATTACK,
        buildingDamage = MatchRules.SMALL_BUILDING_DAMAGE,
        buildingIntervalTicks = MatchRules.BUILDING_HIT_TICKS,
        attackRange = MatchRules.BUILDING_ATTACK_RANGE,
        cost = listOf(MonsterCost(Items.REDSTONE, MatchRules.ZOMBIE_COST_REDSTONE, "红石")),
        baseProductionTicks = MatchRules.ZOMBIE_PRODUCTION_TICKS,
        egg = ModItems.EGG_ZOMBIE,
        undead = true,
    )

    val SKELETON = MonsterDefinition(
        id = "skeleton",
        displayName = "唤灵·骷髅",
        rank = 1,
        health = MatchRules.SKELETON_HEALTH,
        armor = MatchRules.SKELETON_ARMOR,
        playerAttack = MatchRules.SKELETON_ATTACK,
        buildingDamage = MatchRules.SMALL_BUILDING_DAMAGE,
        buildingIntervalTicks = MatchRules.BUILDING_HIT_TICKS,
        attackRange = MatchRules.BUILDING_ATTACK_RANGE,
        cost = listOf(MonsterCost(Items.REDSTONE, MatchRules.SKELETON_COST_REDSTONE, "红石")),
        baseProductionTicks = MatchRules.SKELETON_PRODUCTION_TICKS,
        egg = ModItems.EGG_SKELETON,
        bowRange = MonsterRules.SKELETON_BOW_RANGE,
        undead = true,
    )

    val GOLEM = MonsterDefinition(
        id = "golem",
        displayName = "唤灵·铁傀儡",
        rank = 3,
        health = MatchRules.GOLEM_HEALTH,
        armor = MatchRules.GOLEM_ARMOR,
        playerAttack = MatchRules.GOLEM_ATTACK,
        buildingDamage = MatchRules.GOLEM_BUILDING_DAMAGE,
        buildingIntervalTicks = MatchRules.BUILDING_HIT_TICKS,
        attackRange = MatchRules.BUILDING_ATTACK_RANGE,
        cost = listOf(
            MonsterCost(Items.REDSTONE, MatchRules.GOLEM_COST_REDSTONE, "红石"),
            MonsterCost(Items.IRON_INGOT, MatchRules.GOLEM_COST_IRON, "铁锭"),
        ),
        baseProductionTicks = MatchRules.GOLEM_PRODUCTION_TICKS,
        egg = ModItems.EGG_GOLEM,
    )

    val SPIDER = MonsterDefinition(
        id = "spider",
        displayName = "唤灵·蜘蛛",
        rank = 1,
        health = MatchRules.SPIDER_HEALTH,
        armor = MatchRules.SPIDER_ARMOR,
        playerAttack = MatchRules.SPIDER_ATTACK,
        buildingDamage = MatchRules.SPIDER_BUILDING_DAMAGE,
        buildingIntervalTicks = MatchRules.BUILDING_HIT_TICKS,
        attackRange = MatchRules.BUILDING_ATTACK_RANGE,
        cost = listOf(MonsterCost(Items.REDSTONE, MatchRules.SPIDER_COST_REDSTONE, "红石")),
        baseProductionTicks = MatchRules.SPIDER_PRODUCTION_TICKS,
        egg = ModItems.EGG_SPIDER,
        ignoresDefensive = true,
    )

    val MINER = MonsterDefinition(
        id = "miner_zombie",
        displayName = "矿工僵尸",
        rank = 2,
        health = MatchRules.MINER_ZOMBIE_HEALTH,
        armor = MatchRules.MINER_ZOMBIE_ARMOR,
        playerAttack = MatchRules.MINER_ZOMBIE_ATTACK,
        buildingDamage = MatchRules.SMALL_BUILDING_DAMAGE,
        buildingIntervalTicks = MatchRules.BUILDING_HIT_TICKS,
        attackRange = MatchRules.BUILDING_ATTACK_RANGE,
        cost = listOf(
            MonsterCost(Items.IRON_INGOT, MatchRules.MINER_ZOMBIE_COST_IRON, "铁锭"),
            MonsterCost(Items.COPPER_INGOT, MatchRules.MINER_ZOMBIE_COST_COPPER, "铜锭"),
            MonsterCost(Items.REDSTONE, MatchRules.MINER_ZOMBIE_COST_REDSTONE, "红石"),
        ),
        baseProductionTicks = MatchRules.MINER_ZOMBIE_PRODUCTION_TICKS,
        egg = ModItems.EGG_MINER,
        undead = true,
        miner = true,
        wornArmor = true,
    )

    val HUSK = MonsterDefinition(
        id = "husk",
        displayName = "唤灵·尸壳",
        rank = 2,
        health = MatchRules.HUSK_HEALTH,
        armor = MatchRules.ZOMBIE_ARMOR,
        playerAttack = MatchRules.ZOMBIE_ATTACK,
        buildingDamage = MatchRules.HUSK_BUILDING_DAMAGE,
        buildingIntervalTicks = MatchRules.BUILDING_HIT_TICKS,
        attackRange = MatchRules.BUILDING_ATTACK_RANGE,
        cost = listOf(MonsterCost(Items.REDSTONE, MatchRules.HUSK_COST_REDSTONE, "红石")),
        baseProductionTicks = MatchRules.ZOMBIE_PRODUCTION_TICKS,
        egg = ModItems.EGG_HUSK,
        undead = true,
        buildingsOnly = true,
    )

    val DEATH_KNIGHT = MonsterDefinition(
        id = "death_knight",
        displayName = "唤灵·骷髅死士",
        rank = 2,
        health = MatchRules.DEATH_KNIGHT_HEALTH,
        armor = MatchRules.SKELETON_ARMOR,
        playerAttack = MatchRules.DEATH_KNIGHT_ATTACK,
        buildingDamage = MatchRules.SMALL_BUILDING_DAMAGE,
        buildingIntervalTicks = MatchRules.BUILDING_HIT_TICKS,
        attackRange = MatchRules.BUILDING_ATTACK_RANGE,
        cost = listOf(MonsterCost(Items.REDSTONE, MatchRules.DEATH_KNIGHT_COST_REDSTONE, "红石")),
        baseProductionTicks = MatchRules.DEATH_KNIGHT_PRODUCTION_TICKS,
        egg = ModItems.EGG_DEATH_KNIGHT,
        undead = true,
        sword = true,
        batch = MatchRules.DEATH_KNIGHT_BATCH,
    )

    val CREEPER = MonsterDefinition(
        id = "creeper",
        displayName = "唤灵·苦力怕",
        rank = 2,
        health = MatchRules.CREEPER_HEALTH,
        armor = MatchRules.CREEPER_ARMOR,
        playerAttack = MatchRules.CREEPER_ATTACK,
        buildingDamage = MatchRules.CREEPER_BUILDING_DAMAGE,
        buildingIntervalTicks = MatchRules.BUILDING_HIT_TICKS,
        attackRange = MatchRules.CREEPER_ATTACK_RANGE,
        cost = listOf(MonsterCost(Items.REDSTONE, MatchRules.CREEPER_COST_REDSTONE, "红石")),
        baseProductionTicks = MatchRules.ZOMBIE_PRODUCTION_TICKS,
        egg = ModItems.EGG_CREEPER,
        explodes = true,
    )

    val WARRIOR = MonsterDefinition(
        id = "zombie_warrior",
        displayName = "唤灵·僵尸战士",
        rank = 2,
        health = MatchRules.WARRIOR_HEALTH,
        armor = MatchRules.WARRIOR_ARMOR,
        playerAttack = MatchRules.WARRIOR_ATTACK,
        buildingDamage = MatchRules.SMALL_BUILDING_DAMAGE,
        buildingIntervalTicks = MatchRules.BUILDING_HIT_TICKS,
        attackRange = MatchRules.BUILDING_ATTACK_RANGE,
        cost = listOf(
            MonsterCost(Items.COPPER_INGOT, MatchRules.WARRIOR_COST_COPPER, "铜锭"),
            MonsterCost(Items.REDSTONE, MatchRules.WARRIOR_COST_REDSTONE, "红石"),
        ),
        baseProductionTicks = MatchRules.ZOMBIE_PRODUCTION_TICKS,
        egg = ModItems.EGG_WARRIOR,
        undead = true,
        copperGear = true,
    )

    val PILLAGER = MonsterDefinition(
        id = "pillager",
        displayName = "唤灵·掠夺者",
        rank = 1,
        health = MatchRules.PILLAGER_HEALTH,
        armor = MatchRules.PILLAGER_ARMOR,
        playerAttack = MatchRules.PILLAGER_ATTACK,
        buildingDamage = MatchRules.SMALL_BUILDING_DAMAGE,
        buildingIntervalTicks = MatchRules.BUILDING_HIT_TICKS,
        attackRange = MatchRules.BUILDING_ATTACK_RANGE,
        cost = listOf(
            MonsterCost(Items.REDSTONE, MatchRules.PILLAGER_COST_REDSTONE, "红石"),
            MonsterCost(Items.WHEAT, MatchRules.PILLAGER_COST_WHEAT, "小麦"),
        ),
        baseProductionTicks = MatchRules.PILLAGER_PRODUCTION_TICKS,
        egg = ModItems.EGG_PILLAGER,
        bowRange = MonsterRules.SKELETON_BOW_RANGE,
        source = MonsterSource.TOWER,
        usesCrossbow = true,
    )

    val VINDICATOR = MonsterDefinition(
        id = "vindicator",
        displayName = "唤灵·卫道士",
        rank = 1,
        health = MatchRules.VINDICATOR_HEALTH,
        armor = MatchRules.PILLAGER_ARMOR,
        playerAttack = MatchRules.VINDICATOR_ATTACK,
        buildingDamage = MatchRules.SMALL_BUILDING_DAMAGE,
        buildingIntervalTicks = MatchRules.BUILDING_HIT_TICKS,
        attackRange = MatchRules.BUILDING_ATTACK_RANGE,
        cost = listOf(
            MonsterCost(Items.REDSTONE, MatchRules.VINDICATOR_COST_REDSTONE, "红石"),
            MonsterCost(Items.WHEAT, MatchRules.VINDICATOR_COST_WHEAT, "小麦"),
        ),
        baseProductionTicks = MatchRules.VINDICATOR_PRODUCTION_TICKS,
        egg = ModItems.EGG_VINDICATOR,
        source = MonsterSource.TOWER,
        axe = true,
    )

    val EVOKER = MonsterDefinition(
        id = "evoker",
        displayName = "唤灵·幻魔者",
        rank = 2,
        health = MatchRules.EVOKER_HEALTH,
        armor = MatchRules.PILLAGER_ARMOR,
        playerAttack = MatchRules.EVOKER_ATTACK,
        buildingDamage = MatchRules.SMALL_BUILDING_DAMAGE,
        buildingIntervalTicks = MatchRules.BUILDING_HIT_TICKS,
        attackRange = MatchRules.BUILDING_ATTACK_RANGE,
        cost = listOf(
            MonsterCost(Items.REDSTONE, MatchRules.EVOKER_COST_REDSTONE, "红石"),
            MonsterCost(Items.WHEAT, MatchRules.EVOKER_COST_WHEAT, "小麦"),
        ),
        baseProductionTicks = MatchRules.EVOKER_PRODUCTION_TICKS,
        egg = ModItems.EGG_EVOKER,
        source = MonsterSource.TOWER,
        fangs = true,
    )

    val ILLUSIONER = MonsterDefinition(
        id = "illusioner",
        displayName = "唤灵·幻术师",
        rank = 2,
        health = MatchRules.ILLUSIONER_HEALTH,
        armor = MatchRules.PILLAGER_ARMOR,
        playerAttack = MatchRules.ILLUSIONER_ATTACK,
        buildingDamage = MatchRules.SMALL_BUILDING_DAMAGE,
        buildingIntervalTicks = MatchRules.BUILDING_HIT_TICKS,
        attackRange = MatchRules.BUILDING_ATTACK_RANGE,
        cost = listOf(
            MonsterCost(Items.REDSTONE, MatchRules.ILLUSIONER_COST_REDSTONE, "红石"),
            MonsterCost(Items.WHEAT, MatchRules.ILLUSIONER_COST_WHEAT, "小麦"),
        ),
        baseProductionTicks = MatchRules.ILLUSIONER_PRODUCTION_TICKS,
        egg = ModItems.EGG_ILLUSIONER,
        bowRange = MonsterRules.SKELETON_BOW_RANGE,
        source = MonsterSource.TOWER,
    )

    val RAVAGER = MonsterDefinition(
        id = "ravager",
        displayName = "唤灵·劫掠兽",
        rank = 3,
        health = MatchRules.RAVAGER_HEALTH,
        armor = MatchRules.PILLAGER_ARMOR,
        playerAttack = MatchRules.RAVAGER_ATTACK,
        buildingDamage = MatchRules.RAVAGER_BUILDING_DAMAGE,
        buildingIntervalTicks = MatchRules.BUILDING_HIT_TICKS,
        attackRange = MatchRules.BUILDING_ATTACK_RANGE,
        cost = listOf(
            MonsterCost(Items.REDSTONE, MatchRules.RAVAGER_COST_REDSTONE, "红石"),
            MonsterCost(Items.WHEAT, MatchRules.RAVAGER_COST_WHEAT, "小麦"),
            MonsterCost(Items.DIAMOND, MatchRules.RAVAGER_COST_DIAMOND, "钻石"),
        ),
        baseProductionTicks = MatchRules.RAVAGER_PRODUCTION_TICKS,
        egg = ModItems.EGG_RAVAGER,
        source = MonsterSource.TOWER,
        buildingsOnly = true,
    )

    val VILLAGER = MonsterDefinition(
        id = "villager",
        displayName = "唤灵·村民",
        rank = 2,
        health = MatchRules.VILLAGER_HEALTH,
        armor = 0.0,
        playerAttack = 0.0,
        buildingDamage = 0,
        buildingIntervalTicks = MatchRules.BUILDING_HIT_TICKS,
        attackRange = MatchRules.FARMER_WORK_RANGE,
        cost = listOf(MonsterCost(Items.DIAMOND, MatchRules.VILLAGER_COST_DIAMOND, "钻石")),
        baseProductionTicks = MatchRules.VILLAGER_PRODUCTION_TICKS,
        egg = ModItems.EGG_VILLAGER,
        civilian = true,
    )

    private val all = listOf(
        ZOMBIE, SKELETON, GOLEM, SPIDER, MINER, HUSK, DEATH_KNIGHT, CREEPER, WARRIOR,
        PILLAGER, VINDICATOR, EVOKER, ILLUSIONER, RAVAGER, VILLAGER,
    )

    fun byId(id: String): MonsterDefinition? = all.firstOrNull { it.id == id }

    fun byEgg(item: Item): MonsterDefinition? = all.firstOrNull { it.egg === item }

    fun selectable(): List<MonsterDefinition> = selectable(MonsterSource.BARRACKS)

    fun selectable(source: MonsterSource): List<MonsterDefinition> =
        all.filter { it.source == source }.sortedWith(compareBy({ !it.productionReady }, { it.rank }))
}
