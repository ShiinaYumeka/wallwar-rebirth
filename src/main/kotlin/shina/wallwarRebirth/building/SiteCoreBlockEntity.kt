package shina.wallwarRebirth.building

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.ChatFormatting
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.Container
import net.minecraft.world.Containers
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LevelEvent
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import shina.wallwarRebirth.match.Match
import shina.wallwarRebirth.match.MatchPlacement
import shina.wallwarRebirth.match.MatchRules
import shina.wallwarRebirth.monster.SummonedUnit
import shina.wallwarRebirth.shop.TeamWarehouses
import shina.wallwarRebirth.util.playNotifySound
import shina.wallwarRebirth.util.showActionBar
import shina.wallwarRebirth.util.showTitle
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

class SiteCoreBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(ModSites.TYPE, pos, state), CrossbowSite {
    override var buildingId: String = ""
    var buildingLevel: Int = 0
    var health: Int = 0
    var team: String = ""
    var facing: String = Direction.SOUTH.serializedName
    var age: Int = 0
    var fallAge: Int = 0
    var actionAge: Int = 0
    var producedMonster: String = "zombie"
    var marching: Boolean = true
    var eggReleaseAge: Int = 0
    var healthDisplayId: UUID? = null
    override var crossbowId: UUID? = null
    var reinforce: ItemStack = ItemStack.EMPTY
    var reinforceMineral: String = ""
    var reinforceTwo: ItemStack = ItemStack.EMPTY
    var reinforceTwoMineral: String = ""
    var diffuse: ItemStack = ItemStack.EMPTY
    var gunner: UUID? = null
    var healAge: Int = 0
    var releaseCount: Int = 0
    var lastHurtAge: Int = -1
    var wallHealedGameTime: Long = -1L
    var dripAge: Int = 0
    val barracksStock: MutableList<String> = ArrayList()
    val dripQueue: MutableList<String> = ArrayList()
    private var reflecting = false
    private var relocating = false
    val partPositions: MutableList<BlockPos> = ArrayList()
    private val coveredBelow: MutableList<CoveredBlock> = ArrayList()
    private val mineralAges: MutableMap<String, Int> = HashMap()
    val storage: SimpleContainer = SimpleContainer(STORAGE_SIZE).also { container ->
        container.addListener { setChanged() }
    }
    val barracksMaterials: SimpleContainer = SimpleContainer(BARRACKS_MATERIALS).also { container ->
        container.addListener { setChanged() }
    }
    val barracksEggs: SimpleContainer = SimpleContainer(BARRACKS_EGGS).also { container ->
        container.addListener { setChanged() }
    }
    private val deferredDrops = ArrayList<ItemStack>()

    fun serverTick(level: ServerLevel) {
        if (deferredDrops.isNotEmpty()) {
            val pos = blockPos
            for (stack in deferredDrops) {
                Containers.dropItemStack(level, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, stack)
            }
            deferredDrops.clear()
        }
        val definition = Buildings.byId(buildingId) ?: return
        if (buildingLevel <= 0) return
        fallAge++
        if (fallAge >= MatchRules.BUILDING_FALL_TICKS) {
            fallAge = 0
            setChanged()
            if (descend(level, definition)) return
        }
        if (buildingId == Buildings.TOWER.id) TowerCrossbows.ensure(this, level)
        if (Match.frozen(level)) {
            HealthBars.tick(this, level, definition)
            return
        }
        age++
        var produced = false
        for (mineral in definition.minerals()) {
            val interval = intervalOf(definition, mineral) ?: continue
            val key = itemKey(mineral.item)
            val next = (mineralAges[key] ?: 0) + 1
            if (next >= interval) {
                mineralAges[key] = 0
                produced = true
                val hit = rolled(level, mineral)
                if (hit) {
                    val stack = ItemStack(mineral.item, mineral.count)
                    if (buildingId == Buildings.BASE.id) TeamWarehouses.acceptAll(team, stack) else offer(stack)
                }
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
        if (definition.category == BuildingCategory.WONDER && wonderOnMap(world)) return "全图已经有一座奇观"
        val arena = MatchPlacement.rejection(world, blockPos, definition, facing, teamName)
        if (arena != null) return arena
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
        fallAge = 0
        actionAge = 0
        producedMonster = "zombie"
        marching = true
        eggReleaseAge = 0
        Containers.dropContents(world, blockPos, barracksMaterials as Container)
        Containers.dropContents(world, blockPos, barracksEggs as Container)
        barracksMaterials.clearContent()
        barracksEggs.clearContent()
        mineralAges.clear()
        clearModules()
        partPositions.clear()
        partPositions.addAll(
            BuildingStyles.apply(world, blockPos, definition, buildingLevel, facing, emptyList(), ::rememberCovered),
        )
        setChanged()
        return null
    }

    override fun facingDirection(): Direction = Direction.byName(facing) ?: Direction.SOUTH

    override fun crossbowPos(): BlockPos = blockPos

    override fun crossbowLevel(): Level? = level

    fun isAlly(player: Player): Boolean = team.isNotEmpty() && (player.team?.name ?: "") == team

    fun encloses(pos: BlockPos): Boolean {
        var minX = blockPos.x
        var minY = blockPos.y
        var minZ = blockPos.z
        var maxX = minX
        var maxY = minY
        var maxZ = minZ
        for (part in partPositions) {
            minX = min(minX, part.x)
            minY = min(minY, part.y)
            minZ = min(minZ, part.z)
            maxX = max(maxX, part.x)
            maxY = max(maxY, part.y)
            maxZ = max(maxZ, part.z)
        }
        return pos.x in minX..maxX && pos.y in minY..maxY && pos.z in minZ..maxZ
    }

    fun damage(amount: Int, struck: BlockPos = blockPos, attackerName: String? = null, attacker: LivingEntity? = null) {
        if (amount <= 0 || isRemoved || buildingId.isEmpty() || health <= 0) return
        val world = level as? ServerLevel
        if (world != null) {
            val state = world.getBlockState(struck)
            world.levelEvent(null, LevelEvent.PARTICLES_DESTROY_BLOCK, struck, Block.getId(state))
        }
        val before = health
        health -= amount
        if (health < 0) health = 0
        val dealt = before - health
        if (dealt > 0 && buildingId == Buildings.WALL.id) {
            lastHurtAge = age
            if (!reflecting) {
                reflecting = true
                try {
                    reflect(attacker, dealt)
                } finally {
                    reflecting = false
                }
            }
        }
        if (attackerName != null && buildingId == Buildings.BASE.id) warnBase(attackerName)
        setChanged()
        if (health == 0) {
            if (buildingId == Buildings.BASE.id) Match.onBaseFallen(this)
            destroy()
        }
    }

    private fun reflect(attacker: LivingEntity?, dealt: Int) {
        if (!moduleActive(ModuleSlot.SECONDARY) || attacker == null || !attacker.isAlive || dealt <= 0) return
        if (friendly(attacker)) return
        val world = level as? ServerLevel ?: return
        attacker.hurtServer(world, world.damageSources().thorns(attacker), MatchRules.WALL_THORNS_DAMAGE.toFloat())
    }

    private fun friendly(attacker: LivingEntity): Boolean {
        if (team.isEmpty()) return false
        val other = when (attacker) {
            is Player -> attacker.team?.name ?: ""
            is SummonedUnit -> attacker.summonedTeam
            else -> return false
        }
        return other.isNotEmpty() && other == team
    }

    fun installBase(teamName: String, facing: Direction) {
        buildingId = Buildings.BASE.id
        buildingLevel = 1
        health = Buildings.BASE.maxHealth(1)
        team = teamName
        this.facing = facing.serializedName
        partPositions.clear()
        coveredBelow.clear()
        val world = level as? ServerLevel
        if (world != null) {
            partPositions.addAll(
                BuildingStyles.apply(world, blockPos, Buildings.BASE, 1, facing, emptyList(), ::rememberCovered),
            )
        }
        setChanged()
    }

    private fun wonderOnMap(world: ServerLevel): Boolean =
        SiteCores.all().any { core ->
            core.level === world &&
                !core.isRemoved &&
                core.blockPos != blockPos &&
                Buildings.byId(core.buildingId)?.category == BuildingCategory.WONDER
        }

    fun damageFromMonster(attacker: LivingEntity, attackerTeam: String, amount: Int, struck: BlockPos, attackerName: String) {
        if (attackerTeam == team) return
        damage(amount, struck, attackerName, attacker)
    }

    private fun warnBase(attackerName: String) {
        val world = level as? ServerLevel ?: return
        val text = Component.literal("${attackerName}正在破坏我方核心 剩余生命 $health")
        for (player in world.server.playerList.players) {
            if ((player.team?.name ?: "") != team) continue
            player.showActionBar(text)
            player.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1f, 1f)
        }
    }

    private fun descend(level: ServerLevel, definition: BuildingDefinition): Boolean {
        if (health <= 0 || relocating) return false
        val to = blockPos.below()
        if (level.isOutsideBuildHeight(to)) return false
        val supports = supportCells(level)
        if (supports.isEmpty() || supports.any { level.isOutsideBuildHeight(it) }) return false
        if (!unsupported(level, definition, supports) || !pathClear(level)) return false
        for (pos in supports) {
            if (!level.getBlockState(pos).`is`(BlockTags.PLANKS)) continue
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)
            coveredBelow.removeIf { it.pos == pos }
        }
        val parts = partPositions.toList()
        partPositions.clear()
        for (pos in parts) {
            val state = level.getBlockState(pos)
            if (state.block is SiteStructureBlock || state.block is SitePartBlock) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)
            }
        }
        val from = blockPos
        val state = level.getBlockState(from)
        level.setBlock(to, state, Block.UPDATE_ALL)
        val moved = level.getBlockEntity(to) as? SiteCoreBlockEntity ?: return false
        moved.absorb(this)
        relocating = true
        level.setBlock(from, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)
        moved.partPositions.clear()
        moved.partPositions.addAll(
            BuildingStyles.apply(level, to, definition, moved.buildingLevel, moved.facingDirection(), emptyList(), moved::rememberCovered),
        )
        moved.repositionDisplays(level)
        moved.setChanged()
        return true
    }

    private fun supportCells(level: ServerLevel): List<BlockPos> {
        val definition = Buildings.byId(buildingId) ?: return emptyList()
        val cells = ArrayList<BlockPos>()
        for (column in BuildingFootprints.cells(definition, blockPos, facingDirection())) {
            var lowest = Int.MAX_VALUE
            if (blockPos.x == column.x && blockPos.z == column.z) lowest = blockPos.y
            for (part in partPositions) {
                if (part.x == column.x && part.z == column.z && part.y < lowest) lowest = part.y
            }
            if (lowest == Int.MAX_VALUE) continue
            val under = BlockPos(column.x, lowest - 1, column.z)
            if (level.isOutsideBuildHeight(under)) return emptyList()
            cells.add(under)
        }
        return cells
    }

    private fun pathClear(level: ServerLevel): Boolean {
        val occupied = HashSet<BlockPos>()
        occupied.add(blockPos)
        occupied.addAll(partPositions)
        for (pos in occupied) {
            val under = pos.below()
            if (under in occupied) continue
            val state = level.getBlockState(under)
            if (state.isAir || state.`is`(BlockTags.PLANKS)) continue
            return false
        }
        return true
    }

    private fun unsupported(level: ServerLevel, definition: BuildingDefinition, supports: List<BlockPos>): Boolean {
        if (definition.footprint == Footprint.SQUARE_1 && supports.size == 1) {
            val state = level.getBlockState(supports[0])
            if (state.`is`(BlockTags.PLANKS)) {
                val under = supports[0].below()
                if (level.isOutsideBuildHeight(under)) return false
                val below = level.getBlockState(under)
                return below.isAir || below.`is`(BlockTags.PLANKS)
            }
        }
        return supports.all { pos ->
            val state = level.getBlockState(pos)
            state.isAir || state.`is`(BlockTags.PLANKS)
        }
    }

    private fun absorb(from: SiteCoreBlockEntity) {
        buildingId = from.buildingId
        buildingLevel = from.buildingLevel
        health = from.health
        team = from.team
        facing = from.facing
        age = from.age
        fallAge = 0
        actionAge = from.actionAge
        producedMonster = from.producedMonster
        marching = from.marching
        eggReleaseAge = from.eggReleaseAge
        healthDisplayId = from.healthDisplayId
        crossbowId = from.crossbowId
        reinforce = from.reinforce.copy()
        reinforceMineral = from.reinforceMineral
        reinforceTwo = from.reinforceTwo.copy()
        reinforceTwoMineral = from.reinforceTwoMineral
        diffuse = from.diffuse.copy()
        gunner = from.gunner
        healAge = from.healAge
        releaseCount = from.releaseCount
        lastHurtAge = from.lastHurtAge
        wallHealedGameTime = from.wallHealedGameTime
        dripAge = from.dripAge
        barracksStock.clear()
        barracksStock.addAll(from.barracksStock)
        dripQueue.clear()
        dripQueue.addAll(from.dripQueue)
        coveredBelow.clear()
        coveredBelow.addAll(from.coveredBelow)
        mineralAges.clear()
        mineralAges.putAll(from.mineralAges)
        copyItems(from.storage, storage)
        copyItems(from.barracksMaterials, barracksMaterials)
        copyItems(from.barracksEggs, barracksEggs)
    }

    private fun copyItems(from: SimpleContainer, to: SimpleContainer) {
        for (slot in 0 until to.containerSize) {
            val stack = if (slot < from.containerSize) from.getItem(slot).copy() else ItemStack.EMPTY
            to.setItem(slot, stack)
        }
    }

    private fun repositionDisplays(level: ServerLevel) {
        healthDisplayId?.let { id ->
            val label = level.getEntity(id) as? BuildingHealthLabel ?: return@let
            label.corePos = blockPos
            label.snapTo(blockPos.x + 0.5, blockPos.y + 1.2, blockPos.z + 0.5, 0f, 0f)
        }
        crossbowId?.let { id ->
            val crossbow = level.getEntity(id) as? TowerCrossbow ?: return@let
            crossbow.corePos = blockPos
            crossbow.snapTo(blockPos.x + 0.5, blockPos.y + 1.02, blockPos.z + 0.5, crossbow.yRot, 0f)
        }
    }

    fun destroy() {
        HealthBars.discard(this)
        TowerCrossbows.discard(this)
        val world = level as? ServerLevel ?: return
        BarracksWork.discardStock(this, world)
        val parts = partPositions.toList()
        partPositions.clear()
        for (pos in parts) {
            val state = world.getBlockState(pos)
            if (state.block is SiteStructureBlock || state.block is SitePartBlock) {
                world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)
            }
        }
        BuildingStructure.clearLegacy(world, blockPos)
        restoreCovered(world)
        if (world.getBlockState(blockPos).block is SiteCoreBlock) {
            world.setBlock(blockPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)
        }
    }

    private fun rememberCovered(pos: BlockPos, state: BlockState) {
        if (coveredBelow.any { it.pos == pos }) return
        coveredBelow.add(CoveredBlock(pos, state))
    }

    private fun restoreCovered(world: ServerLevel) {
        if (coveredBelow.isEmpty()) return
        val pending = coveredBelow.toList()
        coveredBelow.clear()
        for (covered in pending) {
            if (world.isOutsideBuildHeight(covered.pos)) continue
            val state = world.getBlockState(covered.pos)
            val cleared = state.isAir ||
                state.block is SiteStructureBlock ||
                state.block is SitePartBlock ||
                state.block is SiteCoreBlock
            if (!cleared) continue
            world.setBlock(
                covered.pos,
                covered.state,
                Block.UPDATE_ALL,
            )
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
        val marks = ArrayList<BlockPos>()
        marks.add(blockPos)
        marks.addAll(partPositions)
        destroy()
        for (pos in marks) {
            world.sendParticles(ParticleTypes.EXPLOSION, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, 1, 0.0, 0.0, 0.0, 0.0)
        }
        return true
    }

    fun tryUpgrade(player: ServerPlayer): Boolean {
        val definition = Buildings.byId(buildingId) ?: return false
        if (buildingLevel >= definition.maxLevel) {
            player.sendSystemMessage(Component.literal("已满级"))
            return false
        }
        val next = definition.upgradeTo(buildingLevel + 1) ?: return false
        val warehouse = TeamWarehouses.of(team)
        if (!InventoryCosts.has(player, storage, warehouse, next.costs)) {
            player.sendSystemMessage(Component.literal("材料不足"))
            return false
        }
        val world = level as? ServerLevel ?: return false
        val style = BuildingStyles.rejection(world, blockPos, definition, buildingLevel + 1, facingDirection())
        if (style != null) {
            player.reportBuilding(style)
            return false
        }
        InventoryCosts.take(player, storage, warehouse, next.costs)
        val oldMax = definition.maxHealth(buildingLevel)
        buildingLevel += 1
        val newMax = definition.maxHealth(buildingLevel)
        val delta = newMax - oldMax
        health = if (delta >= 0) health + delta else minOf(health, newMax)
        sanitizeMinerals(definition)
        val previous = partPositions.toList()
        partPositions.clear()
        partPositions.addAll(
            BuildingStyles.apply(world, blockPos, definition, buildingLevel, facingDirection(), previous, ::rememberCovered),
        )
        setChanged()
        player.containerMenu.broadcastChanges()
        player.sendSystemMessage(Component.literal("${definition.displayName}已升至 $buildingLevel 级"))
        return true
    }

    fun produceCycle() {
        val definition = Buildings.byId(buildingId) ?: return
        if (buildingId != Buildings.BASE.id) return
        val world = level as? ServerLevel ?: return
        for (mineral in definition.minerals()) {
            if (intervalOf(definition, mineral) == null) continue
            if (!rolled(world, mineral)) continue
            TeamWarehouses.acceptAll(team, ItemStack(mineral.item, mineral.count))
        }
        setChanged()
    }

    private fun rolled(world: ServerLevel, mineral: MineralOutput): Boolean = when {
        mineral.chancePercent > 0 -> world.random.nextInt(100) < mineral.chancePercent
        mineral.chance <= 1 -> true
        else -> world.random.nextInt(mineral.chance) == 0
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
        if (relocating) {
            super.preRemoveSideEffects(pos, state)
            return
        }
        val world = level
        if (world != null) {
            val server = world as? ServerLevel
            if (server != null) {
                BarracksWork.discardStock(this, server)
                restoreCovered(server)
                CannonCrew.evict(this, server)
            }
            Containers.dropContents(world, pos, storage as Container)
            storage.clearContent()
            Containers.dropContents(world, pos, barracksMaterials as Container)
            Containers.dropContents(world, pos, barracksEggs as Container)
            barracksMaterials.clearContent()
            barracksEggs.clearContent()
            dropModule(world, pos, reinforce)
            dropModule(world, pos, reinforceTwo)
            dropModule(world, pos, diffuse)
            reinforce = ItemStack.EMPTY
            reinforceTwo = ItemStack.EMPTY
            diffuse = ItemStack.EMPTY
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
        output.putInt("FallAge", fallAge)
        output.putInt("ActionAge", actionAge)
        output.putString("ProducedMonster", producedMonster)
        output.putBoolean("Marching", marching)
        output.putInt("EggReleaseAge", eggReleaseAge)
        output.putString("HealthDisplay", healthDisplayId?.toString() ?: "")
        output.putString("Crossbow", crossbowId?.toString() ?: "")
        output.putString("ReinforceMineral", reinforceMineral)
        output.putString("ReinforceTwoMineral", reinforceTwoMineral)
        output.store("Reinforce", ItemStack.OPTIONAL_CODEC, reinforce)
        output.store("ReinforceTwo", ItemStack.OPTIONAL_CODEC, reinforceTwo)
        output.store("Diffuse", ItemStack.OPTIONAL_CODEC, diffuse)
        output.putString("Gunner", gunner?.toString() ?: "")
        output.putInt("HealAge", healAge)
        output.putInt("ReleaseCount", releaseCount)
        output.putInt("LastHurtAge", lastHurtAge)
        output.putLong("WallHealedGameTime", wallHealedGameTime)
        output.putInt("DripAge", dripAge)
        output.store("BarracksStock", Codec.STRING.listOf(), barracksStock.toList())
        output.store("DripQueue", Codec.STRING.listOf(), dripQueue.toList())
        output.store("Parts", Codec.LONG.listOf(), partPositions.map { it.asLong() })
        output.store("CoveredBelow", CoveredBlock.CODEC.listOf(), coveredBelow.toList())
        output.store("MineralAges", Codec.unboundedMap(Codec.STRING, Codec.INT), mineralAges)
        val items = (0 until storage.containerSize).map { storage.getItem(it) }
        output.store("Storage", ItemStack.OPTIONAL_CODEC.listOf(), items)
        output.store(
            "BarracksMaterials",
            ItemStack.OPTIONAL_CODEC.listOf(),
            (0 until barracksMaterials.containerSize).map { barracksMaterials.getItem(it) },
        )
        output.store(
            "BarracksEggs",
            ItemStack.OPTIONAL_CODEC.listOf(),
            (0 until barracksEggs.containerSize).map { barracksEggs.getItem(it) },
        )
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
        fallAge = input.getIntOr("FallAge", 0)
        actionAge = input.getIntOr("ActionAge", 0)
        producedMonster = input.getStringOr("ProducedMonster", "zombie")
        marching = input.getBooleanOr("Marching", true)
        eggReleaseAge = input.getIntOr("EggReleaseAge", 0)
        healthDisplayId = input.getStringOr("HealthDisplay", "").takeIf { it.isNotEmpty() }?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        crossbowId = input.getStringOr("Crossbow", "").takeIf { it.isNotEmpty() }?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        reinforceMineral = input.getStringOr("ReinforceMineral", "")
        reinforceTwoMineral = input.getStringOr("ReinforceTwoMineral", "")
        reinforce = input.read("Reinforce", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY) ?: ItemStack.EMPTY
        reinforceTwo = input.read("ReinforceTwo", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY) ?: ItemStack.EMPTY
        diffuse = input.read("Diffuse", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY) ?: ItemStack.EMPTY
        gunner = input.getStringOr("Gunner", "").takeIf { it.isNotEmpty() }?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        healAge = input.getIntOr("HealAge", 0)
        releaseCount = input.getIntOr("ReleaseCount", 0)
        lastHurtAge = input.getIntOr("LastHurtAge", -1)
        wallHealedGameTime = input.getLongOr("WallHealedGameTime", -1L)
        dripAge = input.getIntOr("DripAge", 0)
        barracksStock.clear()
        readIds(input, "BarracksStock", barracksStock)
        dripQueue.clear()
        readIds(input, "DripQueue", dripQueue)
        partPositions.clear()
        val parts = input.read("Parts", Codec.LONG.listOf()).orElse(emptyList()) ?: emptyList()
        for (packed in parts) {
            if (packed != null) partPositions.add(BlockPos.of(packed))
        }
        coveredBelow.clear()
        val covered = input.read("CoveredBelow", CoveredBlock.CODEC.listOf()).orElse(emptyList()) ?: emptyList()
        for (block in covered) {
            if (block != null) coveredBelow.add(block)
        }
        mineralAges.clear()
        val ages = input.read("MineralAges", Codec.unboundedMap(Codec.STRING, Codec.INT)).orElse(emptyMap()) ?: emptyMap()
        for ((name, ticks) in ages) {
            if (name != null && ticks != null) mineralAges[name] = ticks
        }
        val items = input.read("Storage", ItemStack.OPTIONAL_CODEC.listOf()).orElse(emptyList())
        if (items != null) {
            items.forEachIndexed { slot, stack ->
                if (slot < storage.containerSize) storage.setItem(slot, stack)
            }
        }
        readContainer(input, "BarracksMaterials", barracksMaterials)
        readContainer(input, "BarracksEggs", barracksEggs)
    }

    fun productionLines(): List<Pair<String, Boolean>> {
        val definition = Buildings.byId(buildingId) ?: return emptyList()
        return definition.minerals().map { mineral ->
            val interval = intervalOf(definition, mineral)
            val unlocked = buildingLevel >= mineral.minLevel
            val text = when {
                !unlocked -> "${mineral.name}：${mineral.describeBase()}"
                interval == null -> "${mineral.name}：不生成"
                else -> "${mineral.name}：${mineral.describe(interval)}"
            }
            text to (unlocked && interval != null)
        }
    }

    fun generatableMinerals(definition: BuildingDefinition): List<MineralOutput> =
        definition.minerals().filter { buildingLevel >= it.minLevel }

    fun choicesFor(slot: ModuleSlot, definition: BuildingDefinition): List<MineralOutput> {
        val unlocked = definition.minerals().filter { buildingLevel >= it.minLevel }
        return when (slot) {
            ModuleSlot.PRIMARY -> unlocked
            ModuleSlot.SECONDARY -> emptyList()
            ModuleSlot.DIFFUSION -> emptyList()
        }
    }

    fun chooseReleaseCount(count: Int) {
        if (count !in MatchRules.BARRACKS_RELEASE_MIN..MatchRules.BARRACKS_RELEASE_MAX) return
        releaseCount = count
        setChanged()
    }

    fun setMineral(slot: ModuleSlot, item: Item) {
        val definition = Buildings.byId(buildingId) ?: return
        val key = itemKey(item)
        when (slot) {
            ModuleSlot.PRIMARY -> reinforceMineral = key
            ModuleSlot.SECONDARY -> reinforceTwoMineral = key
            ModuleSlot.DIFFUSION -> return
        }
        sanitizeMinerals(definition)
        setChanged()
    }

    fun moduleRefusal(slot: ModuleSlot, stack: ItemStack): String? {
        val definition = Buildings.byId(buildingId) ?: return "这座建筑不能装模块"
        if (!definition.acceptsModules) return "这座建筑不能装模块"
        if (slot == ModuleSlot.DIFFUSION && !definition.acceptsDiffusion) return "这座建筑不能装扩散模块"
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
            ModuleSlot.DIFFUSION -> ""
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

    fun moduleActive(slot: ModuleSlot): Boolean {
        if (!moduleStack(slot).isEmpty) return true
        if (buildingId != Buildings.WALL.id || slot == ModuleSlot.DIFFUSION) return false
        return borrowed(slot)
    }

    fun moduleStack(slot: ModuleSlot): ItemStack = when (slot) {
        ModuleSlot.PRIMARY -> reinforce
        ModuleSlot.SECONDARY -> reinforceTwo
        ModuleSlot.DIFFUSION -> diffuse
    }

    private fun borrowed(slot: ModuleSlot): Boolean {
        val origin = Vec3.atCenterOf(blockPos)
        for (other in SiteCores.all()) {
            if (other === this || other.isRemoved || other.health <= 0 || other.level !== level) continue
            if (other.buildingId != Buildings.WALL.id) continue
            if (team.isEmpty() || other.team != team) continue
            if (other.diffuse.isEmpty || other.moduleStack(slot).isEmpty) continue
            if (origin.distanceTo(Vec3.atCenterOf(other.blockPos)) > MatchRules.DIFFUSION_RANGE) continue
            return true
        }
        return false
    }

    private fun setModule(slot: ModuleSlot, stack: ItemStack) {
        val definition = Buildings.byId(buildingId)
        when (slot) {
            ModuleSlot.PRIMARY -> {
                reinforce = stack
                if (stack.isEmpty) reinforceMineral = ""
            }
            ModuleSlot.SECONDARY -> {
                val had = !reinforceTwo.isEmpty
                reinforceTwo = stack
                if (stack.isEmpty) reinforceTwoMineral = ""
                if (!stack.isEmpty && buildingId == Buildings.BASE.id) healAge = 0
                if (had && buildingId == Buildings.BARRACKS.id) {
                    val world = level as? ServerLevel
                    if (world != null) BarracksWork.dumpStock(this, world)
                }
            }
            ModuleSlot.DIFFUSION -> {
                diffuse = stack
                if (buildingId == Buildings.BARRACKS.id) healAge = 0
                if (buildingId == Buildings.BASE.id && !stack.isEmpty) {
                    val world = level as? ServerLevel
                    if (world != null) shina.wallwarRebirth.match.PortableShop.tick(world.server)
                }
            }
        }
        if (definition != null) sanitizeMinerals(definition)
        setChanged()
    }

    private fun intervalOf(definition: BuildingDefinition, mineral: MineralOutput): Int? {
        if (buildingLevel < mineral.minLevel) return null
        val key = itemKey(mineral.item)
        val multiplier = if (reinforce.isEmpty.not() && reinforceMineral == key) 2 else 1
        return maxOf(MatchRules.MIN_PRODUCTION_TICKS, mineral.baseTicks / multiplier)
    }

    private fun sanitizeMinerals(definition: BuildingDefinition) {
        if (buildingId == Buildings.BASE.id) reinforceTwoMineral = ""
        val unlocked = definition.minerals().filter { buildingLevel >= it.minLevel }.map { itemKey(it.item) }.toSet()
        if (reinforceMineral.isNotEmpty() && reinforceMineral !in unlocked) reinforceMineral = ""
    }

    private fun clearModules() {
        reinforce = ItemStack.EMPTY
        reinforceTwo = ItemStack.EMPTY
        diffuse = ItemStack.EMPTY
        reinforceMineral = ""
        reinforceTwoMineral = ""
        healAge = 0
        releaseCount = 0
        lastHurtAge = -1
        dripAge = 0
        barracksStock.clear()
        dripQueue.clear()
    }

    private fun readIds(input: ValueInput, key: String, into: MutableList<String>) {
        val ids = input.read(key, Codec.STRING.listOf()).orElse(emptyList()) ?: return
        for (id in ids) {
            if (id != null) into.add(id)
        }
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

    private fun readContainer(input: ValueInput, key: String, container: SimpleContainer) {
        container.clearContent()
        val items = input.read(key, ItemStack.OPTIONAL_CODEC.listOf()).orElse(emptyList()) ?: return
        items.forEachIndexed { slot, stack ->
            if (stack.isEmpty) return@forEachIndexed
            if (slot < container.containerSize) container.setItem(slot, stack)
            else deferredDrops.add(stack.copy())
        }
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
        const val BARRACKS_MATERIALS = 16
        const val BARRACKS_EGGS = 12

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

data class CoveredBlock(val pos: BlockPos, val state: BlockState) {
    companion object {
        val CODEC: Codec<CoveredBlock> = RecordCodecBuilder.create { instance ->
            instance.group(
                BlockPos.CODEC.fieldOf("pos").forGetter { it.pos },
                BlockState.CODEC.fieldOf("state").forGetter { it.state },
            ).apply(instance, ::CoveredBlock)
        }
    }
}

fun ServerPlayer.reportBuilding(message: String) {
    if (message == BuildingStyles.BLOCKED) {
        showTitle(Component.literal(message).withStyle(ChatFormatting.RED))
        playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1f, 1f)
        return
    }
    sendSystemMessage(Component.literal(message))
}

enum class ModuleSlot(val item: Item) {
    PRIMARY(shina.wallwarRebirth.item.ModItems.REINFORCE),
    SECONDARY(shina.wallwarRebirth.item.ModItems.REINFORCE_TWO),
    DIFFUSION(shina.wallwarRebirth.item.ModItems.DIFFUSION),
}

private fun MineralOutput.describeBase(): String = describe(baseTicks)

private fun MineralOutput.describe(ticks: Int): String {
    val every = "每 ${SiteCoreBlockEntity.secondsText(ticks)}"
    val odds = when {
        chancePercent > 0 -> "有 $chancePercent% 概率 "
        chance <= 1 -> ""
        else -> "有 1/$chance 概率 "
    }
    return if (odds.isEmpty()) "$every $count 个" else "$every $odds$count 个"
}
