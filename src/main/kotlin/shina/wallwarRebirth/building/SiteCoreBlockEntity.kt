package shina.wallwarRebirth.building

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
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
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import shina.wallwarRebirth.match.Match
import shina.wallwarRebirth.match.MatchPlacement
import shina.wallwarRebirth.profession.ProfessionEffects
import shina.wallwarRebirth.rules.MatchRules
import shina.wallwarRebirth.shop.match.TeamWarehouses
import shina.wallwarRebirth.util.playNotifySound
import shina.wallwarRebirth.util.showTitle
import java.util.*
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
    var weaponCooldown: Int = 0
    var producedMonster: String = "zombie"
    var marching: Boolean = true
    var holding: Boolean = false
    var holdEpoch: Int = 0
    var holdAnchor: BlockPos? = null
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
    internal var reflecting = false
    internal var relocating = false
    internal var dropModulesOnRemove = false
    internal var commandPost = false
    internal var commandOwner: UUID? = null
    internal var echoing = false
    val partPositions: MutableList<BlockPos> = ArrayList()
    internal val coveredBelow: MutableList<CoveredBlock> = ArrayList()
    internal val mineralAges: MutableMap<String, Int> = HashMap()
    var crystalFurnaceOn: Boolean = false
    var crystalFurnaceTicks: Int = 0
    var crystalFurnaceIron: Int = 0
    var crystalFurnaceCopper: Int = 0
    val storage: SimpleContainer = SimpleContainer(STORAGE_SIZE).also { container ->
        container.addListener { setChanged() }
    }
    val barracksMaterials: SimpleContainer = SimpleContainer(BARRACKS_MATERIALS).also { container ->
        container.addListener { setChanged() }
    }
    val barracksEggs: SimpleContainer = SimpleContainer(BARRACKS_EGGS).also { container ->
        container.addListener { setChanged() }
    }
    internal val deferredDrops = ArrayList<ItemStack>()

    fun serverTick(level: ServerLevel) {
        if (deferredDrops.isNotEmpty()) {
            val pos = blockPos
            for (stack in deferredDrops) {
                Containers.dropItemStack(level, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, stack)
            }
            deferredDrops.clear()
        }
        val definition = Buildings.byId(buildingId) ?: return
        if (buildingId == Buildings.FARMLAND.id) FarmlandWork.ensureRealParts(this, level)
        BuildingStyles.ensureRealParts(this, level)
        if (buildingLevel <= 0) return
        fallAge++
        if (fallAge >= MatchRules.BUILDING_FALL_TICKS) {
            fallAge = 0
            setChanged()
            if (descend(level, definition)) return
        }
        if (buildingId == Buildings.TOWER.id) TowerCrossbows.ensure(this, level)
        CrystalFurnaceWork.tick(this, level)
        if (Match.frozen(level)) {
            HealthBars.tick(this, level, definition)
            return
        }
        age++
        var produced = false
        for (mineral in definition.minerals()) {
            if (buildingId == Buildings.MINERAL_REACTOR.id && team.isEmpty()) continue
            val interval = intervalOf(mineral) ?: continue
            val key = itemKey(mineral.item)
            val next = (mineralAges[key] ?: 0) + 1
            if (next >= interval) {
                mineralAges[key] = 0
                produced = true
                val hit = rolled(level, mineral)
                if (hit) {
                    val stack = ItemStack(mineral.item, mineral.count)
                    if (buildingId == Buildings.BASE.id || buildingId == Buildings.MINERAL_REACTOR.id) {
                        TeamWarehouses.acceptAll(team, stack)
                    } else {
                        offer(stack)
                    }
                }
            } else {
                mineralAges[key] = next
            }
        }
        if (produced || age % 20 == 0) setChanged()
        BuildingCombat.tick(this, level)
        HealthBars.tick(this, level, definition)
    }

    fun assign(definition: BuildingDefinition, replace: Boolean, teamName: String, facing: Direction, raised: Boolean = false): String? {
        if (!replace && buildingId.isNotEmpty()) return "已经选定"
        val world = level as? ServerLevel ?: return "这个位置不能放置"
        if (definition.category == BuildingCategory.WONDER && wonderOnMap(world)) return "全图已经有一座奇观"
        if (definition.teamUnique && SuperWeapons.placed(world, teamName, definition.id, this)) {
            return "本队已有一座${definition.displayName}"
        }
        val lift = if (raised) 0 else BuildingStyles.coreLift(world, definition, 1, facing)
        val origin = if (lift > 0) blockPos.above(lift) else blockPos
        if (world.isOutsideBuildHeight(origin)) return "这个位置不能放置"
        val arena = MatchPlacement.rejection(world, origin, definition, facing, teamName)
        if (arena != null) return arena
        val footprint = BuildingFootprints.rejection(world, origin, definition, facing)
        if (footprint != null) return footprint
        val style = BuildingStyles.rejection(
            world,
            origin,
            definition,
            1,
            facing,
            except = if (lift > 0) blockPos else null,
        )
        if (style != null) return style
        if (lift > 0) {
            val moved = raiseCore(world, lift) ?: return "这个位置不能放置"
            val error = moved.assign(definition, replace, teamName, facing, raised = true)
            if (error != null) {
                world.setBlock(moved.blockPos, Blocks.AIR.defaultBlockState(), CLEAR_MOVED)
                return error
            }
            BuildingPlacement.settle(world, moved, lift)
            return null
        }
        buildingId = definition.id
        buildingLevel = 1
        health = definition.maxHealth(1)
        team = teamName
        this.facing = facing.serializedName
        age = 0
        fallAge = 0
        actionAge = BuildingCombat.initialActionAge(definition)
        weaponCooldown = if (definition.teamUnique) SuperWeapons.cooldownTicks(definition, 1) else 0
        producedMonster = defaultMonster(definition)
        marching = true
        holding = false
        holdEpoch = 0
        holdAnchor = null
        eggReleaseAge = 0
        Containers.dropContents(world, blockPos, barracksMaterials as Container)
        Containers.dropContents(world, blockPos, barracksEggs as Container)
        barracksMaterials.clearContent()
        barracksEggs.clearContent()
        mineralAges.clear()
        CrystalFurnaceWork.reset(this)
        clearModules()
        partPositions.clear()
        partPositions.addAll(
            BuildingStyles.apply(world, blockPos, definition, buildingLevel, facing, emptyList(), { pos, state -> rememberCovered(pos, state) }),
        )
        setChanged()
        if (!raised) BuildingPlacement.settle(world, this, 0)
        return null
    }

    private fun raiseCore(world: ServerLevel, lift: Int): SiteCoreBlockEntity? {
        val to = blockPos.above(lift)
        if (world.isOutsideBuildHeight(to)) return null
        val carried = world.getBlockState(blockPos)
        if (carried.block !is SiteCoreBlock) return null
        val occupant = world.getBlockState(to)
        if (!occupant.isAir && !BuildingStyles.isNonSolid(world, to)) return null
        world.setBlock(to, carried, Block.UPDATE_ALL)
        world.setBlock(blockPos, Blocks.AIR.defaultBlockState(), CLEAR_MOVED)
        return world.getBlockEntity(to) as? SiteCoreBlockEntity
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
        storedDamage(amount, struck, attackerName, attacker)
    }



    fun installBase(teamName: String, facing: Direction, raised: Boolean = false) {
        val world = level as? ServerLevel
        if (!raised && world != null) {
            val lift = BuildingStyles.coreLift(world, Buildings.BASE, 1, facing)
            if (lift > 0) {
                val moved = raiseCore(world, lift)
                if (moved != null) {
                    moved.installBase(teamName, facing, raised = true)
                    BuildingPlacement.settle(world, moved, lift)
                    return
                }
            }
        }
        buildingId = Buildings.BASE.id
        buildingLevel = 1
        health = Buildings.BASE.maxHealth(1)
        team = teamName
        this.facing = facing.serializedName
        partPositions.clear()
        coveredBelow.clear()
        if (world != null) {
            partPositions.addAll(
                BuildingStyles.apply(world, blockPos, Buildings.BASE, 1, facing, emptyList(), { pos, state -> rememberCovered(pos, state) }),
            )
        }
        setChanged()
        if (!raised) {
            val settled = level as? ServerLevel
            if (settled != null) BuildingPlacement.settle(settled, this, 0)
        }
    }

    fun installMineralReactor(facing: Direction, raised: Boolean = false) {
        val world = level as? ServerLevel
        if (!raised && world != null) {
            val lift = BuildingStyles.coreLift(world, Buildings.MINERAL_REACTOR, 3, facing)
            if (lift > 0) {
                val moved = raiseCore(world, lift)
                if (moved != null) {
                    moved.installMineralReactor(facing, raised = true)
                    BuildingPlacement.settle(world, moved, lift)
                    return
                }
            }
        }
        buildingId = Buildings.MINERAL_REACTOR.id
        buildingLevel = 3
        health = Buildings.MINERAL_REACTOR.maxHealth(3)
        team = ""
        this.facing = facing.serializedName
        age = 0
        fallAge = 0
        mineralAges.clear()
        clearModules()
        partPositions.clear()
        coveredBelow.clear()
        if (world != null) {
            partPositions.addAll(
                BuildingStyles.apply(world, blockPos, Buildings.MINERAL_REACTOR, 3, facing, emptyList(), { pos, state -> rememberCovered(pos, state) }),
            )
        }
        setChanged()
        if (!raised) {
            val settled = level as? ServerLevel
            if (settled != null) BuildingPlacement.settle(settled, this, 0)
        }
    }

    private fun wonderOnMap(world: ServerLevel): Boolean =
        SiteCores.all().any { core ->
            core.level === world &&
                !core.isRemoved &&
                core.blockPos != blockPos &&
                Buildings.byId(core.buildingId)?.category == BuildingCategory.WONDER
        }

    fun damageFromMonster(attacker: LivingEntity, attackerTeam: String, amount: Int, struck: BlockPos, attackerName: String) {
        storedDamageFromMonster(attacker, attackerTeam, amount, struck, attackerName)
    }









    fun destroy() {
        storedDestroy()
    }



    override fun setLevel(level: Level) {
        super.setLevel(level)
        if (!level.isClientSide) SiteCores.add(this)
    }

    override fun setRemoved() {
        SiteCores.remove(this)
        super.setRemoved()
    }

    fun demolish(player: ServerPlayer): Boolean = storedDemolish(player)

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
        InventoryCosts.take(player, storage, warehouse, next.costs)
        if (buildingId == Buildings.WALL.id) {
            ProfessionEffects.refundWallUpgrade(player, next.costs)
        }
        val oldCooldown = if (SuperWeapons.isSuperWeapon(definition.id)) {
            SuperWeapons.cooldownTicks(definition, buildingLevel)
        } else {
            0
        }
        buildingLevel += 1
        if (oldCooldown > 0) {
            val cut = oldCooldown - SuperWeapons.cooldownTicks(definition, buildingLevel)
            if (cut > 0) weaponCooldown = (weaponCooldown - cut).coerceAtLeast(0)
        }
        health = definition.maxHealth(buildingLevel)
        sanitizeMinerals(definition)
        val previous = partPositions.toList()
        partPositions.clear()
        partPositions.addAll(
            BuildingStyles.apply(world, blockPos, definition, buildingLevel, facingDirection(), previous, { pos, state -> rememberCovered(pos, state) }),
        )
        setChanged()
        player.containerMenu.broadcastChanges()
        player.sendSystemMessage(Component.literal("${definition.displayName}已升至 $buildingLevel 级"))
        return true
    }

    fun produceCycle() {
        storedProduceCycle()
    }

    fun offer(stack: ItemStack) {
        storedOffer(stack)
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
            if (dropModulesOnRemove) {
                dropModule(world, pos, reinforce)
                dropModule(world, pos, reinforceTwo)
                dropModule(world, pos, diffuse)
            }
            reinforce = ItemStack.EMPTY
            reinforceTwo = ItemStack.EMPTY
            diffuse = ItemStack.EMPTY
        }
        super.preRemoveSideEffects(pos, state)
    }

    override fun saveAdditional(output: ValueOutput) {
        super.saveAdditional(output)
        writeState(output)
    }

    override fun loadAdditional(input: ValueInput) {
        super.loadAdditional(input)
        readState(input)
    }

    fun productionLines(): List<Pair<String, Boolean>> = storedProductionLines()

    fun chooseReleaseCount(count: Int) {
        if (count !in MatchRules.BARRACKS_RELEASE_MIN..MatchRules.BARRACKS_RELEASE_MAX) return
        releaseCount = count
        setChanged()
    }

    fun moduleRefusal(slot: ModuleSlot, stack: ItemStack): String? = storedModuleRefusal(slot, stack)

    fun exchangeModule(slot: ModuleSlot, incoming: ItemStack): ItemStack = storedExchangeModule(slot, incoming)

    fun moduleActive(slot: ModuleSlot): Boolean = storedModuleActive(slot)

    fun moduleStack(slot: ModuleSlot): ItemStack = storedModuleStack(slot)

    fun holdOrigin(): BlockPos = holdAnchor ?: blockPos


    companion object {
        private const val CLEAR_MOVED = Block.UPDATE_CLIENTS or Block.UPDATE_SUPPRESS_DROPS
        const val STORAGE_SIZE = 36
        const val BARRACKS_MATERIALS = 16
        const val BARRACKS_EGGS = 12

        fun defaultMonster(definition: BuildingDefinition): String =
            if (definition.id == Buildings.PILLAGER.id) "pillager" else "zombie"

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

internal fun MineralOutput.describeBase(): String = describe(baseTicks)

internal fun MineralOutput.describe(ticks: Int, percent: Int = chancePercent): String {
    val every = "每 ${SiteCoreBlockEntity.secondsText(ticks)}"
    val odds = when {
        percent > 0 -> "有 $percent% 概率 "
        chance <= 1 -> ""
        else -> "有 1/$chance 概率 "
    }
    return if (odds.isEmpty()) "$every $count 个" else "$every $odds$count 个"
}
