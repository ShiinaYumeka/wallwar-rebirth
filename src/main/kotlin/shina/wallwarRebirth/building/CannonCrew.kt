package shina.wallwarRebirth.building
import shina.wallwarRebirth.rules.MatchRules

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.GameType
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import shina.wallwarRebirth.match.Match
import java.util.UUID

object CannonCrew {
    private val sneakArmed = HashSet<UUID>()

    @JvmStatic
    fun locked(player: Player): Boolean = (player.vehicle as? CannonShell)?.manned == true

    fun busy(player: ServerPlayer): Boolean = locked(player) || waiting(player) != null

    fun tryEnter(site: SiteCoreBlockEntity, player: ServerPlayer): Boolean {
        if (site.buildingId != Buildings.CANNON.id) return false
        if (!player.isShiftKeyDown) return false
        if (site.diffuse.isEmpty) return false
        if (!site.isAlly(player)) return false
        if (Match.phase != Match.Phase.FIGHT) return false
        if (player.gameMode.gameModeForPlayer != GameType.SURVIVAL) return false
        val level = site.level as? ServerLevel ?: return false
        if (!Match.isArena(level)) return false
        val occupied = site.gunner
        if (occupied != null && occupied != player.uuid) {
            player.sendSystemMessage(Component.literal("已经有人"))
            return true
        }
        if (occupied == player.uuid) return true
        site.gunner = player.uuid
        sneakArmed.remove(player.uuid)
        site.setChanged()
        player.closeContainer()
        conceal(player, level, site.blockPos)
        return true
    }

    fun tickWaiting(site: SiteCoreBlockEntity, level: ServerLevel, player: ServerPlayer): Boolean {
        if (site.diffuse.isEmpty || site.health <= 0) {
            evict(site, level)
            return true
        }
        conceal(player, level, site.blockPos)
        if (!player.isShiftKeyDown) {
            sneakArmed.add(player.uuid)
            return false
        }
        if (player.uuid !in sneakArmed) return false
        sneakArmed.remove(player.uuid)
        release(site, level, player)
        return true
    }

    fun rider(site: SiteCoreBlockEntity, level: ServerLevel): ServerPlayer? {
        val id = site.gunner ?: return null
        val player = level.server.playerList.getPlayer(id) ?: return null
        if (!player.isAlive || player.level() !== level) return null
        if (player.isSpectator || player.gameMode.gameModeForPlayer != GameType.SURVIVAL) return null
        return player
    }

    fun holding(site: SiteCoreBlockEntity, level: ServerLevel): Boolean {
        val id = site.gunner ?: return false
        val player = level.server.playerList.getPlayer(id)
        if (player == null) return true
        site.gunner = null
        sneakArmed.remove(id)
        site.setChanged()
        reveal(player)
        return false
    }

    fun disconnect(player: ServerPlayer) {
        val site = waiting(player) ?: return
        site.gunner = null
        sneakArmed.remove(player.uuid)
        site.setChanged()
        reveal(player)
    }

    fun embark(site: SiteCoreBlockEntity, player: ServerPlayer, shell: CannonShell) {
        site.gunner = null
        sneakArmed.remove(player.uuid)
        site.setChanged()
        reveal(player)
        player.startRiding(shell, true, true)
    }

    fun evict(site: SiteCoreBlockEntity, level: ServerLevel) {
        val id = site.gunner ?: return
        site.gunner = null
        sneakArmed.remove(id)
        val player = level.server.playerList.getPlayer(id) ?: return
        release(site, level, player)
    }

    fun rejoin(player: ServerPlayer) {
        val site = waiting(player)
        if (site != null && Match.phase == Match.Phase.FIGHT) {
            val level = site.level as? ServerLevel ?: return
            conceal(player, level, site.blockPos)
            return
        }
        if (locked(player)) return
        if (player.isInvisible && !player.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY)) {
            player.setInvisible(false)
        }
        player.setInvulnerable(false)
        player.noPhysics = false
    }

    fun settle(server: MinecraftServer, killRiders: Boolean) {
        val arena = server.getLevel(shina.wallwarRebirth.rules.MatchRules.DIMENSION) ?: return
        val shells = ArrayList<CannonShell>()
        for (entity in arena.allEntities) {
            val shell = entity as? CannonShell ?: continue
            if (shell.manned) shells.add(shell)
        }
        for (shell in shells) {
            val rider = shell.passengers.firstOrNull() as? ServerPlayer
            val point = shell.position()
            rider?.stopRiding()
            shell.discard()
            if (rider == null) continue
            if (killRiders) kill(arena, rider, point) else reveal(rider)
        }
        for (site in SiteCores.all().toList()) {
            if (site.level !== arena || site.gunner == null) continue
            evict(site, arena)
        }
    }

    private fun waiting(player: ServerPlayer): SiteCoreBlockEntity? =
        SiteCores.all().firstOrNull { it.gunner == player.uuid && !it.isRemoved && it.health > 0 }

    private fun release(site: SiteCoreBlockEntity, level: ServerLevel, player: ServerPlayer) {
        if (site.gunner == player.uuid) {
            site.gunner = null
            site.setChanged()
        }
        sneakArmed.remove(player.uuid)
        reveal(player)
        val spot = beside(level, site)
        player.snapTo(spot.x, spot.y, spot.z, player.yRot, player.xRot)
        player.setDeltaMovement(Vec3.ZERO)
        player.fallDistance = 0.0
    }

    private fun conceal(player: ServerPlayer, level: ServerLevel, core: BlockPos) {
        val center = Vec3.atCenterOf(core)
        player.setInvisible(true)
        player.setInvulnerable(true)
        player.noPhysics = true
        player.releaseUsingItem()
        player.setDeltaMovement(Vec3.ZERO)
        player.fallDistance = 0.0
        if (player.level() !== level) return
        player.snapTo(center.x, center.y, center.z, player.yRot, player.xRot)
    }

    private fun reveal(player: ServerPlayer) {
        if (!player.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY)) player.setInvisible(false)
        player.setInvulnerable(false)
        player.noPhysics = false
    }

    fun kill(level: ServerLevel, player: ServerPlayer, point: Vec3) {
        reveal(player)
        player.stopRiding()
        player.snapTo(point.x, point.y, point.z, player.yRot, player.xRot)
        player.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE)
    }

    private fun beside(level: ServerLevel, site: SiteCoreBlockEntity): Vec3 {
        val facing = site.facingDirection()
        val order = listOf(facing, facing.clockWise, facing.counterClockWise, facing.opposite)
        for (dir in order) {
            val feet = site.blockPos.relative(dir)
            if (open(level, feet)) return Vec3(feet.x + 0.5, feet.y.toDouble(), feet.z + 0.5)
        }
        val above = site.blockPos.above()
        if (open(level, above)) return Vec3(above.x + 0.5, above.y.toDouble(), above.z + 0.5)
        val fallback = site.blockPos.relative(if (facing.axis == Direction.Axis.Y) Direction.NORTH else facing)
        return Vec3(fallback.x + 0.5, fallback.y.toDouble(), fallback.z + 0.5)
    }

    private fun open(level: Level, feet: BlockPos): Boolean {
        val head = feet.above()
        if (level.isOutsideBuildHeight(feet) || level.isOutsideBuildHeight(head)) return false
        if (!level.getBlockState(feet).isAir || !level.getBlockState(head).isAir) return false
        val server = level as? ServerLevel ?: return true
        return !SiteCores.insideBuilding(server, feet) && !SiteCores.insideBuilding(server, head)
    }
}
