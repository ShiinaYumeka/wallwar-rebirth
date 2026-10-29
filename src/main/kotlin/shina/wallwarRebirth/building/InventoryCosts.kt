package shina.wallwarRebirth.building

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Container
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

object InventoryCosts {
    fun count(player: ServerPlayer, item: Item): Int {
        var total = 0
        for (stack in player.inventory.nonEquipmentItems) {
            if (stack.`is`(item)) total += stack.count
        }
        val offhand = player.getItemBySlot(EquipmentSlot.OFFHAND)
        if (offhand.`is`(item)) total += offhand.count
        return total
    }

    fun count(container: Container, item: Item): Int {
        var total = 0
        for (slot in 0 until container.containerSize) {
            val stack = container.getItem(slot)
            if (stack.`is`(item)) total += stack.count
        }
        return total
    }

    fun has(player: ServerPlayer, container: Container, costs: List<ItemAmount>): Boolean =
        costs.all { count(player, it.item) + count(container, it.item) >= it.count }

    fun take(player: ServerPlayer, container: Container, costs: List<ItemAmount>) {
        for (cost in costs) {
            val fromStorage = minOf(cost.count, count(container, cost.item))
            take(container, cost.item, fromStorage)
            val rest = cost.count - fromStorage
            if (rest > 0) take(player, cost.item, rest)
        }
    }

    private fun take(container: Container, item: Item, amount: Int) {
        var left = amount
        for (slot in 0 until container.containerSize) {
            if (left <= 0) return
            val stack = container.getItem(slot)
            if (!stack.`is`(item)) continue
            val remove = minOf(left, stack.count)
            stack.shrink(remove)
            left -= remove
            if (stack.isEmpty) container.setItem(slot, ItemStack.EMPTY) else container.setChanged()
        }
    }

    private fun take(player: ServerPlayer, item: Item, amount: Int) {
        val left = takeFrom(player.inventory.nonEquipmentItems, item, amount) { slot ->
            player.inventory.setItem(slot, ItemStack.EMPTY)
        }
        if (left <= 0) return
        val offhand = player.getItemBySlot(EquipmentSlot.OFFHAND)
        if (!offhand.`is`(item)) return
        val remove = minOf(left, offhand.count)
        offhand.shrink(remove)
        if (offhand.isEmpty) player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY)
    }

    private fun takeFrom(
        stacks: List<ItemStack>,
        item: Item,
        amount: Int,
        clear: (Int) -> Unit,
    ): Int {
        var left = amount
        for (slot in stacks.indices) {
            if (left <= 0) break
            val stack = stacks[slot]
            if (!stack.`is`(item)) continue
            val remove = minOf(left, stack.count)
            stack.shrink(remove)
            left -= remove
            if (stack.isEmpty) clear(slot)
        }
        return left
    }
}
