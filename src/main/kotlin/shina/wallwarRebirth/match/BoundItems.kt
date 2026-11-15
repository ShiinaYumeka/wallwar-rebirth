package shina.wallwarRebirth.match

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import shina.wallwarRebirth.item.CommandItem
import shina.wallwarRebirth.item.MatchMenuItem
import shina.wallwarRebirth.item.PlunderBannerItem
import shina.wallwarRebirth.item.PortableTerminalItem
import shina.wallwarRebirth.item.SkillItem
import shina.wallwarRebirth.item.SwitchTeamItem
import java.util.UUID

object BoundItems {
    private const val MAIN_SLOTS = 36
    private const val CARRIED_SLOT = -1
    private val pending = HashMap<UUID, List<Pair<Int, ItemStack>>>()

    @JvmStatic
    fun isBound(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        return MatchMenuItem.isMenu(stack) ||
            PortableTerminalItem.isTerminal(stack) ||
            SkillItem.isSkill(stack) ||
            CommandItem.isCommand(stack) ||
            PlunderBannerItem.isBanner(stack) ||
            SwitchTeamItem.isSwitch(stack)
    }

    fun offerToMain(player: ServerPlayer, stack: ItemStack): Boolean {
        if (alreadyHas(player, stack)) return true
        val inventory = player.inventory
        for (slot in 0 until MAIN_SLOTS) {
            if (inventory.getItem(slot).isEmpty) {
                inventory.setItem(slot, stack)
                return true
            }
        }
        return false
    }

    fun holds(player: Player, matches: (ItemStack) -> Boolean): Boolean =
        locate(player, matches).isNotEmpty()

    fun stash(player: ServerPlayer) {
        val saved = ArrayList<Pair<Int, ItemStack>>()
        val seen = HashSet<ItemStack>()
        val inventory = player.inventory
        for (slot in 0 until inventory.containerSize) {
            val stack = inventory.getItem(slot)
            if (!isBound(stack) || !seen.add(stack)) continue
            saved.add(slot to stack.copy())
            inventory.setItem(slot, ItemStack.EMPTY)
        }
        val offhand = player.offhandItem
        if (isBound(offhand) && seen.add(offhand)) {
            saved.add(Inventory.SLOT_OFFHAND to offhand.copy())
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY)
        }
        val carried = player.containerMenu.carried
        if (isBound(carried) && seen.add(carried)) {
            saved.add(CARRIED_SLOT to carried.copy())
            player.containerMenu.carried = ItemStack.EMPTY
        }
        if (saved.isNotEmpty()) pending[player.uuid] = saved
    }

    fun restore(player: ServerPlayer) {
        val saved = pending.remove(player.uuid) ?: return
        val inventory = player.inventory
        for ((slot, stack) in saved) {
            if (alreadyHas(player, stack)) continue
            if (slot in 0 until MAIN_SLOTS && inventory.getItem(slot).isEmpty) {
                inventory.setItem(slot, stack)
            } else {
                offerToMain(player, stack)
            }
        }
    }

    fun forget(player: Player) {
        pending.remove(player.uuid)
    }

    fun forgetAll() {
        pending.clear()
    }

    fun recoverMisplaced(player: ServerPlayer) {
        val inventory = player.inventory
        val moved = ArrayList<ItemStack>()
        for (slot in MAIN_SLOTS until inventory.containerSize) {
            val stack = inventory.getItem(slot)
            if (!isBound(stack)) continue
            inventory.setItem(slot, ItemStack.EMPTY)
            moved.add(stack)
        }
        if (isBound(player.offhandItem)) {
            moved.add(player.offhandItem.copy())
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY)
        }
        for (stack in moved) offerToMain(player, stack)
    }

    @JvmStatic
    fun blockClick(
        menu: AbstractContainerMenu,
        slotIndex: Int,
        button: Int,
        clickType: ClickType,
        player: Player,
    ): Boolean {
        val carried = menu.carried
        return when (clickType) {
            ClickType.THROW -> {
                val stack = if (slotIndex == AbstractContainerMenu.SLOT_CLICKED_OUTSIDE) {
                    carried
                } else if (slotIndex in 0 until menu.slots.size) {
                    menu.getSlot(slotIndex).item
                } else {
                    ItemStack.EMPTY
                }
                isBound(stack)
            }
            ClickType.SWAP -> {
                if (button == Inventory.SLOT_OFFHAND) {
                    val slotStack = slotStack(menu, slotIndex)
                    return isBound(slotStack) || isBound(player.offhandItem)
                }
                if (button !in 0..8) return false
                val hotbar = player.inventory.getItem(button)
                val slot = slotOf(menu, slotIndex) ?: return false
                if (isBound(hotbar) && !isMainInventorySlot(slot, player)) return true
                isBound(slot.item) && !isMainInventorySlot(slot, player)
            }
            ClickType.QUICK_MOVE -> {
                val slot = slotOf(menu, slotIndex) ?: return false
                if (!isBound(slot.item)) return false
                menu !== player.inventoryMenu || !isMainInventorySlot(slot, player)
            }
            ClickType.PICKUP, ClickType.PICKUP_ALL -> {
                if (slotIndex == AbstractContainerMenu.SLOT_CLICKED_OUTSIDE) return isBound(carried)
                val slot = slotOf(menu, slotIndex) ?: return false
                isBound(carried) && !isMainInventorySlot(slot, player)
            }
            ClickType.QUICK_CRAFT -> {
                val slot = slotOf(menu, slotIndex) ?: return false
                isBound(carried) && !isMainInventorySlot(slot, player)
            }
            else -> false
        }
    }

    private fun slotOf(menu: AbstractContainerMenu, slotIndex: Int): Slot? {
        if (slotIndex < 0 || slotIndex >= menu.slots.size) return null
        return menu.getSlot(slotIndex)
    }

    private fun slotStack(menu: AbstractContainerMenu, slotIndex: Int): ItemStack =
        slotOf(menu, slotIndex)?.item ?: ItemStack.EMPTY

    private fun isMainInventorySlot(slot: Slot, player: Player): Boolean {
        if (slot.container !== player.inventory) return false
        return slot.containerSlot in 0 until MAIN_SLOTS
    }

    fun alreadyHas(player: Player, stack: ItemStack): Boolean =
        holds(player) { sameBoundRole(it, stack) }

    private fun sameBoundRole(existing: ItemStack, incoming: ItemStack): Boolean {
        if (existing.isEmpty || incoming.isEmpty) return false
        if (CommandItem.isCommand(incoming)) return CommandItem.isCommand(existing)
        if (PlunderBannerItem.isBanner(incoming)) {
            return PlunderBannerItem.isBanner(existing) && PlunderBannerItem.tower(existing) == PlunderBannerItem.tower(incoming)
        }
        if (SkillItem.isSkill(incoming)) return SkillItem.isSkill(existing) && existing.`is`(incoming.item)
        if (MatchMenuItem.isMenu(incoming)) return MatchMenuItem.isMenu(existing)
        if (PortableTerminalItem.isTerminal(incoming)) return PortableTerminalItem.isTerminal(existing)
        if (SwitchTeamItem.isSwitch(incoming)) return SwitchTeamItem.isSwitch(existing)
        return false
    }

    private fun locate(player: Player, matches: (ItemStack) -> Boolean): List<Int> {
        val found = ArrayList<Int>()
        val inventory = player.inventory
        for (slot in 0 until inventory.containerSize) {
            if (matches(inventory.getItem(slot))) found.add(slot)
        }
        if (matches(player.offhandItem)) found.add(Inventory.SLOT_OFFHAND)
        if (matches(player.containerMenu.carried)) found.add(CARRIED_SLOT)
        return found
    }
}
