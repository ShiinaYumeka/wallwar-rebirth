package shina.wallwarRebirth.match

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import shina.wallwarRebirth.item.ReturnLobbyItem

object MenuClicks {
    @JvmStatic
    fun onSlotClick(player: Player, stack: ItemStack): Boolean {
        val serverPlayer = player as? ServerPlayer ?: return false
        if (stack.isEmpty) return false
        if (!ReturnLobbyItem.isReturn(stack)) return false
        ReturnLobbyItem.activate(serverPlayer)
        return true
    }
}
