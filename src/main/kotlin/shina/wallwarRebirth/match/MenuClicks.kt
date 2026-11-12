package shina.wallwarRebirth.match

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import shina.wallwarRebirth.item.PortableTerminalItem
import shina.wallwarRebirth.item.ReturnLobbyItem
import shina.wallwarRebirth.shop.match.PortableShop

object MenuClicks {
    @JvmStatic
    fun onSlotClick(player: Player, stack: ItemStack): Boolean {
        val serverPlayer = player as? ServerPlayer ?: return false
        if (stack.isEmpty) return false
        return when {
            ReturnLobbyItem.isReturn(stack) -> {
                ReturnLobbyItem.activate(serverPlayer)
                true
            }
            PortableTerminalItem.isTerminal(stack) -> {
                PortableShop.open(serverPlayer)
                true
            }
            else -> false
        }
    }
}
