package shina.wallwarRebirth.building

import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.FluidTags
import net.minecraft.tags.ItemTags
import net.minecraft.world.Containers
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import shina.wallwarRebirth.match.Match
import shina.wallwarRebirth.monster.SummonedUnit
import shina.wallwarRebirth.monster.SummonedUnits
import shina.wallwarRebirth.monster.SummonedVillager
import shina.wallwarRebirth.rules.MatchRules
import shina.wallwarRebirth.shop.match.TeamWarehouses
import shina.wallwarRebirth.util.showActionBar
import java.util.UUID
import kotlin.math.ceil

object FarmlandWork {
    private class PlayerHarvest(
        val player: ServerPlayer,
        val level: ServerLevel,
        val plot: WheatPlot,
        val hoe: ItemStack,
        val totalTicks: Int,
        var elapsedTicks: Int = 0,
    )

    private val playerHarvests = HashMap<UUID, PlayerHarvest>()

    fun isPart(core: SiteCoreBlockEntity, pos: BlockPos): Boolean =
        BuildingStyles.isUnbreakablePart(core, pos)

    fun owner(level: ServerLevel, pos: BlockPos): SiteCoreBlockEntity? =
        BuildingStyles.ownerOfProtectedPart(level, pos)

    @JvmStatic
    fun isProtected(level: BlockGetter, pos: BlockPos): Boolean =
        BuildingStyles.isProtectedPart(level, pos)

    fun ensureRealParts(site: SiteCoreBlockEntity, level: ServerLevel) {
        if (site.buildingId != Buildings.FARMLAND.id) return
        val trapdoor = site.blockPos.above()
        val remaining = ArrayList<BlockPos>()
        var changed = false
        for (pos in site.partPositions) {
            if (pos == site.blockPos) continue
            if (pos == trapdoor) {
                val state = level.getBlockState(pos)
                val fake = level.getBlockEntity(pos) as? SiteStructureBlockEntity
                val trap = state.block is TrapDoorBlock ||
                    fake?.appearance?.block is TrapDoorBlock ||
                    state.block is SiteStructureBlock ||
                    state.block is SitePartBlock
                if (trap && state.block !is SiteCoreBlock) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)
                }
                changed = true
                continue
            }
            val entity = level.getBlockEntity(pos) as? SiteStructureBlockEntity
            if (entity != null) {
                level.setBlock(pos, entity.appearance, Block.UPDATE_ALL)
                changed = true
            }
            remaining.add(pos)
        }
        if (!changed && remaining.size == site.partPositions.size) return
        site.partPositions.clear()
        site.partPositions.addAll(remaining)
        site.setChanged()
    }

    fun tick(site: SiteCoreBlockEntity, level: ServerLevel) {
        if (site.buildingId != Buildings.FARMLAND.id) return
        ensureRealParts(site, level)
        site.actionAge++
        if (site.actionAge < MatchRules.FARMLAND_WHEAT_TICKS) return
        site.actionAge = 0
        grow(site, level)
        site.setChanged()
    }

    fun call(site: SiteCoreBlockEntity, level: ServerLevel): Boolean {
        if (!Match.isFight()) return false
        val origin = Vec3.atCenterOf(site.blockPos)
        val villagers = SummonedUnits.all().filter { unit ->
            val mob = unit.living()
            mob.level() === level &&
                unit is SummonedVillager &&
                unit.unemployed() &&
                unit.summonedTeam.isNotEmpty() &&
                unit.summonedTeam == site.team &&
                !unit.mind.called()
        }
        val chosen = villagers.minWithOrNull(
            compareBy<SummonedUnit> { Relay.bucket(origin.distanceTo(it.living().position())) }
                .thenBy { it.living().health }
                .thenBy { it.living().id },
        ) ?: return false
        chosen.mind.acceptCall(site.blockPos)
        return true
    }

    fun nearestIdle(level: ServerLevel, team: String, origin: Vec3, seeker: SummonedVillager): SiteCoreBlockEntity? {
        if (team.isEmpty()) return null
        return SiteCores.all()
            .filter { core ->
                liveFarmland(core, level, team) &&
                    !worked(core, level) &&
                    !claimed(level, core.blockPos, seeker) &&
                    nearestSeeker(level, team, core) === seeker
            }
            .minWithOrNull(
                compareBy<SiteCoreBlockEntity> { Relay.bucket(origin.distanceTo(Vec3.atCenterOf(it.blockPos))) }
                    .thenBy { it.health }
                    .thenBy { it.blockPos.asLong() },
            )
    }

    fun nearestFriendly(level: ServerLevel, team: String, origin: Vec3): SiteCoreBlockEntity? {
        if (team.isEmpty()) return null
        return SiteCores.all()
            .filter { core -> liveFarmland(core, level, team) }
            .minWithOrNull(
                compareBy<SiteCoreBlockEntity> { Relay.bucket(origin.distanceTo(Vec3.atCenterOf(it.blockPos))) }
                    .thenBy { it.health }
                    .thenBy { it.blockPos.asLong() },
            )
    }

    fun live(level: ServerLevel, pos: BlockPos, team: String): SiteCoreBlockEntity? {
        val site = level.getBlockEntity(pos) as? SiteCoreBlockEntity ?: return null
        return if (liveFarmland(site, level, team)) site else null
    }

    fun nearestWheat(level: ServerLevel, team: String, origin: Vec3, range: Double? = null): WheatPlot? {
        var best: WheatPlot? = null
        var bestDist = Double.POSITIVE_INFINITY
        for (core in SiteCores.all()) {
            if (!liveFarmland(core, level, team)) continue
            for (pos in core.partPositions) {
                val crop = cropOf(level, pos) ?: continue
                if (!crop.mature) continue
                val dist = origin.distanceTo(Vec3.atCenterOf(pos))
                if (range != null && dist > range) continue
                if (dist < bestDist) {
                    bestDist = dist
                    best = WheatPlot(core, pos, crop.state, crop.block)
                }
            }
        }
        return best
    }

    fun startPlayerHarvest(level: ServerLevel, player: ServerPlayer, pos: BlockPos): Boolean {
        if (!Match.isFight() || !canHarvest(player, level, pos)) return false
        val hoe = player.mainHandItem
        if (!hoe.`is`(ItemTags.HOES)) return false
        val crop = cropOf(level, pos) ?: return false
        if (!crop.mature) return false
        val site = owner(level, pos) ?: return false
        if (site.buildingId != Buildings.FARMLAND.id || site.health <= 0) return false
        val active = playerHarvests[player.uuid]
        if (active?.player === player && active.plot.core === site && active.plot.pos == pos &&
            ItemStack.isSameItemSameComponents(hoe, active.hoe)
        ) return true
        val speed = hoeSpeed(player)
        if (!speed.isFinite() || speed <= 0f) return false
        val duration = ceil(FARMLAND_HARDNESS / speed).toInt().coerceAtLeast(1)
        val plot = WheatPlot(site, pos.immutable(), crop.state, crop.block)
        val job = PlayerHarvest(player, level, plot, hoe.copy(), duration)
        playerHarvests[player.uuid] = job
        showHarvestProgress(job)
        return true
    }

    fun tickPlayerHarvests(server: MinecraftServer) {
        val jobs = playerHarvests.values.iterator()
        while (jobs.hasNext()) {
            val job = jobs.next()
            val player = job.player
            if (server.playerList.getPlayer(player.uuid) !== player) {
                jobs.remove()
                continue
            }
            val (site, pos) = job.plot
            val level = job.level
            if (!Match.isFight() || !canHarvest(player, level, pos) ||
                !ItemStack.isSameItemSameComponents(player.mainHandItem, job.hoe) ||
                site.isRemoved || site.health <= 0 || site.buildingId != Buildings.FARMLAND.id ||
                pos !in site.partPositions || cropOf(level, pos)?.mature != true
            ) {
                jobs.remove()
                player.showActionBar(Component.literal("收割已取消"))
                continue
            }
            job.elapsedTicks++
            if (job.elapsedTicks >= job.totalTicks) {
                jobs.remove()
                if (harvest(level, job.plot)) {
                    player.mainHandItem.hurtAndBreak(1, player, EquipmentSlot.MAINHAND)
                    Block.popResource(level, pos, ItemStack(Items.WHEAT))
                    showHarvestProgress(job)
                }
            } else if (job.elapsedTicks % 2 == 0) {
                showHarvestProgress(job)
            }
        }
    }

    private fun canHarvest(player: ServerPlayer, level: ServerLevel, pos: BlockPos): Boolean =
        player.isAlive && !player.isSpectator && player.level() === level &&
            player.isWithinBlockInteractionRange(pos, 0.0) &&
            level.chunkSource.hasChunk(SectionPos.blockToSectionCoord(pos.x), SectionPos.blockToSectionCoord(pos.z))

    private fun showHarvestProgress(job: PlayerHarvest) {
        val percent = (job.elapsedTicks.toLong() * 100 / job.totalTicks).toInt()
        val filled = percent / 10
        val bar = Component.literal("收割小麦 ")
            .append(Component.literal("■".repeat(filled)).withStyle(ChatFormatting.GREEN))
            .append(Component.literal("■".repeat(10 - filled)).withStyle(ChatFormatting.DARK_GRAY))
            .append(" $percent%")
        job.player.showActionBar(bar)
    }

    fun harvest(level: ServerLevel, plot: WheatPlot): Boolean {
        val crop = cropOf(level, plot.pos) ?: return false
        if (!crop.mature) return false
        show(level, plot.pos, crop.block.getStateForAge(0))
        level.levelEvent(null, LevelEvent.PARTICLES_DESTROY_BLOCK, plot.pos, Block.getId(crop.state))
        level.playSound(null, plot.pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1f, 1f)
        return true
    }

    fun grow(site: SiteCoreBlockEntity, level: ServerLevel) {
        for (pos in site.partPositions) {
            val crop = cropOf(level, pos) ?: continue
            if (crop.age >= crop.block.maxAge) continue
            show(level, pos, crop.block.getStateForAge(crop.age + 1))
        }
    }

    fun deposit(site: SiteCoreBlockEntity, unit: SummonedVillager): Boolean {
        val inventory = unit.inventory
        for (slot in 0 until inventory.containerSize) {
            val stack = inventory.getItem(slot)
            if (stack.isEmpty || !stack.`is`(Items.WHEAT)) continue
            TeamWarehouses.acceptAll(site.team, stack)
            inventory.setItem(slot, ItemStack.EMPTY)
        }
        return true
    }

    fun dropStored(site: SiteCoreBlockEntity, level: ServerLevel) {
        if (site.buildingId != Buildings.FARMLAND.id) return
        Containers.dropContents(level, site.blockPos, site.storage)
        site.storage.clearContent()
    }

    private fun claimed(level: ServerLevel, pos: BlockPos, seeker: SummonedVillager): Boolean =
        SummonedUnits.all().any { unit ->
            val villager = unit as? SummonedVillager ?: return@any false
            villager !== seeker &&
                villager.isAlive &&
                villager.level() === level &&
                villager.unemployed() &&
                villager.mind.callDestination() == pos
        }

    private fun nearestSeeker(level: ServerLevel, team: String, site: SiteCoreBlockEntity): SummonedVillager? {
        val origin = Vec3.atCenterOf(site.blockPos)
        return SummonedUnits.all().mapNotNull { unit ->
            val villager = unit as? SummonedVillager ?: return@mapNotNull null
            if (!villager.isAlive || villager.level() !== level) return@mapNotNull null
            if (!villager.unemployed() || villager.summonedTeam != team || villager.mind.called()) return@mapNotNull null
            villager
        }.minWithOrNull(
            compareBy<SummonedVillager> { Relay.bucket(origin.distanceTo(it.position())) }
                .thenBy { it.health }
                .thenBy { it.id },
        )
    }

    private fun worked(site: SiteCoreBlockEntity, level: ServerLevel): Boolean =
        SummonedUnits.all().any { unit ->
            val villager = unit as? SummonedVillager ?: return@any false
            villager.isAlive &&
                villager.isFarmer() &&
                villager.level() === level &&
                villager.summonedTeam == site.team &&
                !villager.mind.following() &&
                workSite(villager, level) == site.blockPos
        }

    private fun workSite(villager: SummonedVillager, level: ServerLevel): BlockPos? {
        val origin = villager.position()
        val team = villager.summonedTeam
        if (villager.wheatFull()) return nearestFriendly(level, team, origin)?.blockPos
        val wheat = nearestWheat(level, team, origin)
        if (wheat != null) return wheat.core.blockPos
        return nearestFriendly(level, team, origin)?.blockPos
    }

    private fun liveFarmland(core: SiteCoreBlockEntity, level: ServerLevel, team: String): Boolean =
        core.level === level &&
            !core.isRemoved &&
            core.health > 0 &&
            core.buildingId == Buildings.FARMLAND.id &&
            core.team.isNotEmpty() &&
            core.team == team

    private fun cropOf(level: ServerLevel, pos: BlockPos): CropView? {
        val state = wheatState(level, pos) ?: return null
        val crop = state.block as? CropBlock ?: return null
        if (!state.`is`(Blocks.WHEAT)) return null
        val age = crop.getAge(state)
        return CropView(state, crop, age, crop.isMaxAge(state))
    }

    private fun hoeSpeed(player: Player): Float {
        val stack = player.mainHandItem
        val tool = stack.get(DataComponents.TOOL)
        var speed = 1f
        if (tool != null) {
            for (rule in tool.rules) {
                if (!rule.speed.isPresent) continue
                val value = rule.speed.get()
                if (value > speed) speed = value
            }
        }
        if (speed > 1f) {
            val efficiency = enchantmentLevel(player, stack, Enchantments.EFFICIENCY)
            if (efficiency > 0) speed += efficiency * efficiency + 1
        }
        val haste = player.getEffect(MobEffects.HASTE)
        if (haste != null) speed *= 1f + (haste.amplifier + 1) * 0.2f
        val fatigue = player.getEffect(MobEffects.MINING_FATIGUE)
        if (fatigue != null) {
            speed *= when (fatigue.amplifier) {
                0 -> 0.3f
                1 -> 0.09f
                2 -> 0.027f
                else -> 0.0081f
            }
        }
        val aqua = enchantmentLevel(player, player.getItemBySlot(EquipmentSlot.HEAD), Enchantments.AQUA_AFFINITY) > 0
        if (player.isEyeInFluid(FluidTags.WATER) && !aqua) speed /= 5f
        if (!player.onGround()) speed /= 5f
        return speed
    }

    private fun enchantmentLevel(player: Player, stack: ItemStack, enchantment: net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment>): Int {
        val enchants = stack.get(DataComponents.ENCHANTMENTS) ?: return 0
        val holder = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(enchantment).orElse(null) ?: return 0
        return enchants.getLevel(holder)
    }

    private fun wheatState(level: ServerLevel, pos: BlockPos): BlockState? {
        val entity = level.getBlockEntity(pos) as? SiteStructureBlockEntity
        if (entity != null) return entity.appearance
        val state = level.getBlockState(pos)
        return if (state.`is`(Blocks.WHEAT)) state else null
    }

    private fun show(level: ServerLevel, pos: BlockPos, appearance: BlockState) {
        val entity = level.getBlockEntity(pos) as? SiteStructureBlockEntity
        if (entity != null) {
            entity.appearance = appearance
            entity.setChanged()
            for (player in level.players()) {
                if (player.distanceToSqr(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5) <= 64.0 * 64.0) {
                    player.connection.send(ClientboundBlockUpdatePacket(pos, appearance))
                }
            }
            return
        }
        if (level.getBlockState(pos).block is CropBlock) {
            level.setBlock(pos, appearance, Block.UPDATE_ALL)
        }
    }

    data class WheatPlot(val core: SiteCoreBlockEntity, val pos: BlockPos, val state: BlockState, val block: CropBlock)

    private data class CropView(val state: BlockState, val block: CropBlock, val age: Int, val mature: Boolean)

    private const val FARMLAND_HARDNESS = 200f
}
