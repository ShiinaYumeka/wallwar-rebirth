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
import shina.wallwarRebirth.profession.Classes
import shina.wallwarRebirth.match.Match
import shina.wallwarRebirth.rules.MatchRules
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
        if (FarmlandWork.isProtected(level, pos)) return 0f
        val core = resolve(level, pos, state) ?: return 0f
        if (core.isAlly(player) || !player.mainHandItem.`is`(ItemTags.PICKAXES)) return 0f
        if (protectedFloor(core, pos) || FarmlandWork.isPart(core, pos)) return 0f
        val amount = vanilla()
        if (amount > 0f) noteMining(player, core)
        return amount
    }

    fun finish(level: Level, player: Player, pos: BlockPos, state: BlockState): Boolean {
        if (FarmlandWork.isProtected(level, pos)) return false
        if (state.block !is SiteCoreBlock && state.block !is SiteStructureBlock && state.block !is SitePartBlock) {
            return true
        }
        val core = resolve(level, pos, state) ?: return false
        if (core.isAlly(player) || !player.mainHandItem.`is`(ItemTags.PICKAXES)) return false
        if (protectedFloor(core, pos) || FarmlandWork.isPart(core, pos)) return false
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
    fun holds(level: Level, pos: BlockPos, state: BlockState): Boolean =
        holds(state) || FarmlandWork.isProtected(level, pos)

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
        if (!Classes.isWrecker(member.classLocked, member.classId)) return MatchRules.DIG_DAMAGE
        val now = player.level().server.overworld().gameTime
        if (now >= member.wreckerDigUntil) return MatchRules.DIG_DAMAGE
        return MatchRules.DIG_DAMAGE * 2
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
        if (level is ServerLevel) {
            val farmland = FarmlandWork.owner(level, pos)
            if (farmland != null) return farmland
        }
        return null
    }

    private fun protectedFloor(core: SiteCoreBlockEntity, pos: BlockPos): Boolean {
        if (pos.y != core.blockPos.y - 1) return false
        val id = core.buildingId
        return id == Buildings.BASE.id || id == Buildings.BARRACKS.id || id == Buildings.PILLAGER.id
    }

    private data class MineAttempt(val team: String, val expiresAt: Long, val level: ServerLevel)

    private val miners = HashMap<UUID, MineAttempt>()

    private fun noteMining(player: Player, core: SiteCoreBlockEntity) {
        val world = core.level as? ServerLevel ?: return
        miners[player.uuid] = MineAttempt(core.team, world.gameTime + 10, world)
    }

    fun minersOf(level: ServerLevel, team: String): List<ServerPlayer> {
        if (team.isEmpty()) return emptyList()
        val now = level.gameTime
        miners.entries.removeIf { it.value.expiresAt < now }
        val found = ArrayList<ServerPlayer>()
        for ((id, attempt) in miners) {
            if (attempt.level !== level || attempt.team != team) continue
            val player = level.players().firstOrNull { it.uuid == id } ?: continue
            found.add(player)
        }
        return found
    }

    private fun manhattan(origin: BlockPos, pos: BlockPos): Int {
        val dx = origin.x - pos.x
        val dy = origin.y - pos.y
        val dz = origin.z - pos.z
        return kotlin.math.abs(dx) + kotlin.math.abs(dy) + kotlin.math.abs(dz)
    }
}
