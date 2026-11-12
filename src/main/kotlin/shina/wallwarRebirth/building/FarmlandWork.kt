package shina.wallwarRebirth.building

import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.Containers
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
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

object FarmlandWork {
    fun isPart(core: SiteCoreBlockEntity, pos: BlockPos): Boolean =
        core.buildingId == Buildings.FARMLAND.id && pos != core.blockPos

    fun owner(level: ServerLevel, pos: BlockPos): SiteCoreBlockEntity? =
        SiteCores.all().firstOrNull { core ->
            core.level === level &&
                !core.isRemoved &&
                core.buildingId == Buildings.FARMLAND.id &&
                pos in core.partPositions &&
                pos != core.blockPos
        }

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
}
