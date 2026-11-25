package shina.wallwarRebirth.building

import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.GameType
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.CropBlock
import net.minecraft.world.level.block.FarmBlock
import net.minecraft.world.phys.Vec3
import shina.wallwarRebirth.contract.WallwarTeams
import shina.wallwarRebirth.match.Match
import shina.wallwarRebirth.match.MatchRoster
import shina.wallwarRebirth.monster.SummonedUnits
import shina.wallwarRebirth.monster.Summoning
import shina.wallwarRebirth.rules.MatchRules
import shina.wallwarRebirth.targeting.Targeting
import shina.wallwarRebirth.util.clearTitle
import shina.wallwarRebirth.util.playNotifySound
import shina.wallwarRebirth.util.showTitle
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

object SuperWeaponAim {
    private enum class Kind { STRIKE, TELEPORT }

    private class Session(
        val sitePos: BlockPos,
        val kind: Kind,
        val savedMode: GameType,
        var selected: BlockPos? = null,
        var lastClickTick: Long = Long.MIN_VALUE,
        var sneakArmed: Boolean = false,
    )

    private val sessions = HashMap<UUID, Session>()

    @JvmStatic
    fun busy(player: Player): Boolean = sessions.containsKey(player.uuid)

    fun tryStart(site: SiteCoreBlockEntity, player: ServerPlayer, teleport: Boolean): Boolean {
        if (!site.isAlly(player)) return false
        if (Match.phase != Match.Phase.FIGHT) return false
        if (!SuperWeapons.ready(site)) return false
        if (busy(player) || CannonCrew.busy(player)) return false
        val level = site.level as? ServerLevel ?: return false
        if (!Match.isArena(level)) return false
        val mode = player.gameMode.gameModeForPlayer
        if (mode != GameType.SURVIVAL && mode != GameType.ADVENTURE) return false
        player.closeContainer()
        val session = Session(site.blockPos.immutable(), if (teleport) Kind.TELEPORT else Kind.STRIKE, mode)
        sessions[player.uuid] = session
        conceal(player, level)
        prompt(player, session)
        return true
    }

    fun tick(server: MinecraftServer) {
        SuperWeaponParticles.tick(server)
        val arena = server.getLevel(MatchRules.DIMENSION) ?: return
        for (id in sessions.keys.toList()) {
            val session = sessions[id] ?: continue
            val player = server.playerList.getPlayer(id)
            if (player == null) {
                sessions.remove(id)
                continue
            }
            val site = siteOf(arena, session)
            if (site == null || Match.phase != Match.Phase.FIGHT) {
                cancel(player, site)
                continue
            }
            conceal(player, arena)
            prompt(player, session)
            val marked = session.selected
            if (marked != null) {
                val age = arena.gameTime - session.lastClickTick
                if (age > 0) showPreview(arena, site, session.kind, marked, age)
            }
            if (!player.isShiftKeyDown) {
                session.sneakArmed = true
                continue
            }
            if (!session.sneakArmed) continue
            cancel(player, site)
        }
    }

    @JvmStatic
    fun handleClick(player: ServerPlayer): Boolean {
        val session = sessions[player.uuid] ?: return false
        if (Match.phase != Match.Phase.FIGHT) return true
        val level = player.level()
        val site = siteOf(level, session)
        if (site == null) {
            cancel(player, null)
            return true
        }
        val surface = surfaceOf(level, player) ?: return true
        val now = level.gameTime
        if (session.lastClickTick != Long.MIN_VALUE && now - session.lastClickTick < 4L) return true
        session.lastClickTick = now
        val selected = session.selected
        if (selected == null || selected != surface) {
            session.selected = surface
            prompt(player, session)
            showPreview(level, site, session.kind, surface, age = 0)
            return true
        }
        confirm(player, site, session, surface)
        return true
    }

    fun disconnect(player: ServerPlayer) {
        val session = sessions.remove(player.uuid) ?: return
        val site = siteOf(player.level(), session)
        reveal(player, session)
        if (site != null) {
            appearBeside(player, site)
        }
        player.clearTitle()
    }

    fun settle(server: MinecraftServer) {
        for (id in sessions.keys.toList()) {
            val player = server.playerList.getPlayer(id)
            if (player == null) {
                sessions.remove(id)
                continue
            }
            val session = sessions[id] ?: continue
            val site = siteOf(player.level(), session)
            cancel(player, site)
        }
    }

    private fun confirm(player: ServerPlayer, site: SiteCoreBlockEntity, session: Session, surface: BlockPos) {
        sessions.remove(player.uuid)
        reveal(player, session)
        player.clearTitle()
        SuperWeapons.startCooldown(site)
        when (session.kind) {
            Kind.STRIKE -> {
                appearBeside(player, site)
                val world = site.level as? ServerLevel ?: return
                CannonShell.launchGuided(world, site, surface)
            }
            Kind.TELEPORT -> teleport(player, site, surface)
        }
    }

    private fun cancel(player: ServerPlayer, site: SiteCoreBlockEntity?) {
        val session = sessions.remove(player.uuid) ?: return
        reveal(player, session)
        player.clearTitle()
        if (site != null) appearBeside(player, site)
    }

    private fun teleport(player: ServerPlayer, site: SiteCoreBlockEntity, surface: BlockPos) {
        val level = site.level as? ServerLevel ?: return
        val feet = standableAt(level, surface) ?: standableNear(level, surface, null)
        val dest = feet ?: surface.above()
        snap(player, dest)
        SuperWeaponParticles.arrival(level, player.position())
        val used = HashSet<BlockPos>()
        used.add(dest)
        for (unit in SummonedUnits.all()) {
            val living = unit.living()
            if (!living.isAlive) continue
            if (living.level() !== level) continue
            if (unit.summonedTeam != site.team) continue
            if (!unit.mind.follows(player)) continue
            val spot = standableNear(level, dest, used) ?: continue
            used.add(spot)
            if (!living.teleportTo(level, spot.x + 0.5, spot.y.toDouble(), spot.z + 0.5, emptySet(), living.yRot, living.xRot, false)) continue
            living.deltaMovement = Vec3.ZERO
            living.fallDistance = 0.0
            (living as? Mob)?.navigation?.stop()
            SuperWeaponParticles.followerArrival(level, living.position())
        }
        announceArrival(level, site, player)
    }

    private fun announceArrival(level: ServerLevel, site: SiteCoreBlockEntity, player: ServerPlayer) {
        val color = when (site.team) {
            WallwarTeams.RED -> ChatFormatting.RED
            WallwarTeams.BLUE -> ChatFormatting.BLUE
            else -> ChatFormatting.WHITE
        }
        val title = Component.literal("传送尖塔").withStyle(color)
        val subtitle = Component.literal(player.scoreboardName).append(" 已抵达")
        for (viewer in level.players()) {
            viewer.showTitle(title, subtitle, 5, 40, 10)
            viewer.playNotifySound(SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1f, 0.7f)
        }
    }

    private fun conceal(player: ServerPlayer, level: ServerLevel) {
        Targeting.setAiming(player.uuid, true)
        if (player.gameMode.gameModeForPlayer == GameType.SPECTATOR) {
            val session = sessions[player.uuid]
            if (session != null) player.gameMode.changeGameModeForPlayer(session.savedMode)
        }
        val abilities = player.abilities
        abilities.mayfly = true
        abilities.flying = true
        abilities.invulnerable = true
        player.onUpdateAbilities()
        player.isInvulnerable = true
        player.isInvisible = true
        player.noPhysics = true
        player.releaseUsingItem()
        player.deltaMovement = Vec3.ZERO
        player.fallDistance = 0.0
        if (player.level() !== level) return
        val top = level.maxY - 2
        val y = top.toDouble().coerceAtLeast(player.y)
        if (player.y < top - 8) {
            player.snapTo(player.x, y, player.z, player.yRot, 90f)
        } else if (player.xRot < 45f) {
            player.snapTo(player.x, player.y, player.z, player.yRot, 90f)
        }
        MatchRoster.followChunks(player)
    }

    private fun reveal(player: ServerPlayer, session: Session) {
        Targeting.setAiming(player.uuid, false)
        if (player.gameMode.gameModeForPlayer == GameType.SPECTATOR) {
            player.gameMode.changeGameModeForPlayer(session.savedMode)
        }
        stopFlight(player)
    }

    private fun stopFlight(player: ServerPlayer) {
        val mode = player.gameMode.gameModeForPlayer
        if (mode == GameType.SURVIVAL || mode == GameType.ADVENTURE) {
            mode.updatePlayerAbilities(player.abilities)
        }
        val abilities = player.abilities
        abilities.mayfly = false
        abilities.flying = false
        abilities.invulnerable = false
        player.onUpdateAbilities()
        if (!player.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY)) player.isInvisible = false
        player.isInvulnerable = false
        player.noPhysics = false
    }

    private fun showPreview(level: ServerLevel, site: SiteCoreBlockEntity, kind: Kind, surface: BlockPos, age: Long) {
        val center = Vec3.atCenterOf(surface).add(0.0, 0.7, 0.0)
        SuperWeaponParticles.preview(level, site.team, center, kind == Kind.STRIKE, age)
    }

    private fun appearBeside(player: ServerPlayer, site: SiteCoreBlockEntity) {
        val level = site.level as? ServerLevel ?: return
        val spot = beside(level, site)
        snap(player, BlockPos.containing(spot.x, spot.y, spot.z), spot)
    }

    private fun snap(player: ServerPlayer, feet: BlockPos, exact: Vec3? = null) {
        val level = player.level()
        val dest = exact ?: Vec3(feet.x + 0.5, feet.y.toDouble(), feet.z + 0.5)
        player.fallDistance = 0.0
        player.deltaMovement = Vec3.ZERO
        player.teleportTo(level, dest.x, dest.y, dest.z, emptySet(), player.yRot, 0f, false)
        player.deltaMovement = Vec3.ZERO
        player.fallDistance = 0.0
        stopFlight(player)
        MatchRoster.followChunks(player)
    }

    private fun prompt(player: ServerPlayer, session: Session) {
        val selected = session.selected != null
        val title = if (session.kind == Kind.TELEPORT) {
            if (selected) "已选定传送地点" else "选定传送地点"
        } else {
            if (selected) "已选定打击地点" else "选定打击地点"
        }
        val subtitle = if (session.kind == Kind.TELEPORT) {
            if (selected) "再按左键确认传送，潜行取消" else "左键选定，潜行取消"
        } else {
            if (selected) "再按左键确认开炮，潜行取消" else "左键选定，潜行取消"
        }
        player.showTitle(Component.literal(title), Component.literal(subtitle), 0, 30, 0)
    }

    private fun siteOf(level: ServerLevel, session: Session): SiteCoreBlockEntity? {
        val site = SiteCores.find(level, session.sitePos) ?: return null
        if (site.isRemoved || site.health <= 0) return null
        val expected = if (session.kind == Kind.TELEPORT) Buildings.TELEPORT_SPIRE.id else Buildings.GUIDED_CANNON.id
        if (site.buildingId != expected) return null
        return site
    }

    private fun surfaceOf(level: ServerLevel, player: ServerPlayer): BlockPos? {
        val hit = player.pick(512.0, 0f, false)
        val looked = if (hit is net.minecraft.world.phys.BlockHitResult && hit.type != net.minecraft.world.phys.HitResult.Type.MISS) {
            hit.blockPos
        } else {
            val look = player.lookAngle
            if (abs(look.y) < 1.0e-6) return null
            val t = (level.maxY - player.y) / look.y
            if (t < 0) return null
            val point = player.eyePosition.add(look.scale(t.coerceAtMost(512.0)))
            BlockPos.containing(point.x, point.y, point.z)
        }
        return highestSurface(level, looked.x, looked.z)
    }

    private fun highestSurface(level: ServerLevel, x: Int, z: Int): BlockPos? {
        var y = level.maxY
        while (y >= level.minY) {
            val pos = BlockPos(x, y, z)
            val state = level.getBlockState(pos)
            if (!state.getCollisionShape(level, pos).isEmpty && state.fluidState.isEmpty) {
                return pos
            }
            y--
        }
        return null
    }

    private fun standableAt(level: ServerLevel, surface: BlockPos): BlockPos? {
        val feet = surface.above()
        return if (usable(level, feet)) feet else null
    }

    private fun standableNear(level: ServerLevel, origin: BlockPos, used: Set<BlockPos>?): BlockPos? {
        val spots = ArrayList<BlockPos>()
        for (dy in intArrayOf(0, 1, -1, 2, -2)) {
            for (dx in -8..8) {
                for (dz in -8..8) {
                    spots.add(origin.offset(dx, dy, dz))
                }
            }
        }
        spots.sortWith(
            compareBy<BlockPos> { abs(it.y - origin.y) }
                .thenBy { max(abs(it.x - origin.x), abs(it.z - origin.z)) }
                .thenBy { it.x }
                .thenBy { it.z },
        )
        return spots.firstOrNull { (used == null || it !in used) && usable(level, it) }
    }

    private fun usable(level: ServerLevel, pos: BlockPos): Boolean {
        if (level.isOutsideBuildHeight(pos) || level.isOutsideBuildHeight(pos.above())) return false
        if (!Summoning.canOccupy(level, pos, null) || !Summoning.canOccupy(level, pos.above(), null)) return false
        val state = level.getBlockState(pos)
        if (!state.fluidState.isEmpty) return false
        if (state.block is CropBlock || state.block is FarmBlock) return false
        val belowPos = pos.below()
        if (level.isOutsideBuildHeight(belowPos)) return false
        val below = level.getBlockState(belowPos)
        if (!below.fluidState.isEmpty) return false
        if (below.getCollisionShape(level, belowPos).isEmpty) return false
        return true
    }

    fun beside(level: ServerLevel, site: SiteCoreBlockEntity): Vec3 {
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
