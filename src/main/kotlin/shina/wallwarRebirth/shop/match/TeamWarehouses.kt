package shina.wallwarRebirth.shop.match

import com.mojang.serialization.DynamicOps
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.SimpleContainer
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.scores.PlayerTeam
import shina.wallwarRebirth.contract.WallwarTeams
import shina.wallwarRebirth.shop.TeamMaterialStore
import shina.wallwarRebirth.shop.TeamStoreLookup
import shina.wallwarRebirth.shop.TeamStores
import shina.wallwarRebirth.shop.element.Material

object TeamWarehouses {
    const val SIZE = 54

    private val boxes = HashMap<String, SimpleContainer>()

    fun init() {
        TeamStores.lookup = TeamStoreLookup { _, team -> store(team) }
    }

    fun acceptAll(team: String, stack: ItemStack): Boolean {
        if (stack.isEmpty) return true
        val box = of(team) ?: return false
        if (!fits(box, stack)) return false
        return ChestStore(box).quickPut(stack.copy()).isEmpty
    }

    private fun fits(box: SimpleContainer, stack: ItemStack): Boolean {
        var left = stack.count
        for (slot in 0 until box.containerSize) {
            if (left <= 0) return true
            val there = box.getItem(slot)
            if (there.isEmpty) {
                left -= stack.maxStackSize
            } else if (ItemStack.isSameItemSameComponents(there, stack)) {
                left -= (there.maxStackSize - there.count).coerceAtLeast(0)
            }
        }
        return left <= 0
    }

    fun of(team: String): SimpleContainer? {
        if (team != WallwarTeams.RED && team != WallwarTeams.BLUE) return null
        return boxes.getOrPut(team) { SimpleContainer(SIZE) }
    }

    fun clear() {
        for (box in boxes.values) box.clearContent()
        boxes.clear()
    }

    fun write(tag: CompoundTag, ops: DynamicOps<Tag>) {
        val list = ListTag()
        for ((team, box) in boxes) {
            val entry = CompoundTag()
            entry.putString("Team", team)
            val items = ListTag()
            for (slot in 0 until box.containerSize) {
                val stack = box.getItem(slot)
                if (stack.isEmpty) continue
                val saved = ItemStack.CODEC.encodeStart(ops, stack).resultOrPartial { }.orElse(null)
                if (saved !is CompoundTag) continue
                val wrapped = CompoundTag()
                wrapped.putInt("Slot", slot)
                wrapped.put("Stack", saved)
                items.add(wrapped)
            }
            entry.put("Items", items)
            list.add(entry)
        }
        tag.put("Warehouses", list)
    }

    fun read(tag: CompoundTag, ops: DynamicOps<Tag>) {
        clear()
        val list = tag.getListOrEmpty("Warehouses")
        for (index in 0 until list.size) {
            val entry = list.getCompoundOrEmpty(index)
            val team = entry.getStringOr("Team", "")
            val box = of(team) ?: continue
            val items = entry.getListOrEmpty("Items")
            for (itemIndex in 0 until items.size) {
                val saved = items.getCompoundOrEmpty(itemIndex)
                val slot = saved.getIntOr("Slot", -1)
                if (slot !in 0 until box.containerSize) continue
                val stack = ItemStack.CODEC.parse(ops, saved.getCompoundOrEmpty("Stack")).resultOrPartial { }.orElse(ItemStack.EMPTY) ?: continue
                if (!stack.isEmpty) box.setItem(slot, stack)
            }
        }
    }

    private fun store(team: PlayerTeam): TeamMaterialStore? {
        val box = of(team.name) ?: return null
        return ChestStore(box)
    }
}

private class ChestStore(private val box: SimpleContainer) : TeamMaterialStore {
    override fun countMaterialAmount(material: Material): Int {
        var total = 0
        for (slot in 0 until box.containerSize) {
            val stack = box.getItem(slot)
            if (material.matches(stack)) total += stack.count
        }
        return total
    }

    override fun deductMaterial(material: Material, amount: Int): Boolean {
        if (countMaterialAmount(material) < amount) return false
        var left = amount
        for (slot in 0 until box.containerSize) {
            if (left <= 0) break
            val stack = box.getItem(slot)
            if (!material.matches(stack)) continue
            val remove = minOf(left, stack.count)
            stack.shrink(remove)
            left -= remove
            if (stack.isEmpty) box.setItem(slot, ItemStack.EMPTY) else box.setChanged()
        }
        return left <= 0
    }

    override fun quickPut(stack: ItemStack): ItemStack {
        if (stack.isEmpty) return stack
        var left = stack.count
        for (slot in 0 until box.containerSize) {
            if (left <= 0) break
            val there = box.getItem(slot)
            if (there.isEmpty || !ItemStack.isSameItemSameComponents(there, stack)) continue
            val room = there.maxStackSize - there.count
            if (room <= 0) continue
            val move = minOf(room, left)
            there.grow(move)
            left -= move
            box.setChanged()
        }
        var slot = 0
        while (left > 0 && slot < box.containerSize) {
            if (box.getItem(slot).isEmpty) {
                val put = stack.copyWithCount(minOf(left, stack.maxStackSize))
                box.setItem(slot, put)
                left -= put.count
            }
            slot++
        }
        if (left <= 0) return ItemStack.EMPTY
        val remain = stack.copy()
        remain.count = left
        return remain
    }

    override fun open(player: ServerPlayer) {
        val gui = SimpleGui(MenuType.GENERIC_9x6, player, false)
        gui.title = net.minecraft.network.chat.Component.literal("团队仓库")
        for (slot in 0 until box.containerSize) gui.setSlotRedirect(slot, Slot(box, slot, 0, 0))
        gui.open()
    }
}
