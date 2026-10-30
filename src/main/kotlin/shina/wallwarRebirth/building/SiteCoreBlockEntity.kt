package shina.wallwarRebirth.building

import com.mojang.serialization.Codec
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Container
import net.minecraft.world.Containers
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import java.util.UUID

class SiteCoreBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(ModSites.TYPE, pos, state) {
    var buildingId: String = ""
    var buildingLevel: Int = 0
    var health: Int = 0
    var team: String = ""
    var facing: String = Direction.SOUTH.serializedName
    var age: Int = 0
    var actionAge: Int = 0
    var healthDisplayId: UUID? = null
    var reinforce: ItemStack = ItemStack.EMPTY
    var reinforceMineral: String = ""
    var reinforceTwo: ItemStack = ItemStack.EMPTY
    var reinforceTwoMineral: String = ""
    val partPositions: MutableList<BlockPos> = ArrayList()
    private val mineralAges: MutableMap<String, Int> = HashMap()
    val storage: SimpleContainer = SimpleContainer(STORAGE_SIZE).also { container ->
        container.addListener { setChanged() }
    }

    fun serverTick(level: ServerLevel) {
        val definition = Buildings.byId(buildingId) ?: return
        if (buildingLevel <= 0) return
        age++
        var produced = false
        for (mineral in definition.minerals()) {
            val interval = intervalOf(definition, mineral) ?: continue
            val key = itemKey(mineral.item)
            val next = (mineralAges[key] ?: 0) + 1
            if (next >= interval) {
                offer(ItemStack(mineral.item, mineral.count))
                mineralAges[key] = 0
                produced = true
            } else {
                mineralAges[key] = next
            }
        }
        if (produced || age % 20 == 0) setChanged()
        BuildingCombat.tick(this, level)
        HealthBars.tick(this, level, definition)
    }

    fun assign(definition: BuildingDefinition, replace: Boolean, teamName: String, facing: Direction): String? {
        if (!replace && buildingId.isNotEmpty()) return "已经选定"
        val world = level as? ServerLevel ?: return "这个位置不能放置"
        val footprint = BuildingFootprints.rejection(world, blockPos, definition, facing)
        if (footprint != null) return footprint
        val style = BuildingStyles.rejection(world, blockPos, definition, 1, facing)
        if (style != null) return style
        buildingId = definition.id
        buildingLevel = 1
        health = definition.maxHealth(1)
        team = teamName
        this.facing = facing.serializedName
        age = 0
        mineralAges.clear()
        clearModules()
        partPositions.clear()
        partPositions.addAll(BuildingStyles.apply(world, blockPos, definition, buildingLevel, facing, emptyList()))
        setChanged()
        return null
    }

    fun facingDirection(): Direction = Direction.byName(facing) ?: Direction.SOUTH

    fun isAlly(player: Player): Boolean = team.isNotEmpty() && (player.team?.name ?: "") == team

    fun damage(amount: Int) {
        if (amount <= 0 || isRemoved || buildingId.isEmpty() || health <= 0) return
        health -= amount
        if (health < 0) health = 0
        setChanged()
        if (health == 0) destroy()
    }

    fun damageFromMonster(attackerTeam: String, amount: Int) {
        if (attackerTeam == team) return
        damage(amount)
    }

    fun destroy() {
        HealthBars.discard(this)
        val world = level as? ServerLevel ?: return
        val parts = partPositions.toList()
        partPositions.clear()
        for (pos in parts) {
            val state = world.getBlockState(pos)
            if (state.block is SiteStructureBlock || state.block is SitePartBlock) {
                world.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL)
            }
        }
        BuildingStructure.clearLegacy(world, blockPos)
        if (world.getBlockState(blockPos).block is SiteCoreBlock) {
            world.setBlock(blockPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL)
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
        if (level !is ServerLevel) return false
        destroy()
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
        val world = level as? ServerLevel ?: return false
        val style = BuildingStyles.rejection(world, blockPos, definition, buildingLevel + 1, facingDirection())
        if (style != null) {
            player.sendSystemMessage(Component.literal(style))
            return false
        }
        InventoryCosts.take(player, storage, next.costs)
        val oldMax = definition.maxHealth(buildingLevel)
        buildingLevel += 1
        val newMax = definition.maxHealth(buildingLevel)
        val delta = newMax - oldMax
        health = if (delta >= 0) health + delta else minOf(health, newMax)
        sanitizeMinerals(definition)
        val previous = partPositions.toList()
        partPositions.clear()
        partPositions.addAll(BuildingStyles.apply(world, blockPos, definition, buildingLevel, facingDirection(), previous))
        setChanged()
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
            Containers.dropContents(world, pos, storage as Container)
            storage.clearContent()
            dropModule(world, pos, reinforce)
            dropModule(world, pos, reinforceTwo)
            reinforce = ItemStack.EMPTY
            reinforceTwo = ItemStack.EMPTY
        }
        super.preRemoveSideEffects(pos, state)
    }

    override fun saveAdditional(output: ValueOutput) {
        super.saveAdditional(output)
        output.putString("Building", buildingId)
        output.putInt("Level", buildingLevel)
        output.putInt("Health", health)
        output.putString("Team", team)
        output.putString("Facing", facing)
        output.putInt("Age", age)
        output.putInt("ActionAge", actionAge)
        output.putString("HealthDisplay", healthDisplayId?.toString() ?: "")
        output.putString("ReinforceMineral", reinforceMineral)
        output.putString("ReinforceTwoMineral", reinforceTwoMineral)
        output.store("Reinforce", ItemStack.OPTIONAL_CODEC, reinforce)
        output.store("ReinforceTwo", ItemStack.OPTIONAL_CODEC, reinforceTwo)
        output.store("Parts", Codec.LONG.listOf(), partPositions.map { it.asLong() })
        output.store("MineralAges", Codec.unboundedMap(Codec.STRING, Codec.INT), mineralAges)
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
        facing = input.getStringOr("Facing", Direction.SOUTH.serializedName)
        age = input.getIntOr("Age", 0)
        actionAge = input.getIntOr("ActionAge", 0)
        healthDisplayId = input.getStringOr("HealthDisplay", "").takeIf { it.isNotEmpty() }?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        reinforceMineral = input.getStringOr("ReinforceMineral", "")
        reinforceTwoMineral = input.getStringOr("ReinforceTwoMineral", "")
        reinforce = input.read("Reinforce", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY)
        reinforceTwo = input.read("ReinforceTwo", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY)
        partPositions.clear()
        input.read("Parts", Codec.LONG.listOf()).orElse(emptyList()).forEach { partPositions.add(BlockPos.of(it)) }
        mineralAges.clear()
        mineralAges.putAll(input.read("MineralAges", Codec.unboundedMap(Codec.STRING, Codec.INT)).orElse(emptyMap()))
        val items = input.read("Storage", ItemStack.OPTIONAL_CODEC.listOf()).orElse(emptyList())
        if (items != null) {
            items.forEachIndexed { slot, stack ->
                if (slot < storage.containerSize) storage.setItem(slot, stack)
            }
        }
    }

    fun productionLines(): List<Pair<String, Boolean>> {
        val definition = Buildings.byId(buildingId) ?: return emptyList()
        return definition.minerals().map { mineral ->
            val interval = intervalOf(definition, mineral)
            val unlocked = buildingLevel >= mineral.minLevel
            val text = when {
                !unlocked -> "${mineral.name}：${mineral.describeBase()}"
                interval == null -> "${mineral.name}：不生成"
                else -> "${mineral.name}：每 ${secondsText(interval)} ${mineral.count} 个"
            }
            text to (unlocked && interval != null)
        }
    }

    fun generatableMinerals(definition: BuildingDefinition): List<MineralOutput> =
        definition.minerals().filter { buildingLevel >= it.minLevel && itemKey(it.item) != reinforceTwoMineral }

    fun choicesFor(slot: ModuleSlot, definition: BuildingDefinition): List<MineralOutput> {
        val unlocked = definition.minerals().filter { buildingLevel >= it.minLevel }
        return when (slot) {
            ModuleSlot.PRIMARY -> unlocked.filter { itemKey(it.item) != reinforceTwoMineral }
            ModuleSlot.SECONDARY -> if (unlocked.size <= 1) emptyList() else unlocked
        }
    }

    fun setMineral(slot: ModuleSlot, item: Item) {
        val definition = Buildings.byId(buildingId) ?: return
        val key = itemKey(item)
        when (slot) {
            ModuleSlot.PRIMARY -> reinforceMineral = key
            ModuleSlot.SECONDARY -> reinforceTwoMineral = key
        }
        sanitizeMinerals(definition)
        setChanged()
    }

    fun moduleRefusal(slot: ModuleSlot, stack: ItemStack): String? {
        val definition = Buildings.byId(buildingId) ?: return "这座建筑不能装模块"
        if (!definition.acceptsModules) return "这座建筑不能装模块"
        if (stack.isEmpty || !stack.`is`(slot.item)) return "这个槽不能放这种模块"
        return null
    }

    fun exchangeModule(slot: ModuleSlot, incoming: ItemStack): ItemStack {
        val previous = moduleStack(slot).copy()
        setModule(slot, incoming.copyWithCount(1))
        return previous
    }

    fun mineralLabel(slot: ModuleSlot): String {
        val key = when (slot) {
            ModuleSlot.PRIMARY -> reinforceMineral
            ModuleSlot.SECONDARY -> reinforceTwoMineral
        }
        if (key.isEmpty()) return "未选择"
        val definition = Buildings.byId(buildingId) ?: return "未选择"
        return definition.minerals().firstOrNull { itemKey(it.item) == key }?.name ?: "未选择"
    }

    fun takeModule(slot: ModuleSlot, player: ServerPlayer): String? {
        val stack = moduleStack(slot)
        if (stack.isEmpty) return null
        if (!player.inventory.add(stack.copy())) return "背包放不下"
        setModule(slot, ItemStack.EMPTY)
        return null
    }

    fun moduleStack(slot: ModuleSlot): ItemStack = when (slot) {
        ModuleSlot.PRIMARY -> reinforce
        ModuleSlot.SECONDARY -> reinforceTwo
    }

    private fun setModule(slot: ModuleSlot, stack: ItemStack) {
        val definition = Buildings.byId(buildingId)
        when (slot) {
            ModuleSlot.PRIMARY -> {
                reinforce = stack
                if (stack.isEmpty) reinforceMineral = ""
            }
            ModuleSlot.SECONDARY -> {
                reinforceTwo = stack
                if (stack.isEmpty) reinforceTwoMineral = ""
            }
        }
        if (definition != null) sanitizeMinerals(definition)
        setChanged()
    }

    private fun intervalOf(definition: BuildingDefinition, mineral: MineralOutput): Int? {
        if (buildingLevel < mineral.minLevel) return null
        val key = itemKey(mineral.item)
        if (reinforceTwo.isEmpty.not() && reinforceTwoMineral == key) return null
        var multiplier = 1
        if (reinforce.isEmpty.not() && reinforceMineral == key) multiplier *= 2
        val othersBoosted = reinforceTwo.isEmpty.not() && reinforceTwoMineral.isNotEmpty() && reinforceTwoMineral != key
        if (othersBoosted) multiplier *= 2
        return maxOf(1, mineral.baseTicks / multiplier)
    }

    private fun sanitizeMinerals(definition: BuildingDefinition) {
        val unlocked = definition.minerals().filter { buildingLevel >= it.minLevel }.map { itemKey(it.item) }.toSet()
        if (reinforceTwoMineral.isNotEmpty() && reinforceTwoMineral !in unlocked) reinforceTwoMineral = ""
        if (unlocked.size <= 1) reinforceTwoMineral = ""
        if (reinforceMineral.isNotEmpty() && (reinforceMineral !in unlocked || reinforceMineral == reinforceTwoMineral)) {
            reinforceMineral = ""
        }
    }

    private fun clearModules() {
        reinforce = ItemStack.EMPTY
        reinforceTwo = ItemStack.EMPTY
        reinforceMineral = ""
        reinforceTwoMineral = ""
    }

    private fun dropModule(world: Level, pos: BlockPos, stack: ItemStack) {
        if (!stack.isEmpty) Containers.dropItemStack(world, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, stack.copy())
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

        fun itemKey(item: Item): String = BuiltInRegistries.ITEM.getKey(item).toString()

        fun secondsText(ticks: Int): String {
            val whole = ticks / 20
            val remainder = ticks % 20
            if (remainder == 0) return "$whole 秒"
            val hundredths = remainder * 5
            val fraction = hundredths.toString().padStart(2, '0').trimEnd('0')
            return "$whole.$fraction 秒"
        }
    }
}

enum class ModuleSlot(val item: Item) {
    PRIMARY(shina.wallwarRebirth.item.ModItems.REINFORCE),
    SECONDARY(shina.wallwarRebirth.item.ModItems.REINFORCE_TWO),
}

private fun MineralOutput.describeBase(): String = "每 ${SiteCoreBlockEntity.secondsText(baseTicks)} $count 个"
