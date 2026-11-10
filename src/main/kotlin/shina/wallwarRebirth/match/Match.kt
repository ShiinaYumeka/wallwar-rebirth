package shina.wallwarRebirth.match
import shina.wallwarRebirth.shop.match.PortableShop
import shina.wallwarRebirth.profession.Classes
import shina.wallwarRebirth.rules.MatchRules
import shina.wallwarRebirth.world.ArenaReset
import shina.wallwarRebirth.world.MatchWorld

import net.minecraft.commands.Commands
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtOps
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import net.minecraft.world.level.gamerules.GameRules
import net.minecraft.world.level.storage.LevelData
import net.minecraft.world.level.storage.LevelResource
import net.minecraft.world.level.Level
import shina.wallwarRebirth.contract.MatchPort
import shina.wallwarRebirth.contract.WallwarTeams
import shina.wallwarRebirth.shop.match.TeamWarehouses
import shina.wallwarRebirth.util.clearTitle
import shina.wallwarRebirth.util.showActionBar
import shina.wallwarRebirth.util.showTitle
import java.util.UUID
import kotlin.math.hypot

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
        var minerUntil: Long = 0
        var ready: Boolean = false
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
        if (phase == Phase.FIGHT) PortableShop.tick(current)
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
        member.classId = Classes.MINER
        member.classLocked = true
        MatchMenu.give(player)
        when (phase) {
            Phase.LOBBY -> player.setGameMode(GameType.ADVENTURE)
            Phase.WATCH -> {
                player.setGameMode(GameType.SPECTATOR)
                MatchRoster.teleportSpawn(player, team)
            }
            Phase.FIGHT -> {
                if (!member.ironGiven) MatchRoster.resetVitals(player)
                MatchRoster.enterFight(player, member)
            }
            Phase.SETTLE -> player.setGameMode(GameType.SPECTATOR)
        }
        MatchPersistence.save(current)
        if (phase == Phase.LOBBY || phase == Phase.WATCH) MatchGui.open(player)
    }

    fun castMiner(player: ServerPlayer, stack: ItemStack): Boolean {
        val member = members[player.uuid] ?: return false
        val now = player.level().server.overworld().gameTime
        val cast = Classes.castMiner(
            member.classLocked,
            member.classId,
            member.minerUntil,
            isFight(),
            now,
            stack,
        ) ?: return false
        player.sendSystemMessage(Component.literal(cast.message))
        val until = cast.minerUntil
        if (until != null) {
            member.minerUntil = until
            MatchPersistence.save(player.level().server)
        }
        return true
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
            player.sendSystemMessage(Component.literal("已准备"))
            return
        }
        member.ready = true
        player.sendSystemMessage(Component.literal("已准备"))
        MatchPersistence.save(player.level().server)
        MatchFlow.tryBeginWatch(player.level().server)
    }

    fun chooseClass(player: ServerPlayer, classId: String) {
        if (classId != Classes.MINER) return
        if (phase != Phase.LOBBY) {
            player.sendSystemMessage(Component.literal("现在不能选择职业"))
            return
        }
        val member = members[player.uuid]
        if (member == null || member.team.isEmpty()) {
            player.sendSystemMessage(Component.literal("先加入队伍"))
            return
        }
        member.classId = Classes.MINER
        member.classLocked = true
        MatchPersistence.save(player.level().server)
        player.sendSystemMessage(Component.literal("职业：矿工"))
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
        shina.wallwarRebirth.building.CannonCrew.settle(current, false)
        val arena = current.getLevel(MatchRules.DIMENSION)
        val overworld = current.overworld()
        for (player in current.playerList.players.toList()) {
            if (player.level().dimension() == MatchRules.DIMENSION) {
                val spawn = overworld.respawnData.pos()
                MatchRoster.teleport(player, overworld, spawn.x + 0.5, spawn.y.toDouble(), spawn.z + 0.5, player.yRot)
            }
            if (members.containsKey(player.uuid)) player.setGameMode(GameType.ADVENTURE)
        }
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
            member.classId = Classes.MINER
            member.classLocked = true
            member.minerUntil = 0
            member.ready = false
            member.started = false
            member.ironGiven = false
            member.respawnAt = 0
            member.pendingReset = member.team == WallwarTeams.RED || member.team == WallwarTeams.BLUE
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
        if (member == null || member.team.isEmpty()) {
            MatchRoster.offerMenu(player)
            return
        }
        val current = player.level().server
        server = current
        if (MatchRoster.applyBodyReset(player, member)) MatchPersistence.save(current)
        when (phase) {
            Phase.LOBBY -> player.setGameMode(GameType.ADVENTURE)
            Phase.WATCH -> {
                player.setGameMode(GameType.SPECTATOR)
                MatchRoster.teleportSpawn(player, member.team)
                MatchGui.open(player)
            }
            Phase.FIGHT -> {
                if (member.respawnAt > current.tickCount) {
                    player.setGameMode(GameType.SPECTATOR)
                    MatchRoster.teleportSpawn(player, member.team)
                    val left = member.respawnAt - current.tickCount
                    val seconds = (left + MatchRules.TICKS_PER_SECOND - 1) / MatchRules.TICKS_PER_SECOND
                    MatchRoster.showRespawnTitle(player, seconds)
                } else {
                    member.respawnAt = 0
                    if (!member.ironGiven) MatchRoster.resetVitals(player)
                    MatchRoster.enterFight(player, member)
                }
            }
            Phase.SETTLE -> {
                player.setGameMode(GameType.SPECTATOR)
                if (worldReady) MatchRoster.teleportSpawn(player, member.team)
            }
        }
        MatchMenu.give(player)
    }

    fun onLeave(current: MinecraftServer) {
        server = current
        if (phase == Phase.LOBBY) MatchFlow.tryBeginWatch(current)
    }

    fun leaveAndSpectate(player: ServerPlayer) {
        val current = player.level().server
        val member = members[player.uuid]
        if (member != null) {
            member.team = ""
            member.ready = false
            member.started = false
            current.scoreboard.removePlayerFromTeam(player.scoreboardName)
            MatchPersistence.save(current)
        }
        MatchMenu.give(player)
        if (phase == Phase.LOBBY) player.setGameMode(GameType.ADVENTURE)
        spectate(player)
    }

    fun spectate(player: ServerPlayer) {
        val member = members[player.uuid]
        if (member != null && member.team.isNotEmpty()) return
        val current = player.level().server
        val arena = current.getLevel(MatchRules.DIMENSION)
        if (!worldReady || arena == null) {
            player.sendSystemMessage(Component.literal("现在没有对局"))
            return
        }
        player.setGameMode(GameType.SPECTATOR)
        val x = (redSpawn.x + blueSpawn.x) / 2.0
        val y = (redSpawn.y + blueSpawn.y) / 2.0
        val z = (redSpawn.z + blueSpawn.z) / 2.0
        MatchRoster.teleport(player, arena, x, y, z, player.yRot)
    }

    fun afterRespawn(player: ServerPlayer) {
        val member = members[player.uuid] ?: return
        member.heldItems = emptyList()
        member.heldLevel = 0
        member.heldProgress = 0f
        when (phase) {
            Phase.FIGHT -> {
                if (member.team == WallwarTeams.RED || member.team == WallwarTeams.BLUE) {
                    MatchRoster.startRespawnWait(player, member)
                } else {
                    member.respawnAt = 0
                    player.clearTitle()
                }
            }
            Phase.WATCH, Phase.SETTLE -> {
                member.respawnAt = 0
                player.setGameMode(GameType.SPECTATOR)
                if (worldReady) MatchRoster.teleportSpawn(player, member.team)
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

    fun horizontal(a: BlockPos, b: BlockPos): Double = MatchPersistence.horizontal(a, b)

    fun counts(current: MinecraftServer, team: String, readyFlag: Boolean): Pair<Int, Int> {
        val online = MatchRoster.onlineTeam(current, team)
        val done = online.count { if (readyFlag) it.second.ready else it.second.started }
        return done to online.size
    }





























}
