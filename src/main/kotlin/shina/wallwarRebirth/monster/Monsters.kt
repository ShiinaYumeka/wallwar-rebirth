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
        rank = 1,
        health = MatchRules.MINER_ZOMBIE_HEALTH,
        armor = MatchRules.MINER_ZOMBIE_ARMOR,
        playerAttack = MatchRules.MINER_ZOMBIE_ATTACK,
        buildingDamage = MatchRules.SMALL_BUILDING_DAMAGE,
        buildingIntervalTicks = MatchRules.BUILDING_HIT_TICKS,
        attackRange = MatchRules.BUILDING_ATTACK_RANGE,
        cost = listOf(
            MonsterCost(Items.IRON_INGOT, MatchRules.MINER_ZOMBIE_COST_IRON, "铁锭"),
            MonsterCost(Items.COPPER_INGOT, MatchRules.MINER_ZOMBIE_COST_COPPER, "铜锭"),
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
        buildingDamage = MatchRules.SMALL_BUILDING_DAMAGE,
        buildingIntervalTicks = MatchRules.BUILDING_HIT_TICKS,
        attackRange = MatchRules.BUILDING_ATTACK_RANGE,
        cost = listOf(MonsterCost(Items.REDSTONE, MatchRules.HUSK_COST_REDSTONE, "红石")),
        baseProductionTicks = MatchRules.ZOMBIE_PRODUCTION_TICKS,
        egg = ModItems.EGG_HUSK,
        undead = true,
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
        cost = listOf(MonsterCost(Items.REDSTONE, MatchRules.WARRIOR_COST_REDSTONE, "红石")),
        baseProductionTicks = MatchRules.ZOMBIE_PRODUCTION_TICKS,
        egg = ModItems.EGG_WARRIOR,
        undead = true,
        copperGear = true,
    )

    private val all = listOf(ZOMBIE, SKELETON, GOLEM, SPIDER, MINER, HUSK, DEATH_KNIGHT, CREEPER, WARRIOR)

    fun byId(id: String): MonsterDefinition? = all.firstOrNull { it.id == id }

    fun byEgg(item: Item): MonsterDefinition? = all.firstOrNull { it.egg === item }

    fun selectable(): List<MonsterDefinition> = all.sortedBy { it.rank }
}
