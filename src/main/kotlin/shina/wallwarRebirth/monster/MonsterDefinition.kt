package shina.wallwarRebirth.monster

import net.minecraft.world.item.Item

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
    val dropItem: Item,
    val dropCount: Int,
)

object MonsterRules {
    const val CORE_RETARGET_TICKS = 40
    const val STUCK_TICKS = 60
    const val STUCK_HORIZONTAL = 0.5
    const val CHASE_RADIUS_MAX = 16.0
    const val CHASE_RADIUS_START = 16.0
    const val RADIUS_RECOVER_PER_SECOND = 0.5
    const val RADIUS_SHRINK_PER_SECOND = 2.0
    const val CHASE_LOSE_INTEREST_TICKS = 100
    const val COUNTER_END_TICKS = 60
    const val SECOND_TICKS = 20
    const val MELEE_INTERVAL_TICKS = 20
    const val REPATH_TICKS = 10
}
