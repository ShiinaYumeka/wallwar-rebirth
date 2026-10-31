package shina.wallwarRebirth.monster

import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import shina.wallwarRebirth.item.ModItems
import shina.wallwarRebirth.match.MatchRules

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
        dropItem = Items.REDSTONE,
        dropCount = MatchRules.ZOMBIE_DROP,
        dropName = "红石",
        cost = listOf(MonsterCost(Items.REDSTONE_BLOCK, MatchRules.ZOMBIE_COST_REDSTONE, "红石块")),
        baseProductionTicks = MatchRules.ZOMBIE_PRODUCTION_TICKS,
        egg = ModItems.EGG_ZOMBIE,
        undead = true,
    )

    val SKELETON = MonsterDefinition(
        id = "skeleton",
        displayName = "唤灵·骷髅",
        rank = 2,
        health = MatchRules.SKELETON_HEALTH,
        armor = MatchRules.SKELETON_ARMOR,
        playerAttack = MatchRules.SKELETON_ATTACK,
        buildingDamage = MatchRules.SMALL_BUILDING_DAMAGE,
        buildingIntervalTicks = MatchRules.BUILDING_HIT_TICKS,
        attackRange = MatchRules.BUILDING_ATTACK_RANGE,
        dropItem = Items.REDSTONE,
        dropCount = MatchRules.SKELETON_DROP,
        dropName = "红石",
        cost = listOf(MonsterCost(Items.REDSTONE_BLOCK, MatchRules.SKELETON_COST_REDSTONE, "红石块")),
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
        dropItem = Items.REDSTONE_BLOCK,
        dropCount = MatchRules.GOLEM_DROP,
        dropName = "红石块",
        cost = listOf(
            MonsterCost(Items.REDSTONE_BLOCK, MatchRules.GOLEM_COST_REDSTONE, "红石块"),
            MonsterCost(Items.IRON_INGOT, MatchRules.GOLEM_COST_IRON, "铁锭"),
        ),
        baseProductionTicks = MatchRules.GOLEM_PRODUCTION_TICKS,
        egg = ModItems.EGG_GOLEM,
    )

    private val all = listOf(ZOMBIE, SKELETON, GOLEM)

    fun byId(id: String): MonsterDefinition? = all.firstOrNull { it.id == id }

    fun byEgg(item: Item): MonsterDefinition? = all.firstOrNull { it.egg === item }

    fun selectable(): List<MonsterDefinition> = all
}
