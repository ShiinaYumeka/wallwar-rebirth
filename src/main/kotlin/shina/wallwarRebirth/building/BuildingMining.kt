package shina.wallwarRebirth.building

import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.ItemTags
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.ai.attributes.AttributeInstance
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import shina.wallwarRebirth.match.Classes
import shina.wallwarRebirth.match.Match
import shina.wallwarRebirth.match.MatchRules
import java.util.UUID

object BuildingMining {
    private val breakLocked = HashSet<UUID>()
    private val swings = HashMap<UUID, Pair<Long, Long>>()

    fun progress(
        state: BlockState,
        player: Player,
        level: BlockGetter,
        pos: BlockPos,
        vanilla: () -> Float,
    ): Float {
        val core = resolve(level, pos, state) ?: return 0f
        if (core.isAlly(player) || !player.mainHandItem.`is`(ItemTags.PICKAXES)) return 0f
        if (protectedFloor(core, pos)) return 0f
        return vanilla()
    }

    fun finish(level: Level, player: Player, pos: BlockPos, state: BlockState): Boolean {
        if (state.block !is SiteCoreBlock && state.block !is SiteStructureBlock && state.block !is SitePartBlock) {
            return true
        }
        val core = resolve(level, pos, state) ?: return false
        if (core.isAlly(player) || !player.mainHandItem.`is`(ItemTags.PICKAXES)) return false
        if (protectedFloor(core, pos)) return false
        val miner = player as? ServerPlayer
        if (miner != null && repeatedSwing(miner, pos)) return false
        val amount = if (miner != null) digDamage(miner) else MatchRules.DIG_DAMAGE
        core.damage(amount, pos, player.scoreboardName, player)
        level.playSound(null, pos, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.BLOCKS, 1f, 0.8f)
        if (miner != null) {
            miner.forceAddEffect(MobEffectInstance(MobEffects.GLOWING, MatchRules.GLOW_TICKS, 0, false, false, true), miner)
        }
        return false
    }

    @JvmStatic
    fun holds(state: BlockState): Boolean =
        state.block is SiteCoreBlock || state.block is SiteStructureBlock || state.block is SitePartBlock

    @JvmStatic
    fun syncMiningView(player: ServerPlayer, pos: BlockPos, state: BlockState) {
        val shown = if (state.block is SiteStructureBlock) {
            (player.level().getBlockEntity(pos) as? SiteStructureBlockEntity)?.appearance ?: return
        } else {
            state
        }
        player.connection.send(ClientboundBlockUpdatePacket(pos, shown))
    }

    @JvmStatic
    fun lockBreakSpeed(player: ServerPlayer) {
        val forced = AttributeInstance(Attributes.BLOCK_BREAK_SPEED) { _ -> }
        forced.baseValue = 0.0
        player.connection.send(ClientboundUpdateAttributesPacket(player.id, listOf(forced)))
        breakLocked.add(player.uuid)
    }

    @JvmStatic
    fun unlockBreakSpeed(player: ServerPlayer) {
        if (!breakLocked.remove(player.uuid)) return
        val live = player.getAttribute(Attributes.BLOCK_BREAK_SPEED) ?: return
        player.connection.send(ClientboundUpdateAttributesPacket(player.id, listOf(live)))
    }

    private fun repeatedSwing(player: ServerPlayer, pos: BlockPos): Boolean {
        val swing = player.level().server.overworld().gameTime to pos.asLong()
        if (swings[player.uuid] == swing) return true
        swings[player.uuid] = swing
        return false
    }

    private fun digDamage(player: ServerPlayer): Int {
        val member = Match.member(player.uuid) ?: return MatchRules.DIG_DAMAGE
        val now = player.level().server.overworld().gameTime
        return Classes.digDamage(member.classLocked, member.classId, member.minerUntil, now)
    }

    fun resolve(level: BlockGetter, pos: BlockPos, state: BlockState): SiteCoreBlockEntity? {
        if (state.block is SiteCoreBlock) return level.getBlockEntity(pos) as? SiteCoreBlockEntity
        if (state.block is SiteStructureBlock) {
            val part = level.getBlockEntity(pos) as? SiteStructureBlockEntity ?: return null
            return level.getBlockEntity(part.corePos) as? SiteCoreBlockEntity
        }
        if (state.block is SitePartBlock && level is ServerLevel) {
            return SiteCores.all().firstOrNull { core ->
                core.level === level && manhattan(core.blockPos, pos) <= 3
            }
        }
        return null
    }

    private fun protectedFloor(core: SiteCoreBlockEntity, pos: BlockPos): Boolean {
        if (pos.y != core.blockPos.y - 1) return false
        val id = core.buildingId
        return id == Buildings.BASE.id || id == Buildings.BARRACKS.id
    }

    private fun manhattan(origin: BlockPos, pos: BlockPos): Int {
        val dx = origin.x - pos.x
        val dy = origin.y - pos.y
        val dz = origin.z - pos.z
        return kotlin.math.abs(dx) + kotlin.math.abs(dy) + kotlin.math.abs(dz)
    }
}
