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

    fun hasAll(container: Container, costs: List<ItemAmount>): Boolean =
        hasAll(container, null, costs)

    fun hasAll(primary: Container, extra: Container?, costs: List<ItemAmount>): Boolean =
        costs.all { count(primary, it.item) + stored(extra, it.item) >= it.count }

    fun takeAll(container: Container, costs: List<ItemAmount>) {
        takeAll(container, null, costs)
    }

    fun takeAll(primary: Container, extra: Container?, costs: List<ItemAmount>) {
        for (cost in costs) {
            val fromPrimary = minOf(cost.count, count(primary, cost.item))
            takeExact(primary, cost.item, fromPrimary)
            val rest = cost.count - fromPrimary
            if (rest > 0) takeStored(extra, cost.item, rest)
        }
    }

    fun has(player: ServerPlayer, container: Container, extra: Container?, costs: List<ItemAmount>): Boolean =
        costs.all { count(player, it.item) + count(container, it.item) + stored(extra, it.item) >= it.count }

    fun take(player: ServerPlayer, container: Container, extra: Container?, costs: List<ItemAmount>) {
        for (cost in costs) {
            var left = cost.count
            val fromPlayer = minOf(left, count(player, cost.item))
            take(player, cost.item, fromPlayer)
            left -= fromPlayer
            val fromContainer = minOf(left, count(container, cost.item))
            takeExact(container, cost.item, fromContainer)
            left -= fromContainer
            if (left > 0) takeStored(extra, cost.item, left)
        }
    }

    private fun stored(container: Container?, item: Item): Int =
        if (container == null) 0 else count(container, item)

    private fun takeStored(container: Container?, item: Item, amount: Int) {
        val target = container ?: return
        if (amount <= 0) return
        takeExact(target, item, amount)
    }

    private fun takeExact(container: Container, item: Item, amount: Int) {
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
