package shina.wallwarRebirth.match

import net.minecraft.commands.Commands
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameType
import net.minecraft.world.level.Level
import net.minecraft.world.level.gamerules.GameRules
import shina.wallwarRebirth.contract.MatchPort
import shina.wallwarRebirth.contract.WallwarTeams
import shina.wallwarRebirth.profession.Classes
import shina.wallwarRebirth.profession.SkillInventory
import shina.wallwarRebirth.rules.MatchRules
import shina.wallwarRebirth.shop.match.TeamWarehouses
import shina.wallwarRebirth.util.clearTitle
import shina.wallwarRebirth.util.showActionBar
import shina.wallwarRebirth.util.showTitle
import shina.wallwarRebirth.world.ArenaReset
import shina.wallwarRebirth.world.MatchWorld
import java.util.*

object Match : MatchPort {
    enum class Phase {
        LOBBY,
        WATCH,
        FIGHT,
        SETTLE,
    }

    data class Spawn(val x: Double, val y: Double, val z: Double, val yaw: Float)

    class Member(val id: UUID) {
        var team: String = ""
        var classId: String = Classes.MINER
        var classLocked: Boolean = true
        var skillReadyAt: Long = 0
        var wreckerDigUntil: Long = 0
        var ready: Boolean = false
        var spectating: Boolean = false
        var started: Boolean = false
        var ironGiven: Boolean = false
        var respawnAt: Int = 0
        var pendingReset: Boolean = false
        var heldItems: List<ItemStack> = emptyList()
        var heldLevel: Int = 0
        var heldProgress: Float = 0f
    }

    var phase: Phase = Phase.LOBBY
    var worldReady: Boolean = false
    var mapSize: Int = MatchRules.MAP_SIZE

    @JvmField
    var terrainSeed: Long = 0
    var redBase: BlockPos = BlockPos.ZERO
    var blueBase: BlockPos = BlockPos.ZERO
    var redSpawn: Spawn = Spawn(0.5, 80.0, 0.5, 0f)
    var blueSpawn: Spawn = Spawn(0.5, 80.0, 0.5, 0f)
    internal val members = HashMap<UUID, Member>()
    internal var generating = false
    internal var loadDeadline: Int = -1
    internal var watchBuildTick: Int = -1
    internal var watchEndsAt: Int = 0
    internal var watchSecond: Int = -1
    internal var settleResetAt: Int = -1
    private val pendingLobby = LinkedHashSet<UUID>()
    internal var server: MinecraftServer? = null

    fun attach(current: MinecraftServer) {
        server = current
        MatchPersistence.load(current)
        MatchWorld.release(current)
    }

    fun stop(current: MinecraftServer) {
        MatchWorld.release(current)
        MatchPersistence.save(current)
    }

    fun tick(current: MinecraftServer) {
        server = current
        flushLobbyReturns(current)
        for (level in current.allLevels) {
            val rules = level.gameRules
            if (rules.get(GameRules.IMMEDIATE_RESPAWN) == true) continue
            rules.set(GameRules.IMMEDIATE_RESPAWN, true, current)
        }
        val arena = current.getLevel(MatchRules.DIMENSION)
        if (arena != null) {
            if (ArenaReset.holding()) ArenaReset.forget(arena)
            if (arena.dayTime != MatchRules.DAY_TIME) arena.dayTime = MatchRules.DAY_TIME
            arena.setWeatherParameters(6000, 0, false, false)
        }
        if (watchBuildTick == current.tickCount) {
            watchBuildTick = -1
            MatchFlow.buildWatch(current)
        }
        if (generating) MatchFlow.finishWatch(current)
        if (phase == Phase.WATCH) {
            if (watchEndsAt == 0) watchEndsAt = current.tickCount + MatchRules.WATCH_TICKS
            val left = watchEndsAt - current.tickCount
            if (left <= 0) {
                MatchFlow.beginFight(current)
            } else {
                MatchFlow.showWatchRemaining(current, left)
            }
        }
        MatchRoster.tickRespawns(current)
        MatchMenu.tick(current)
        if (phase == Phase.SETTLE) tickSettleReset(current)
        if (phase == Phase.FIGHT) {
            SkillInventory.tick(current)
        }
    }

    override fun isFrozen(level: Level): Boolean = level.dimension() == MatchRules.DIMENSION && phase != Phase.FIGHT

    fun frozen(level: Level): Boolean = isFrozen(level)

    override fun isFight(): Boolean = phase == Phase.FIGHT

    override fun isArena(level: Level): Boolean = level.dimension() == MatchRules.DIMENSION

    fun restricted(player: ServerPlayer): Boolean {
        if (phase == Phase.FIGHT) return false
        if (isArena(player.level())) return true
        return members.containsKey(player.uuid)
    }

    fun member(id: UUID): Member? = members[id]

    fun bases(): List<Pair<String, BlockPos>> {
        if (!worldReady) return emptyList()
        return listOf(WallwarTeams.RED to redBase, WallwarTeams.BLUE to blueBase)
    }

    fun spawnOf(team: String): Spawn? = when (team) {
        WallwarTeams.RED -> redSpawn
        WallwarTeams.BLUE -> blueSpawn
        else -> null
    }

    fun assign(player: ServerPlayer, team: String) {
        val current = player.level().server
        server = current
        WallwarTeams.ensure(current)
        val board = current.scoreboard
        val scoreboardTeam = board.getPlayerTeam(team) ?: return
        board.addPlayerToTeam(player.scoreboardName, scoreboardTeam)
        val member = members.getOrPut(player.uuid) { Member(player.uuid) }
        if (member.team != team) {
            member.ready = false
            member.started = false
        }
        member.team = team
        member.spectating = false
        MatchMenu.give(player)
        when (phase) {
            Phase.LOBBY -> player.setGameMode(GameType.ADVENTURE)
            Phase.WATCH -> MatchRoster.teleportThenSpectate(player, team)
            Phase.FIGHT -> {
                if (!member.ironGiven) MatchRoster.resetVitals(player)
                MatchRoster.enterFight(player, member)
            }
            Phase.SETTLE -> player.setGameMode(GameType.SPECTATOR)
        }
        MatchPersistence.save(current)
        if (phase == Phase.LOBBY) MatchGui.open(player)
    }

    fun useSkill(player: ServerPlayer, stack: ItemStack, context: net.minecraft.world.item.context.UseOnContext?): net.minecraft.world.InteractionResult {
        val member = members[player.uuid] ?: return net.minecraft.world.InteractionResult.PASS
        val now = player.level().server.overworld().gameTime
        val cast = Classes.useSkill(
            member.classLocked,
            member.classId,
            member.skillReadyAt,
            isFight(),
            now,
            stack,
            context,
        ) ?: return net.minecraft.world.InteractionResult.PASS
        if (cast.message != null) player.sendSystemMessage(Component.literal(cast.message))
        if (cast.readyAt != null) {
            member.skillReadyAt = cast.readyAt
            if (member.classId == Classes.WRECKER) {
                member.wreckerDigUntil = now + MatchRules.WRECKER_ACTIVE_TICKS
            } else {
                Classes.applyEffect(player, member.classId)
            }
            MatchPersistence.save(player.level().server)
        }
        return net.minecraft.world.InteractionResult.SUCCESS
    }

    fun confirmReady(player: ServerPlayer) {
        if (phase != Phase.LOBBY) {
            player.sendSystemMessage(Component.literal("现在不是准备阶段"))
            return
        }
        val member = members[player.uuid]
        if (member == null || member.team.isEmpty()) {
            player.sendSystemMessage(Component.literal("先加入队伍"))
            return
        }
        if (member.ready) {
            player.sendSystemMessage(Component.literal(readyText(player, member.team, "已准备")))
            return
        }
        member.ready = true
        MatchRoster.notifyTeam(player.level().server, member.team, readyText(player, member.team, "已准备"))
        MatchPersistence.save(player.level().server)
        MatchFlow.tryBeginWatch(player.level().server)
    }

    fun toggleTeam(player: ServerPlayer, team: String) {
        if (member(player.uuid)?.team == team) leaveTeam(player) else assign(player, team)
    }

    fun leaveTeam(player: ServerPlayer) {
        if (phase != Phase.LOBBY) {
            player.sendSystemMessage(Component.literal("现在不能退出队伍"))
            return
        }
        val current = player.level().server
        val member = members[player.uuid]
        if (member == null || member.team.isEmpty()) return
        member.team = ""
        member.ready = false
        member.started = false
        current.scoreboard.removePlayerFromTeam(player.scoreboardName)
        MatchPersistence.save(current)
        player.sendSystemMessage(Component.literal("已退出队伍"))
    }

    fun toggleReady(player: ServerPlayer) {
        if (members[player.uuid]?.ready == true) cancelReady(player) else confirmReady(player)
    }

    fun cancelReady(player: ServerPlayer) {
        if (phase != Phase.LOBBY) {
            player.sendSystemMessage(Component.literal("现在不是准备阶段"))
            return
        }
        val member = members[player.uuid] ?: return
        if (!member.ready) return
        member.ready = false
        MatchPersistence.save(player.level().server)
        MatchRoster.notifyTeam(player.level().server, member.team, readyText(player, member.team, "已取消准备"))
    }

    private fun readyText(player: ServerPlayer, team: String, action: String): String {
        val (done, total) = counts(player.level().server, team, readyFlag = true)
        return "$action（$done/$total）"
    }

    fun leaveSpectate(player: ServerPlayer) {
        members[player.uuid]?.spectating = false
        MatchRoster.sendToLobby(player)
        MatchMenu.give(player)
        player.sendSystemMessage(Component.literal("已离开旁观"))
    }

    fun chooseClass(player: ServerPlayer, classId: String) {
        val chosen = Classes.normalize(classId)
        if (chosen !in Classes.selectable) return
        if (phase != Phase.LOBBY) {
            player.sendSystemMessage(Component.literal("现在不能选择职业"))
            return
        }
        val member = members[player.uuid]
        if (member == null || member.team.isEmpty()) {
            player.sendSystemMessage(Component.literal("先加入队伍"))
            return
        }
        member.classId = chosen
        member.classLocked = true
        member.skillReadyAt = 0
        member.wreckerDigUntil = 0
        MatchPersistence.save(player.level().server)
        player.sendSystemMessage(Component.literal("职业：${Classes.name(chosen)}"))
    }

    fun adjustMapSize(player: ServerPlayer, delta: Int) {
        if (phase != Phase.LOBBY) {
            player.sendSystemMessage(Component.literal("地图已经生成"))
            return
        }
        val next = mapSize + delta
        if (next < MatchRules.minimumMapSize()) {
            player.sendSystemMessage(Component.literal("不能再小"))
            return
        }
        mapSize = next
        MatchPersistence.save(player.level().server)
    }

    fun reset(actor: ServerPlayer?) {
        val current = actor?.level()?.server ?: server ?: return
        if (actor != null && !Commands.LEVEL_GAMEMASTERS.check(actor.permissions())) {
            actor.sendSystemMessage(Component.literal("只有管理员可以重置"))
            return
        }
        settleResetAt = -1
        MatchSurrender.clear()
        shina.wallwarRebirth.building.CannonCrew.settle(current, false)
        val arena = current.getLevel(MatchRules.DIMENSION)
        pendingLobby.clear()
        for (player in current.playerList.players.toList()) {
            if (player.level().dimension() == MatchRules.DIMENSION || members.containsKey(player.uuid)) {
                MatchRoster.sendToLobby(player)
            }
        }
        MatchRoster.clearScoreboardTeams(current)
        MatchRoster.discardLooseItems(current)
        MatchWorld.release(current)
        if (arena != null && !ArenaReset.wipe(arena)) {
            actor?.sendSystemMessage(Component.literal("重置地形失败，请看服务器日志"))
        }
        phase = Phase.LOBBY
        worldReady = false
        generating = false
        loadDeadline = -1
        watchBuildTick = -1
        watchEndsAt = 0
        watchSecond = -1
        MatchFlow.clearTerrainTitle(current)
        for (member in members.values) {
            member.team = ""
            member.classId = Classes.MINER
            member.classLocked = true
            member.skillReadyAt = 0
            member.wreckerDigUntil = 0
            member.ready = false
            member.spectating = false
            member.started = false
            member.ironGiven = false
            member.respawnAt = 0
            member.pendingReset = true
            member.heldItems = emptyList()
            member.heldLevel = 0
            member.heldProgress = 0f
        }
        for (player in current.playerList.players) {
            val member = members[player.uuid] ?: continue
            MatchRoster.applyBodyReset(player, member)
            player.clearTitle()
        }
        TeamWarehouses.clear()
        MatchPersistence.save(current)
        MatchRoster.broadcast(current, "对局已重置")
        for (player in MatchRoster.onlineMembers(current)) MatchGui.open(player)
        for (player in current.playerList.players) MatchRoster.offerMenu(player)
    }

    fun onJoin(player: ServerPlayer) {
        shina.wallwarRebirth.building.CannonCrew.rejoin(player)
        val member = members[player.uuid]
        val inThisMatch = inThisMatch(member)
        if (!inThisMatch) {
            val current = player.level().server
            server = current
            if (member != null && MatchRoster.applyBodyReset(player, member)) MatchPersistence.save(current)
            if (player.level().dimension() != current.overworld().dimension()) {
                pendingLobby.add(player.uuid)
            } else {
                MatchRoster.clearArenaRespawn(player)
            }
            MatchRoster.offerMenu(player)
            return
        }
        val present = member ?: return
        val current = player.level().server
        server = current
        if (MatchRoster.applyBodyReset(player, present)) MatchPersistence.save(current)
        when (phase) {
            Phase.LOBBY -> player.setGameMode(GameType.ADVENTURE)
            Phase.WATCH -> {
                sendIntoWatch(player, present)
                if (isTeamed(present)) MatchGui.open(player)
            }
            Phase.FIGHT -> {
                if (isTeamed(present)) {
                    if (present.respawnAt > current.tickCount) {
                        MatchRoster.teleportThenSpectate(player, present.team)
                        val left = present.respawnAt - current.tickCount
                        val seconds = (left + MatchRules.TICKS_PER_SECOND - 1) / MatchRules.TICKS_PER_SECOND
                        MatchRoster.showRespawnTitle(player, seconds)
                    } else {
                        present.respawnAt = 0
                        if (!present.ironGiven) MatchRoster.resetVitals(player)
                        MatchRoster.enterFight(player, present)
                    }
                } else {
                    sendIntoWatch(player, present)
                }
            }
            Phase.SETTLE -> sendIntoSettle(player, present)
        }
        MatchMenu.give(player)
    }

    fun onLeave(current: MinecraftServer) {
        server = current
        if (phase == Phase.LOBBY) MatchFlow.tryBeginWatch(current)
    }

    fun leaveAndSpectate(player: ServerPlayer) {
        val current = player.level().server
        val member = members.getOrPut(player.uuid) { Member(player.uuid) }
        if (member.team.isNotEmpty()) current.scoreboard.removePlayerFromTeam(player.scoreboardName)
        member.team = ""
        member.ready = false
        member.started = false
        member.spectating = true
        MatchPersistence.save(current)
        if (worldReady) {
            spectate(player)
        } else {
            if (phase == Phase.LOBBY) player.setGameMode(GameType.ADVENTURE)
            player.sendSystemMessage(Component.literal("将在观察阶段开始时进入场地"))
        }
        MatchMenu.give(player)
    }

    fun spectate(player: ServerPlayer) {
        val member = members[player.uuid]
        if (member != null && member.team.isNotEmpty()) return
        if (!worldReady) {
            player.sendSystemMessage(Component.literal("现在没有对局"))
            return
        }
        MatchRoster.teleportSpectator(player)
    }

    fun afterRespawn(player: ServerPlayer) {
        val member = members[player.uuid] ?: return
        member.heldItems = emptyList()
        member.heldLevel = 0
        member.heldProgress = 0f
        when (phase) {
            Phase.FIGHT -> {
                if (isTeamed(member)) {
                    MatchRoster.startRespawnWait(player, member)
                } else if (member.spectating && worldReady) {
                    member.respawnAt = 0
                    MatchRoster.teleportSpectator(player)
                } else {
                    member.respawnAt = 0
                    player.clearTitle()
                }
            }
            Phase.WATCH, Phase.SETTLE -> {
                member.respawnAt = 0
                sendIntoSettle(player, member)
            }
            Phase.LOBBY -> {
                member.respawnAt = 0
                if (member.team.isNotEmpty()) player.setGameMode(GameType.ADVENTURE)
            }
        }
        MatchPersistence.save(player.level().server)
    }

    override fun onBaseFallen(level: ServerLevel, team: String) {
        if (phase == Phase.SETTLE) return
        if (level.dimension() != MatchRules.DIMENSION) return
        val current = level.server
        phase = Phase.SETTLE
        MatchSurrender.clear()
        settleResetAt = current.tickCount + MatchRules.SETTLE_RESET_TICKS
        shina.wallwarRebirth.building.CannonCrew.settle(current, true)
        val winner = if (team == WallwarTeams.RED) WallwarTeams.BLUE else WallwarTeams.RED
        val text = "$winner 获胜"
        MatchRoster.broadcast(current, text)
        for (member in members.values) {
            member.respawnAt = 0
            if (member.team == WallwarTeams.RED || member.team == WallwarTeams.BLUE) member.pendingReset = true
        }
        for (player in current.playerList.players) {
            val member = members[player.uuid]
            if (member != null) MatchRoster.applyBodyReset(player, member)
            player.showTitle(Component.literal(text))
            if (isArena(player.level()) || member != null) {
                player.setGameMode(GameType.SPECTATOR)
            }
        }
        MatchPersistence.save(current)
    }

    private fun tickSettleReset(current: MinecraftServer) {
        if (settleResetAt < 0) return
        val left = settleResetAt - current.tickCount
        val seconds = if (left <= 0) 0 else (left + MatchRules.TICKS_PER_SECOND - 1) / MatchRules.TICKS_PER_SECOND
        val text = Component.literal("重置倒计时 $seconds")
        for (player in current.playerList.players) {
            if (isArena(player.level())) player.showActionBar(text)
        }
        if (left <= 0) {
            settleResetAt = -1
            reset(null)
        }
    }

    private fun isTeamed(member: Member): Boolean =
        member.team == WallwarTeams.RED || member.team == WallwarTeams.BLUE

    private fun inThisMatch(member: Member?): Boolean {
        if (member == null || phase == Phase.LOBBY) return false
        return isTeamed(member) || member.spectating
    }

    private fun inThisMatch(player: ServerPlayer): Boolean = inThisMatch(members[player.uuid])

    private fun sendIntoWatch(player: ServerPlayer, member: Member) {
        if (isTeamed(member)) MatchRoster.teleportThenSpectate(player, member.team)
        else if (member.spectating) MatchRoster.teleportSpectator(player)
    }

    private fun sendIntoSettle(player: ServerPlayer, member: Member) {
        if (!worldReady) {
            player.setGameMode(GameType.SPECTATOR)
            return
        }
        if (isTeamed(member)) {
            MatchRoster.teleportSpawn(player, member.team)
            player.setGameMode(GameType.SPECTATOR)
        } else if (member.spectating) {
            MatchRoster.teleportSpectator(player)
        }
    }

    private fun flushLobbyReturns(current: MinecraftServer) {
        if (pendingLobby.isEmpty()) return
        val ids = pendingLobby.toList()
        pendingLobby.clear()
        for (id in ids) {
            val player = current.playerList.getPlayer(id) ?: continue
            if (inThisMatch(player)) continue
            if (player.level().dimension() != current.overworld().dimension()) {
                MatchRoster.sendToLobby(player)
            } else {
                MatchRoster.clearArenaRespawn(player)
            }
            MatchRoster.offerMenu(player)
        }
    }

    fun horizontal(a: BlockPos, b: BlockPos): Double = MatchPersistence.horizontal(a, b)

    fun counts(current: MinecraftServer, team: String, readyFlag: Boolean): Pair<Int, Int> {
        val online = MatchRoster.onlineTeam(current, team)
        val done = online.count { if (readyFlag) it.second.ready else it.second.started }
        return done to online.size
    }





























}
