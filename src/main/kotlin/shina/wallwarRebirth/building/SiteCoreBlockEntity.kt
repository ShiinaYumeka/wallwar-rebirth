package shina.wallwarRebirth.building

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

class SiteCoreBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(ModSites.TYPE, pos, state) {
    var buildingId: String = ""
    var buildingLevel: Int = 0
    var age: Int = 0
    val storage: SimpleContainer = SimpleContainer(27).also { container ->
        container.addListener { setChanged() }
    }

    fun serverTick(level: ServerLevel) {
        val definition = Buildings.byId(buildingId) ?: return
        if (buildingLevel <= 0) return
        age++
        var produced = false
        for (rule in definition.activeProduction(buildingLevel)) {
            if (rule.everyTicks <= 0 || age % rule.everyTicks != 0) continue
            for (output in rule.outputs) {
                offer(ItemStack(output.item, output.count))
                produced = true
            }
        }
        if (produced || age % 20 == 0) setChanged()
    }

    fun select(definition: BuildingDefinition) {
        if (buildingId.isNotEmpty()) return
        val level = level as? ServerLevel ?: return
        buildingId = definition.id
        buildingLevel = 1
        age = 0
        setChanged()
        BuildingStructure.place(level, blockPos, definition, buildingLevel)
    }

    fun tryUpgrade(player: ServerPlayer): Boolean {
        val definition = Buildings.byId(buildingId) ?: return false
        if (buildingLevel >= definition.maxLevel) {
            player.sendSystemMessage(Component.literal("已满级"))
            return false
        }
        val next = definition.upgradeTo(buildingLevel + 1) ?: return false
        if (!InventoryCosts.has(player, next.costs)) {
            player.sendSystemMessage(Component.literal("材料不足"))
            return false
        }
        InventoryCosts.take(player, next.costs)
        buildingLevel += 1
        setChanged()
        player.sendSystemMessage(Component.literal("${definition.displayName}已升至 $buildingLevel 级"))
        return true
    }

    fun offer(stack: ItemStack) {
        val remaining = stack.copy()
        while (!remaining.isEmpty) {
            val piece = remaining.copy()
            val max = piece.maxStackSize
            if (piece.count > max) piece.count = max
            val leftover = insert(piece)
            val accepted = piece.count - leftover.count
            if (accepted <= 0) return
            remaining.shrink(accepted)
        }
    }

    fun snapshot(): Snapshot = Snapshot(
        buildingId,
        buildingLevel,
        age,
        (0 until storage.containerSize).map { storage.getItem(it).copy() },
    )

    fun restore(snapshot: Snapshot) {
        buildingId = snapshot.buildingId
        buildingLevel = snapshot.buildingLevel
        age = snapshot.age
        snapshot.items.forEachIndexed { slot, stack ->
            if (slot < storage.containerSize) storage.setItem(slot, stack.copy())
        }
        setChanged()
    }

    override fun preRemoveSideEffects(pos: BlockPos, state: BlockState) {
        val world = level
        if (world != null) {
            net.minecraft.world.Containers.dropContents(world, pos, storage as Container)
            storage.clearContent()
        }
        super.preRemoveSideEffects(pos, state)
    }

    override fun saveAdditional(output: ValueOutput) {
        super.saveAdditional(output)
        output.putString("Building", buildingId)
        output.putInt("Level", buildingLevel)
        output.putInt("Age", age)
        val items = (0 until storage.containerSize).map { storage.getItem(it) }
        output.store("Storage", ItemStack.OPTIONAL_CODEC.listOf(), items)
    }

    override fun loadAdditional(input: ValueInput) {
        super.loadAdditional(input)
        buildingId = input.getStringOr("Building", "")
        buildingLevel = input.getIntOr("Level", 0)
        age = input.getIntOr("Age", 0)
        val items = input.read("Storage", ItemStack.OPTIONAL_CODEC.listOf()).orElse(emptyList())
        if (items != null) {
            items.forEachIndexed { slot, stack ->
                if (slot < storage.containerSize) storage.setItem(slot, stack)
            }
        }
    }

    private fun insert(stack: ItemStack): ItemStack {
        val remaining = stack.copy()
        for (slot in 0 until storage.containerSize) {
            if (remaining.isEmpty) break
            val existing = storage.getItem(slot)
            if (existing.isEmpty) {
                storage.setItem(slot, remaining)
                return ItemStack.EMPTY
            }
            if (!ItemStack.isSameItemSameComponents(existing, remaining)) continue
            val space = existing.maxStackSize - existing.count
            if (space <= 0) continue
            val move = minOf(space, remaining.count)
            existing.grow(move)
            remaining.shrink(move)
        }
        return remaining
    }

    data class Snapshot(val buildingId: String, val buildingLevel: Int, val age: Int, val items: List<ItemStack>)
}
