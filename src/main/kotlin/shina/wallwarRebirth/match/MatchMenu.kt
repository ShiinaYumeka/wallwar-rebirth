package shina.wallwarRebirth.match

import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore
import shina.wallwarRebirth.item.MatchMenuItem
import shina.wallwarRebirth.item.ModItems
import shina.wallwarRebirth.item.PortableTerminalItem
import shina.wallwarRebirth.item.ReturnLobbyItem
import shina.wallwarRebirth.rules.MatchRules

object MatchMenu {
    fun tick(server: MinecraftServer) {
        for (player in server.playerList.players) give(player)
    }

    fun give(player: ServerPlayer) {
        val lobby = player.level().dimension() != MatchRules.DIMENSION
        val member = Match.member(player.uuid)
        val teamed = member != null && member.team.isNotEmpty()
        reconcile(player, { MatchMenuItem.isMenu(it) }, lobby) {
            named(ModItems.MENU, "大厅终端", "右键打开大厅终端")
        }
        reconcile(player, { PortableTerminalItem.isTerminal(it) }, !lobby) {
            named(ModItems.TERMINAL, "局内终端", "右键打开局内终端")
        }
        reconcile(player, { ReturnLobbyItem.isReturn(it) }, !lobby && !teamed) {
            named(ModItems.RETURN_LOBBY, "返回大厅", "点击返回大厅，对局继续进行")
        }
    }

    @JvmStatic
    fun isBound(stack: ItemStack): Boolean =
        MatchMenuItem.isMenu(stack) || PortableTerminalItem.isTerminal(stack)

    fun stripOnDeath(player: Player) {
        takeMatching(player) { isBound(it) || ReturnLobbyItem.isReturn(it) }
    }

    private fun reconcile(
        player: ServerPlayer,
        matches: (ItemStack) -> Boolean,
        want: Boolean,
        factory: () -> ItemStack,
    ) {
        if (!want) {
            takeMatching(player, matches)
            return
        }
        val found = locate(player, matches)
        if (found.isEmpty()) {
            val stack = factory()
            if (!player.inventory.add(stack)) player.drop(stack, false)
            return
        }
        for (index in 1 until found.size) {
            clearAt(player, found[index])
        }
    }

    private fun locate(player: ServerPlayer, matches: (ItemStack) -> Boolean): List<SlotRef> {
        val found = ArrayList<SlotRef>()
        val inventory = player.inventory
        for (slot in 0 until inventory.containerSize) {
            if (matches(inventory.getItem(slot))) found.add(SlotRef.Inventory(slot))
        }
        if (matches(player.getItemBySlot(EquipmentSlot.OFFHAND))) found.add(SlotRef.Offhand)
        if (matches(player.containerMenu.carried)) found.add(SlotRef.Carried)
        return found
    }

    private fun takeMatching(player: Player, matches: (ItemStack) -> Boolean) {
        val inventory = player.inventory
        for (slot in 0 until inventory.containerSize) {
            if (matches(inventory.getItem(slot))) inventory.setItem(slot, ItemStack.EMPTY)
        }
        if (matches(player.getItemBySlot(EquipmentSlot.OFFHAND))) {
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY)
        }
        if (matches(player.containerMenu.carried)) {
            player.containerMenu.carried = ItemStack.EMPTY
        }
    }

    private fun clearAt(player: ServerPlayer, ref: SlotRef) {
        when (ref) {
            is SlotRef.Inventory -> player.inventory.setItem(ref.slot, ItemStack.EMPTY)
            SlotRef.Offhand -> player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY)
            SlotRef.Carried -> player.containerMenu.carried = ItemStack.EMPTY
        }
    }

    private fun named(item: Item, title: String, hint: String): ItemStack {
        val stack = ItemStack(item)
        stack[DataComponents.CUSTOM_NAME] = Component.literal(title).withStyle { style ->
            style.withItalic(false).withColor(ChatFormatting.WHITE)
        }
        stack[DataComponents.LORE] = ItemLore(
            listOf(Component.literal(hint).withStyle { style ->
                style.withItalic(false).withColor(ChatFormatting.GRAY)
            }),
        )
        return stack
    }

    private sealed class SlotRef {
        data class Inventory(val slot: Int) : SlotRef()
        data object Offhand : SlotRef()
        data object Carried : SlotRef()
    }
}
