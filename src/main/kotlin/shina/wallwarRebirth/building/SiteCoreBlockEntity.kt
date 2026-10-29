package shina.wallwarRebirth.building

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
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
    var health: Int = 0
    var team: String = ""
    var age: Int = 0
    val storage: SimpleContainer = SimpleContainer(STORAGE_SIZE).also { container ->
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

    fun assign(definition: BuildingDefinition, replace: Boolean, teamName: String) {
        if (!replace && buildingId.isNotEmpty()) return
        val level = level as? ServerLevel ?: return
        buildingId = definition.id
        buildingLevel = 1
        health = definition.maxHealth(1)
        team = teamName
        age = 0
        setChanged()
        BuildingStructure.place(level, blockPos, definition, buildingLevel)
    }

    fun damageFromMonster(attackerTeam: String, amount: Int) {
        if (amount <= 0 || isRemoved || buildingId.isEmpty() || health <= 0) return
        if (attackerTeam == team) return
        health -= amount
        if (health < 0) health = 0
        setChanged()
        if (health == 0) {
            val world = level as? ServerLevel ?: return
            BuildingStructure.clear(world, blockPos)
        }
    }

    override fun setLevel(level: Level) {
        super.setLevel(level)
        if (!level.isClientSide) SiteCores.add(this)
    }

    override fun setRemoved() {
        SiteCores.remove(this)
        super.setRemoved()
    }

    fun demolish(player: ServerPlayer): Boolean {
        val definition = Buildings.byId(buildingId) ?: return false
        if (definition.category != BuildingCategory.NORMAL) {
            player.sendSystemMessage(Component.literal("不能拆除"))
            return false
        }
        if (team.isNotEmpty() && player.team?.name != team) {
            player.sendSystemMessage(Component.literal("只有同队可以拆除"))
            return false
        }
        val world = level as? ServerLevel ?: return false
        BuildingStructure.clear(world, blockPos)
        return true
    }

    fun tryUpgrade(player: ServerPlayer): Boolean {
        val definition = Buildings.byId(buildingId) ?: return false
        if (buildingLevel >= definition.maxLevel) {
            player.sendSystemMessage(Component.literal("已满级"))
            return false
        }
        val next = definition.upgradeTo(buildingLevel + 1) ?: return false
        if (!InventoryCosts.has(player, storage, next.costs)) {
            player.sendSystemMessage(Component.literal("材料不足"))
            return false
        }
        InventoryCosts.take(player, storage, next.costs)
        val oldMax = definition.maxHealth(buildingLevel)
        buildingLevel += 1
        val newMax = definition.maxHealth(buildingLevel)
        val delta = newMax - oldMax
        health = if (delta >= 0) health + delta else minOf(health, newMax)
        setChanged()
        val world = level as? ServerLevel
        if (world != null) BuildingStructure.place(world, blockPos, definition, buildingLevel)
        player.containerMenu.broadcastChanges()
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
        health,
        team,
        age,
        (0 until storage.containerSize).map { storage.getItem(it).copy() },
    )

    fun restore(snapshot: Snapshot) {
        buildingId = snapshot.buildingId
        buildingLevel = snapshot.buildingLevel
        health = snapshot.health
        team = snapshot.team
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
        output.putInt("Health", health)
        output.putString("Team", team)
        output.putInt("Age", age)
        val items = (0 until storage.containerSize).map { storage.getItem(it) }
        output.store("Storage", ItemStack.OPTIONAL_CODEC.listOf(), items)
    }

    override fun loadAdditional(input: ValueInput) {
        super.loadAdditional(input)
        buildingId = input.getStringOr("Building", "")
        buildingLevel = input.getIntOr("Level", 0)
        health = input.getIntOr("Health", -1)
        if (health < 0) {
            health = Buildings.byId(buildingId)?.maxHealth(buildingLevel) ?: 0
        }
        team = input.getStringOr("Team", "")
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

    data class Snapshot(
        val buildingId: String,
        val buildingLevel: Int,
        val health: Int,
        val team: String,
        val age: Int,
        val items: List<ItemStack>,
    )

    companion object {
        const val STORAGE_SIZE = 36
    }
}
