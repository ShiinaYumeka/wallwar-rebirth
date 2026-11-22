package shina.wallwarRebirth.item

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import shina.wallwarRebirth.building.CannonShell
import shina.wallwarRebirth.match.Match

object ShopTnt {
    fun use(player: ServerPlayer, hand: InteractionHand): InteractionResult {
        val stack = player.getItemInHand(hand)
        if (!stack.`is`(Items.TNT)) return InteractionResult.PASS
        val level = player.level()
        if (!Match.isArena(level)) return InteractionResult.PASS
        if (Match.phase != Match.Phase.FIGHT || Match.frozen(level)) {
            return InteractionResult.FAIL
        }
        val team = Match.member(player.uuid)?.team ?: ""
        if (team.isEmpty()) return InteractionResult.FAIL
        CannonShell.launchThrown(level, player, team)
        if (!player.abilities.instabuild) stack.shrink(1)
        player.swing(hand, true)
        return InteractionResult.SUCCESS
    }

    fun use(player: ServerPlayer, hand: InteractionHand, stack: ItemStack): InteractionResult {
        if (!stack.`is`(Items.TNT)) return InteractionResult.PASS
        return use(player, hand)
    }
}
