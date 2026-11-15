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
import shina.wallwarRebirth.item.SwitchTeamItem
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
        val settle = !lobby && Match.phase == Match.Phase.SETTLE && Match.needsSettleConsent(player)
        reconcile(player, { ReturnLobbyItem.isReturn(it) }, settle || (!lobby && !teamed)) {
            if (settle) named(ModItems.RETURN_LOBBY, "返回大厅", Match.settleHint(player))
            else named(ModItems.RETURN_LOBBY, "返回大厅", "点击返回大厅，对局继续进行")
        }
        if (settle || (!lobby && !teamed)) {
            retitle(player, { ReturnLobbyItem.isReturn(it) }, if (settle) {
                named(ModItems.RETURN_LOBBY, "返回大厅", Match.settleHint(player))
            } else {
                named(ModItems.RETURN_LOBBY, "返回大厅", "点击返回大厅，对局继续进行")
            })
        }
        reconcile(player, { SwitchTeamItem.isSwitch(it) }, Match.testMode && teamed) {
            named(ModItems.SWITCH_TEAM, "换队伍", "右键切换到另一队")
        }
    }

    @JvmStatic
    fun isBound(stack: ItemStack): Boolean = BoundItems.isBound(stack)

    fun stripOnDeath(player: Player) {
        takeMatching(player) { ReturnLobbyItem.isReturn(it) }
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
            BoundItems.offerToMain(player, factory())
            return
        }
        BoundItems.recoverMisplaced(player)
        for (index in 1 until found.size) {
            clearAt(player, found[index])
        }
    }

    private fun retitle(player: ServerPlayer, matches: (ItemStack) -> Boolean, sample: ItemStack) {
        val name = sample.get(DataComponents.CUSTOM_NAME)
        val lore = sample.get(DataComponents.LORE)
        for (ref in locate(player, matches)) {
            val stack = stackAt(player, ref) ?: continue
            if (name != null) stack.set(DataComponents.CUSTOM_NAME, name)
            if (lore != null) stack.set(DataComponents.LORE, lore)
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

    private fun stackAt(player: ServerPlayer, ref: SlotRef): ItemStack? = when (ref) {
        is SlotRef.Inventory -> player.inventory.getItem(ref.slot)
        SlotRef.Offhand -> player.getItemBySlot(EquipmentSlot.OFFHAND)
        SlotRef.Carried -> player.containerMenu.carried
    }

    private sealed class SlotRef {

        data class Inventory(val slot: Int) : SlotRef()
        data object Offhand : SlotRef()
        data object Carried : SlotRef()
    }
}
