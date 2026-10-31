package shina.wallwarRebirth.monster

import net.minecraft.world.item.Item
import shina.wallwarRebirth.match.MatchRules

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
    val dropItem: Item,
    val dropCount: Int,
    val dropName: String,
    val cost: List<MonsterCost>,
    val baseProductionTicks: Int,
    val egg: Item,
    val bowRange: Double? = null,
    val undead: Boolean = false,
    val minBarracksLevel: Int = 1,
) {
    fun productionTicks(barracksLevel: Int): Int = when {
        barracksLevel >= 3 -> baseProductionTicks / 2
        barracksLevel == 2 -> baseProductionTicks * 2 / 3
        else -> baseProductionTicks
    }.coerceAtLeast(1)

    fun costText(): String = cost.joinToString("、") { "${it.name} ×${it.count}" }

    fun detailLines(barracksLevel: Int): List<String> {
        val lines = ArrayList<String>()
        lines.add("等级 $rank")
        lines.add("生命 ${health.toInt()}")
        lines.add("盔甲 ${armor.toInt()}")
        lines.add("对玩家攻击 ${playerAttack.toInt()}")
        lines.add("建筑破坏力 $buildingDamage，拆建筑间隔 1 秒，攻击距离 ${attackRange.toInt()}")
        val bow = bowRange
        if (bow != null) {
            lines.add("弓射程 ${bow.toInt()} 格，走位和拉弓间隔按原版骷髅，命中盔甲前伤害 ${playerAttack.toInt()}")
        }
        lines.add("消耗 ${costText()}")
        lines.add("当前兵营产出间隔 ${productionTicks(barracksLevel) / 20} 秒")
        lines.add("击杀掉落 $dropName ×$dropCount")
        return lines
    }
}

object MonsterRules {
    const val CORE_RETARGET_TICKS = MatchRules.CORE_RETARGET_TICKS
    const val STUCK_TICKS = MatchRules.STUCK_TICKS
    const val STUCK_HORIZONTAL = MatchRules.STUCK_HORIZONTAL
    const val CHASE_RADIUS_MAX = MatchRules.CHASE_RADIUS_MAX
    const val CHASE_RADIUS_START = MatchRules.CHASE_RADIUS_START
    const val RADIUS_RECOVER_PER_SECOND = MatchRules.RADIUS_RECOVER_PER_SECOND
    const val RADIUS_SHRINK_PER_SECOND = MatchRules.RADIUS_SHRINK_PER_SECOND
    const val CHASE_LOSE_INTEREST_TICKS = MatchRules.CHASE_LOSE_INTEREST_TICKS
    const val COUNTER_END_TICKS = MatchRules.COUNTER_END_TICKS
    const val SECOND_TICKS = MatchRules.TICKS_PER_SECOND
    const val MELEE_INTERVAL_TICKS = MatchRules.BUILDING_HIT_TICKS
    const val REPATH_TICKS = MatchRules.REPATH_TICKS
    const val SKELETON_BOW_RANGE = MatchRules.SKELETON_BOW_RANGE
    const val SKELETON_BOW_SPEED = MatchRules.SKELETON_BOW_SPEED
}
