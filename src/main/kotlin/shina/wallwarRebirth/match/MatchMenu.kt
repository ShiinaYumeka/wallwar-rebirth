package shina.wallwarRebirth.match

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import shina.wallwarRebirth.item.MatchMenuItem
import shina.wallwarRebirth.item.ModItems

object MatchMenu {
    fun give(player: ServerPlayer) {
        if (has(player)) return
        val stack = ItemStack(ModItems.MENU)
        if (!player.inventory.add(stack)) player.drop(stack, false)
    }

    fun take(player: ServerPlayer) {
        val inventory = player.inventory
        for (slot in 0 until inventory.containerSize) {
            if (MatchMenuItem.isMenu(inventory.getItem(slot))) inventory.setItem(slot, ItemStack.EMPTY)
        }
        if (MatchMenuItem.isMenu(player.getItemBySlot(EquipmentSlot.OFFHAND))) {
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY)
        }
    }

    private fun has(player: ServerPlayer): Boolean {
        val inventory = player.inventory
        for (slot in 0 until inventory.containerSize) {
            if (MatchMenuItem.isMenu(inventory.getItem(slot))) return true
        }
        return MatchMenuItem.isMenu(player.getItemBySlot(EquipmentSlot.OFFHAND))
    }
}
