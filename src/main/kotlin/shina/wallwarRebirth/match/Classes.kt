package shina.wallwarRebirth.match

import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object Classes {
    const val MINER = "miner"
    const val ACTIVE_TICKS = MatchRules.MINER_ACTIVE_TICKS
    const val LAPIS_COST = MatchRules.MINER_LAPIS_BLOCKS

    fun name(id: String): String = if (id == MINER) "矿工" else "矿工"

    fun isMiner(player: ServerPlayer): Boolean {
        val member = Match.member(player.uuid) ?: return false
        return member.classLocked && member.classId == MINER
    }

    fun digDamage(player: ServerPlayer): Int {
        if (!isMiner(player)) return MatchRules.DIG_DAMAGE
        val until = Match.member(player.uuid)?.minerUntil ?: return MatchRules.DIG_DAMAGE
        return if (until > now(player)) MatchRules.MINER_DIG_DAMAGE else MatchRules.DIG_DAMAGE
    }

    fun castMiner(player: ServerPlayer, stack: ItemStack): Boolean {
        val member = Match.member(player.uuid) ?: return false
        if (!member.classLocked || member.classId != MINER) return false
        if (!stack.`is`(Items.LAPIS_BLOCK)) return false
        if (!Match.isFight()) {
            player.sendSystemMessage(Component.literal("现在不能释放"))
            return true
        }
        val time = now(player)
        if (member.minerUntil > time) {
            val seconds = ((member.minerUntil - time + 19) / 20).toInt()
            player.sendSystemMessage(Component.literal("挖掘强化还剩 $seconds 秒"))
            return true
        }
        if (stack.count < LAPIS_COST) {
            player.sendSystemMessage(Component.literal("需要 $LAPIS_COST 个青金石块"))
            return true
        }
        stack.shrink(LAPIS_COST)
        member.minerUntil = time + ACTIVE_TICKS
        player.sendSystemMessage(Component.literal("挖掘强化持续 ${ACTIVE_TICKS / MatchRules.TICKS_PER_SECOND} 秒"))
        Match.remember(player)
        return true
    }

    private fun now(player: ServerPlayer): Long = player.level().server.overworld().gameTime
}
