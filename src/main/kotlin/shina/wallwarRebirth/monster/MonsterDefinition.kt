package shina.wallwarRebirth.monster

import net.minecraft.world.item.Item
import shina.wallwarRebirth.copy.GameCopy
import shina.wallwarRebirth.rules.MatchRules

data class MonsterCost(val item: Item, val count: Int, val name: String)

data class MonsterDefinition(
    val id: String,
    val displayName: String,
    val rank: Int,
    val health: Double,
    val armor: Double,
    val playerAttack: Double,
    val buildingDamage: Int,
    val buildingIntervalTicks: Int,
    val attackRange: Double,
    val cost: List<MonsterCost>,
    val baseProductionTicks: Int,
    val egg: Item,
    val bowRange: Double? = null,
    val undead: Boolean = false,
    val ignoresDefensive: Boolean = false,
    val miner: Boolean = false,
    val wornArmor: Boolean = false,
    val batch: Int = 1,
    val sword: Boolean = false,
    val explodes: Boolean = false,
    val calmAboveHalf: Boolean = false,
    val copperGear: Boolean = false,
    val source: MonsterSource = MonsterSource.BARRACKS,
    val usesCrossbow: Boolean = false,
    val fangs: Boolean = false,
    val axe: Boolean = false,
    val civilian: Boolean = false,
    val buildingsOnly: Boolean = false,
    val requiresWorkshop: Boolean = false,
    val movementSpeedScale: Double = 1.0,
    val chaseRadiusStart: Double = MatchRules.CHASE_RADIUS_START,
    val chaseRadiusMax: Double = MatchRules.CHASE_RADIUS_MAX,
) {
    val productionReady: Boolean get() = cost.isNotEmpty() && baseProductionTicks > 0

    fun productionTicks(barracksLevel: Int): Int = when {
        barracksLevel >= 3 -> baseProductionTicks / 2
        barracksLevel == 2 -> baseProductionTicks * 2 / 3
        else -> baseProductionTicks
    }.coerceAtLeast(1)

    fun detailLines(@Suppress("UNUSED_PARAMETER") barracksLevel: Int): List<String> {
        val copy = GameCopy.monsterLines(id)
        return if (copy.isNotEmpty()) copy else emptyList()
    }
}

enum class MonsterSource {
    BARRACKS,
    TOWER,
    SUMMONED,
}

object MonsterRules {
    const val CORE_RETARGET_TICKS = MatchRules.CORE_RETARGET_TICKS
    const val STUCK_TICKS = MatchRules.STUCK_TICKS
    const val STUCK_HORIZONTAL = MatchRules.STUCK_HORIZONTAL
    const val SIEGE_GIVE_UP_TICKS = MatchRules.SIEGE_GIVE_UP_TICKS
    const val SIEGE_GIVE_UP_RANGE = MatchRules.SIEGE_GIVE_UP_RANGE
    const val RADIUS_RECOVER_PER_SECOND = MatchRules.RADIUS_RECOVER_PER_SECOND
    const val RADIUS_SHRINK_PER_SECOND = MatchRules.RADIUS_SHRINK_PER_SECOND
    const val CHASE_LOSE_INTEREST_TICKS = MatchRules.CHASE_LOSE_INTEREST_TICKS
    const val COUNTER_END_TICKS = MatchRules.COUNTER_END_TICKS
    const val SECOND_TICKS = MatchRules.TICKS_PER_SECOND
    const val REPATH_TICKS = MatchRules.REPATH_TICKS
    const val SKELETON_BOW_RANGE = MatchRules.SKELETON_BOW_RANGE
    const val SKELETON_BOW_SPEED = MatchRules.SKELETON_BOW_SPEED
}
