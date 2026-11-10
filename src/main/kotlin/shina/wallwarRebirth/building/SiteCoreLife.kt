package shina.wallwarRebirth.building

import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.BlockTags
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LevelEvent
import shina.wallwarRebirth.contract.Ports
import shina.wallwarRebirth.monster.SummonedUnit
import shina.wallwarRebirth.rules.MatchRules
import shina.wallwarRebirth.util.playNotifySound
import shina.wallwarRebirth.util.showActionBar

internal fun SiteCoreBlockEntity.reflect(attacker: LivingEntity?, dealt: Int) {
    if (!moduleActive(ModuleSlot.SECONDARY) || attacker == null || !attacker.isAlive || dealt <= 0) return
    if (friendly(attacker)) return
    val world = level as? ServerLevel ?: return
    attacker.hurtServer(world, world.damageSources().thorns(attacker), MatchRules.WALL_THORNS_DAMAGE.toFloat())
}

internal fun SiteCoreBlockEntity.friendly(attacker: LivingEntity): Boolean {
    if (team.isEmpty()) return false
    val other = when (attacker) {
        is Player -> attacker.team?.name ?: ""
        is SummonedUnit -> attacker.summonedTeam
        else -> return false
    }
    return other.isNotEmpty() && other == team
}

internal fun SiteCoreBlockEntity.warnBase(attackerName: String) {
    val world = level as? ServerLevel ?: return
    val text = Component.literal("${attackerName}正在破坏我方核心 剩余生命 $health")
    for (player in world.server.playerList.players) {
        if ((player.team?.name ?: "") != team) continue
        player.showActionBar(text)
        player.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1f, 1f)
    }
}

internal fun SiteCoreBlockEntity.descend(level: ServerLevel, definition: BuildingDefinition): Boolean {
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
        BuildingStyles.apply(level, to, definition, moved.buildingLevel, moved.facingDirection(), emptyList()) { pos, state ->
            moved.rememberCovered(pos, state)
        },
    )
    moved.repositionDisplays(level)
    moved.setChanged()
    return true
}

internal fun SiteCoreBlockEntity.supportCells(level: ServerLevel): List<BlockPos> {
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

internal fun SiteCoreBlockEntity.pathClear(level: ServerLevel): Boolean {
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

internal fun SiteCoreBlockEntity.unsupported(level: ServerLevel, definition: BuildingDefinition, supports: List<BlockPos>): Boolean {
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

internal fun SiteCoreBlockEntity.absorb(from: SiteCoreBlockEntity) {
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

internal fun SiteCoreBlockEntity.copyItems(from: SimpleContainer, to: SimpleContainer) {
    for (slot in 0 until to.containerSize) {
        val stack = if (slot < from.containerSize) from.getItem(slot).copy() else ItemStack.EMPTY
        to.setItem(slot, stack)
    }
}

internal fun SiteCoreBlockEntity.repositionDisplays(level: ServerLevel) {
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

internal fun SiteCoreBlockEntity.storedDamage(amount: Int, struck: BlockPos = blockPos, attackerName: String? = null, attacker: LivingEntity? = null) {
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
            if (buildingId == Buildings.BASE.id) {
                val fallen = level as? ServerLevel
                if (fallen != null) Ports.match.onBaseFallen(fallen, team)
            }
            destroy()
        }
}

internal fun SiteCoreBlockEntity.storedDamageFromMonster(attacker: LivingEntity, attackerTeam: String, amount: Int, struck: BlockPos, attackerName: String) {
    if (attackerTeam == team) return
    damage(amount, struck, attackerName, attacker)
}

internal fun SiteCoreBlockEntity.storedDestroy() {
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

internal fun SiteCoreBlockEntity.storedDemolish(player: ServerPlayer): Boolean {
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

