package shina.wallwarRebirth.match

import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object Classes {
    const val MINER = "miner"
    const val ACTIVE_TICKS = MatchRules.MINER_ACTIVE_TICKS
    const val LAPIS_COST = MatchRules.MINER_LAPIS_BLOCKS

    fun name(id: String): String = if (id == MINER) "矿工" else "矿工"

    fun isMiner(classLocked: Boolean, classId: String): Boolean = classLocked && classId == MINER

    fun digDamage(classLocked: Boolean, classId: String, minerUntil: Long, now: Long): Int {
        if (!isMiner(classLocked, classId) || minerUntil <= now) return MatchRules.DIG_DAMAGE
        return MatchRules.MINER_DIG_DAMAGE
    }

    class MinerCast(val message: String, val minerUntil: Long?)

    fun castMiner(
        classLocked: Boolean,
        classId: String,
        minerUntil: Long,
        fighting: Boolean,
        now: Long,
        stack: ItemStack,
    ): MinerCast? {
        if (!isMiner(classLocked, classId)) return null
        if (!stack.`is`(Items.LAPIS_BLOCK)) return null
        if (!fighting) return MinerCast("现在不能释放", null)
        if (minerUntil > now) {
            val seconds = ((minerUntil - now + 19) / 20).toInt()
            return MinerCast("挖掘强化还剩 $seconds 秒", null)
        }
        if (stack.count < LAPIS_COST) return MinerCast("需要 $LAPIS_COST 个青金石块", null)
        stack.shrink(LAPIS_COST)
        return MinerCast("挖掘强化持续 ${ACTIVE_TICKS / MatchRules.TICKS_PER_SECOND} 秒", now + ACTIVE_TICKS)
    }
}
