package shina.wallwarRebirth.monster

import net.minecraft.world.item.Item
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
    val copperGear: Boolean = false,
    val source: MonsterSource = MonsterSource.BARRACKS,
    val usesCrossbow: Boolean = false,
    val fangs: Boolean = false,
    val axe: Boolean = false,
    val civilian: Boolean = false,
    val buildingsOnly: Boolean = false,
) {
    val productionReady: Boolean get() = cost.isNotEmpty() && baseProductionTicks > 0

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
        if (civilian) {
            lines.add("无战斗属性，不主动攻击也不反击")
            if (productionReady) {
                lines.add("消耗 ${costText()}")
                lines.add("当前产出间隔 ${MatchRules.secondsText(productionTicks(barracksLevel))} 秒")
            } else {
                lines.add("兵营生产消耗与间隔尚未确定")
            }
            return lines
        }
        lines.add("盔甲 ${armor.toInt()}")
        if (miner) {
            val pick = MatchRules.IRON_PICKAXE_ATTACK.toInt()
            lines.add("对玩家攻击 ${(playerAttack + pick).toInt()}（本体 ${playerAttack.toInt()}，铁镐另加 $pick）")
        } else if (explodes) {
            lines.add("爆炸中心伤害 ${playerAttack.toInt()}，威力 ${MatchRules.CREEPER_POWER.toInt()}，不随难度变化")
        } else {
            lines.add("对玩家攻击 ${playerAttack.toInt()}")
        }
        if (explodes) {
            lines.add("进入 ${attackRange.toInt()} 格后引信 1.5 秒，造成建筑破坏力 $buildingDamage 后自毁")
        } else {
            lines.add("建筑破坏力 $buildingDamage，拆建筑间隔 1 秒，攻击距离 ${attackRange.toInt()}")
        }
        if (buildingsOnly) {
            if (source == MonsterSource.TOWER) {
                lines.add("掠夺下只索敌敌方建筑，不追玩家和唤灵，也不因受伤反击；固守下仍留在所属哨塔附近按固守索敌")
            } else {
                lines.add("只索敌敌方建筑，不追玩家和唤灵，也不因受伤反击")
            }
        }
        val bow = bowRange
        if (bow != null) {
            if (usesCrossbow) {
                lines.add("弩射程 ${bow.toInt()} 格，走位类骷髅，射击节奏按原版掠夺者弩，命中盔甲前伤害 ${playerAttack.toInt()}")
            } else {
                lines.add("弓射程 ${bow.toInt()} 格，走位和拉弓间隔按原版骷髅，命中盔甲前伤害 ${playerAttack.toInt()}")
            }
        }
        if (fangs) lines.add("对玩家以法术尖牙结算伤害 ${playerAttack.toInt()}，并按原版唤魔者召唤恼鬼")
        if (axe) lines.add("手持铁斧近战，对玩家伤害 ${playerAttack.toInt()}")
        if (miner) {
            lines.add("不主动攻击建筑和敌人，挨打后才反击")
            lines.add("挖满 ${MatchRules.MINER_CARGO} 块矿后回本队基地，每 ${MatchRules.MINER_ORES_PER_CYCLE} 块让基地按当前等级和模块生产一次，钻石仍按 ${MatchRules.BASE_DIAMOND_PERCENT}% 概率")
            lines.add("死亡时按身上的矿计数掉落等量铁锭和等量铜锭")
        }
        if (sword) lines.add("手持铁剑近战，挥击间隔与僵尸相同，伤害不含铁剑的原版攻击")
        if (copperGear) lines.add("铜甲和铜剑不掉，对玩家伤害不含铜剑的原版攻击")
        if (batch > 1) lines.add("一轮生产放出 $batch 只")
        lines.add("消耗 ${costText()}")
        lines.add("当前产出间隔 ${MatchRules.secondsText(productionTicks(barracksLevel))} 秒")
        return lines
    }
}

enum class MonsterSource {
    BARRACKS,
    TOWER,
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
