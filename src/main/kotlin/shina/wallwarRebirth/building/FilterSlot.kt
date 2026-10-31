package shina.wallwarRebirth.building

import net.minecraft.world.Container
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

class FilterSlot(
    container: Container,
    index: Int,
    private val accept: (ItemStack) -> Boolean,
) : Slot(container, index, 0, 0) {
    override fun mayPlace(stack: ItemStack): Boolean = !stack.isEmpty && accept(stack)
}
