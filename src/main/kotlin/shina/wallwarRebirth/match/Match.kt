package shina.wallwarRebirth.match

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
import shina.wallwarRebirth.building.SiteCoreBlockEntity
import shina.wallwarRebirth.shop.TeamWarehouses
import shina.wallwarRebirth.monster.TestTeams
import shina.wallwarRebirth.util.clearTitle
import shina.wallwarRebirth.util.showActionBar
import shina.wallwarRebirth.util.showTitle
import java.util.UUID
import kotlin.math.hypot

object Match {
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
    private val members = HashMap<UUID, Member>()
    private var generating = false
    private var loadDeadline: Int = -1
    private var watchBuildTick: Int = -1
    private var watchEndsAt: Int = 0
    private var watchSecond: Int = -1
    private var server: MinecraftServer? = null

    fun attach(current: MinecraftServer) {
        server = current
        load(current)
        MatchWorld.release(current)
    }

    fun stop(current: MinecraftServer) {
        MatchWorld.release(current)
        save(current)
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
            buildWatch(current)
        }
        if (generating) finishWatch(current)
        if (phase == Phase.WATCH) {
            if (watchEndsAt == 0) watchEndsAt = current.tickCount + MatchRules.WATCH_TICKS
            val left = watchEndsAt - current.tickCount
            if (left <= 0) {
                beginFight(current)
            } else {
                showWatchRemaining(current, left)
            }
        }
        tickRespawns(current)
    }

    fun frozen(level: Level): Boolean = level.dimension() == MatchRules.DIMENSION && phase != Phase.FIGHT

    fun isFight(): Boolean = phase == Phase.FIGHT

    fun isArena(level: Level): Boolean = level.dimension() == MatchRules.DIMENSION

    fun restricted(player: ServerPlayer): Boolean {
        if (phase == Phase.FIGHT) return false
        if (isArena(player.level())) return true
        return members.containsKey(player.uuid)
    }

    fun member(id: UUID): Member? = members[id]

    fun bases(): List<Pair<String, BlockPos>> {
        if (!worldReady) return emptyList()
        return listOf(TestTeams.RED to redBase, TestTeams.BLUE to blueBase)
    }

    fun spawnOf(team: String): Spawn? = when (team) {
        TestTeams.RED -> redSpawn
        TestTeams.BLUE -> blueSpawn
        else -> null
    }

    fun assign(player: ServerPlayer, team: String) {
        val current = player.level().server
        server = current
        TestTeams.ensure(current)
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
                teleportSpawn(player, team)
            }
            Phase.FIGHT -> {
                if (!member.ironGiven) resetVitals(player)
                enterFight(player, member)
            }
            Phase.SETTLE -> player.setGameMode(GameType.SPECTATOR)
        }
        save(current)
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
            save(player.level().server)
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
        save(player.level().server)
        tryBeginWatch(player.level().server)
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
        save(player.level().server)
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
        save(player.level().server)
    }

    fun reset(actor: ServerPlayer?) {
        val current = actor?.level()?.server ?: server ?: return
        if (actor != null && !Commands.LEVEL_GAMEMASTERS.check(actor.permissions())) {
            actor.sendSystemMessage(Component.literal("只有管理员可以重置"))
            return
        }
        val arena = current.getLevel(MatchRules.DIMENSION)
        val overworld = current.overworld()
        for (player in current.playerList.players.toList()) {
            if (player.level().dimension() == MatchRules.DIMENSION) {
                val spawn = overworld.respawnData.pos()
                teleport(player, overworld, spawn.x + 0.5, spawn.y.toDouble(), spawn.z + 0.5, player.yRot)
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
        clearTerrainTitle(current)
        for (member in members.values) {
            member.classId = Classes.MINER
            member.classLocked = true
            member.minerUntil = 0
            member.ready = false
            member.started = false
            member.ironGiven = false
            member.respawnAt = 0
            member.pendingReset = member.team == TestTeams.RED || member.team == TestTeams.BLUE
            member.heldItems = emptyList()
            member.heldLevel = 0
            member.heldProgress = 0f
        }
        for (player in current.playerList.players) {
            val member = members[player.uuid] ?: continue
            applyBodyReset(player, member)
            player.clearTitle()
        }
        TeamWarehouses.clear()
        save(current)
        broadcast(current, "对局已重置")
        for (player in onlineMembers(current)) MatchGui.open(player)
        for (player in current.playerList.players) offerMenu(player)
    }

    fun onJoin(player: ServerPlayer) {
        val member = members[player.uuid]
        if (member == null || member.team.isEmpty()) {
            offerMenu(player)
            return
        }
        val current = player.level().server
        server = current
        if (applyBodyReset(player, member)) save(current)
        when (phase) {
            Phase.LOBBY -> player.setGameMode(GameType.ADVENTURE)
            Phase.WATCH -> {
                player.setGameMode(GameType.SPECTATOR)
                teleportSpawn(player, member.team)
                MatchGui.open(player)
            }
            Phase.FIGHT -> {
                if (member.respawnAt > current.tickCount) {
                    player.setGameMode(GameType.SPECTATOR)
                    teleportSpawn(player, member.team)
                    val left = member.respawnAt - current.tickCount
                    val seconds = (left + MatchRules.TICKS_PER_SECOND - 1) / MatchRules.TICKS_PER_SECOND
                    showRespawnTitle(player, seconds)
                } else {
                    member.respawnAt = 0
                    if (!member.ironGiven) resetVitals(player)
                    enterFight(player, member)
                }
            }
            Phase.SETTLE -> {
                player.setGameMode(GameType.SPECTATOR)
                if (worldReady) teleportSpawn(player, member.team)
            }
        }
        MatchMenu.give(player)
    }

    fun onLeave(current: MinecraftServer) {
        server = current
        if (phase == Phase.LOBBY) tryBeginWatch(current)
    }

    fun leaveAndSpectate(player: ServerPlayer) {
        val current = player.level().server
        val member = members[player.uuid]
        if (member != null) {
            member.team = ""
            member.ready = false
            member.started = false
            current.scoreboard.removePlayerFromTeam(player.scoreboardName)
            save(current)
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
        teleport(player, arena, x, y, z, player.yRot)
    }

    fun afterRespawn(player: ServerPlayer) {
        val member = members[player.uuid] ?: return
        member.heldItems = emptyList()
        member.heldLevel = 0
        member.heldProgress = 0f
        when (phase) {
            Phase.FIGHT -> {
                if (member.team == TestTeams.RED || member.team == TestTeams.BLUE) {
                    startRespawnWait(player, member)
                } else {
                    member.respawnAt = 0
                    player.clearTitle()
                }
            }
            Phase.WATCH, Phase.SETTLE -> {
                member.respawnAt = 0
                player.setGameMode(GameType.SPECTATOR)
                if (worldReady) teleportSpawn(player, member.team)
            }
            Phase.LOBBY -> {
                member.respawnAt = 0
                if (member.team.isNotEmpty()) player.setGameMode(GameType.ADVENTURE)
            }
        }
        save(player.level().server)
    }

    fun onBaseFallen(core: SiteCoreBlockEntity) {
        if (phase == Phase.SETTLE) return
        if (core.level?.dimension() != MatchRules.DIMENSION) return
        val current = (core.level as? ServerLevel)?.server ?: server ?: return
        phase = Phase.SETTLE
        val winner = if (core.team == TestTeams.RED) TestTeams.BLUE else TestTeams.RED
        val text = "$winner 获胜"
        broadcast(current, text)
        for (member in members.values) {
            member.respawnAt = 0
            if (member.team == TestTeams.RED || member.team == TestTeams.BLUE) member.pendingReset = true
        }
        for (player in current.playerList.players) {
            val member = members[player.uuid]
            if (member != null) applyBodyReset(player, member)
            player.showTitle(Component.literal(text))
            if (isArena(player.level()) || member != null) {
                player.setGameMode(GameType.SPECTATOR)
            }
        }
        save(current)
    }

    fun counts(current: MinecraftServer, team: String, readyFlag: Boolean): Pair<Int, Int> {
        val online = onlineTeam(current, team)
        val done = online.count { if (readyFlag) it.second.ready else it.second.started }
        return done to online.size
    }

    private fun offerMenu(player: ServerPlayer) {
        if (phase == Phase.LOBBY && player.isSpectator) player.setGameMode(GameType.ADVENTURE)
        MatchMenu.give(player)
    }

    private fun showWatchRemaining(current: MinecraftServer, left: Int) {
        val seconds = (left + MatchRules.TICKS_PER_SECOND - 1) / MatchRules.TICKS_PER_SECOND
        if (seconds == watchSecond) return
        watchSecond = seconds
        val text = Component.literal("剩余 $seconds 秒")
        for (player in current.playerList.players) {
            val onTeam = members[player.uuid]?.team?.isNotEmpty() == true
            if (!onTeam && !isArena(player.level())) continue
            player.showActionBar(text)
        }
    }

    private fun showTerrainTitle(current: MinecraftServer) {
        val text = Component.literal("正在加载地形")
        for ((player, _) in onlineTeam(current, TestTeams.RED) + onlineTeam(current, TestTeams.BLUE)) {
            player.showTitle(text, fadeIn = 0, stay = 1200, fadeOut = 10)
        }
    }

    private fun clearTerrainTitle(current: MinecraftServer) {
        for ((player, _) in onlineTeam(current, TestTeams.RED) + onlineTeam(current, TestTeams.BLUE)) {
            player.clearTitle()
        }
    }

    private fun tryBeginWatch(current: MinecraftServer) {
        if (phase != Phase.LOBBY || generating || watchBuildTick >= 0) return
        if (!teamDone(current, TestTeams.RED) { it.ready && it.classLocked }) return
        if (!teamDone(current, TestTeams.BLUE) { it.ready && it.classLocked }) return
        watchBuildTick = current.tickCount + 1
        showTerrainTitle(current)
    }

    private fun buildWatch(current: MinecraftServer) {
        if (phase != Phase.LOBBY || generating) return
        if (!teamDone(current, TestTeams.RED) { it.ready && it.classLocked }) {
            clearTerrainTitle(current)
            return
        }
        if (!teamDone(current, TestTeams.BLUE) { it.ready && it.classLocked }) {
            clearTerrainTitle(current)
            return
        }
        generating = true
        val arena = current.getLevel(MatchRules.DIMENSION)
        if (arena != null) {
            val overworld = current.overworld()
            for (player in current.playerList.players.toList()) {
                if (player.level().dimension() != MatchRules.DIMENSION) continue
                val spawn = overworld.respawnData.pos()
                teleport(player, overworld, spawn.x + 0.5, spawn.y.toDouble(), spawn.z + 0.5, player.yRot)
            }
            MatchWorld.release(current)
            if (!ArenaReset.wipe(arena)) {
                abortReady(current, "地图重置失败")
                return
            }
        }
        terrainSeed = System.currentTimeMillis()
        MatchWorld.reseed(current, terrainSeed)
        save(current)
        if (!MatchWorld.prime(current)) {
            abortReady(current, "地图初始化失败")
            return
        }
        loadDeadline = current.tickCount + 30 * MatchRules.TICKS_PER_SECOND
    }

    private fun finishWatch(current: MinecraftServer) {
        if (phase != Phase.LOBBY) {
            abortReady(current, "地图初始化失败")
            return
        }
        if (!MatchWorld.loaded(current)) {
            if (loadDeadline in 0..current.tickCount) abortReady(current, "地图初始化失败")
            return
        }
        val built = MatchWorld.build(current)
        if (built == null) {
            abortReady(current, "地图初始化失败")
            return
        }
        generating = false
        loadDeadline = -1
        redBase = built.redBase
        blueBase = built.blueBase
        redSpawn = built.redSpawn
        blueSpawn = built.blueSpawn
        worldReady = true
        phase = Phase.WATCH
        watchEndsAt = current.tickCount + MatchRules.WATCH_TICKS
        watchSecond = -1
        for (member in members.values) member.started = false
        clearTerrainTitle(current)
        for (player in onlineMembers(current)) {
            player.setGameMode(GameType.SPECTATOR)
            teleportSpawn(player, members[player.uuid]?.team ?: "")
            MatchGui.open(player)
        }
        showWatchRemaining(current, MatchRules.WATCH_TICKS)
        broadcast(current, "地图已准备好")
        MatchWorld.release(current)
        save(current)
    }

    private fun abortReady(current: MinecraftServer, message: String) {
        generating = false
        loadDeadline = -1
        MatchWorld.release(current)
        watchEndsAt = 0
        watchSecond = -1
        for (member in members.values) member.ready = false
        clearTerrainTitle(current)
        broadcast(current, message)
        save(current)
        for (player in onlineMembers(current)) MatchGui.open(player)
    }

    private fun beginFight(current: MinecraftServer) {
        if (phase != Phase.WATCH) return
        watchEndsAt = 0
        watchSecond = -1
        phase = Phase.FIGHT
        TeamWarehouses.clear()
        for ((player, member) in onlineTeam(current, TestTeams.RED) + onlineTeam(current, TestTeams.BLUE)) {
            member.classLocked = true
            member.respawnAt = 0
            resetVitals(player)
            player.clearTitle()
            enterFight(player, member)
        }
        giveStartIngots(current, TestTeams.RED)
        giveStartIngots(current, TestTeams.BLUE)
        broadcast(current, "战斗开始")
        save(current)
    }

    private fun tickRespawns(current: MinecraftServer) {
        var changed = false
        for (player in current.playerList.players) {
            val member = members[player.uuid] ?: continue
            if (member.respawnAt <= 0) continue
            val fighting = phase == Phase.FIGHT && (member.team == TestTeams.RED || member.team == TestTeams.BLUE)
            if (!fighting) {
                member.respawnAt = 0
                changed = true
                continue
            }
            if (member.respawnAt > current.tickCount + MatchRules.RESPAWN_TICKS) {
                member.respawnAt = current.tickCount + MatchRules.RESPAWN_TICKS
            }
            val left = member.respawnAt - current.tickCount
            if (left > 0) {
                if (left % MatchRules.TICKS_PER_SECOND == 0) {
                    showRespawnTitle(player, left / MatchRules.TICKS_PER_SECOND)
                }
                continue
            }
            member.respawnAt = 0
            player.clearTitle()
            player.health = player.maxHealth
            player.removeAllEffects()
            player.clearFire()
            enterFight(player, member)
            changed = true
        }
        if (changed) save(current)
    }

    private fun startRespawnWait(player: ServerPlayer, member: Member) {
        player.removeAllEffects()
        player.clearFire()
        player.setGameMode(GameType.SPECTATOR)
        member.respawnAt = player.level().server.tickCount + MatchRules.RESPAWN_TICKS
        teleportSpawn(player, member.team)
        showRespawnTitle(player, MatchRules.RESPAWN_TICKS / MatchRules.TICKS_PER_SECOND)
    }

    private fun showRespawnTitle(player: ServerPlayer, seconds: Int) {
        player.showTitle(Component.literal("复活倒计时 $seconds"), fadeIn = 0, stay = 25, fadeOut = 0)
    }

    private fun applyBodyReset(player: ServerPlayer, member: Member): Boolean {
        if (!member.pendingReset) return false
        member.pendingReset = false
        if (member.team != TestTeams.RED && member.team != TestTeams.BLUE) return true
        resetVitals(player)
        clearBelongings(player)
        MatchMenu.give(player)
        return true
    }

    private fun clearBelongings(player: ServerPlayer) {
        player.inventory.clearContent()
        player.containerMenu.carried = ItemStack.EMPTY
        for (slot in EquipmentSlot.entries) player.setItemSlot(slot, ItemStack.EMPTY)
    }

    private fun enterFight(player: ServerPlayer, member: Member) {
        player.setGameMode(GameType.SURVIVAL)
        rememberSpawn(player, member.team)
        teleportSpawn(player, member.team)
        if (!member.ironGiven) {
            giveKit(player)
            member.ironGiven = true
        }
        MatchMenu.give(player)
    }

    private fun resetVitals(player: ServerPlayer) {
        player.health = player.maxHealth
        val food = player.foodData
        food.foodLevel = 20
        food.setSaturation(5f)
    }

    private fun giveKit(player: ServerPlayer) {
        clearBelongings(player)
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack(Items.COPPER_HELMET))
        player.setItemSlot(EquipmentSlot.CHEST, ItemStack(Items.COPPER_CHESTPLATE))
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack(Items.COPPER_LEGGINGS))
        player.setItemSlot(EquipmentSlot.FEET, ItemStack(Items.COPPER_BOOTS))
        for (item in listOf(
            Items.COPPER_AXE,
            Items.COPPER_PICKAXE,
            Items.COPPER_SHOVEL,
            Items.COPPER_HOE,
        )) {
            val stack = ItemStack(item)
            if (!player.inventory.add(stack)) player.drop(stack, false)
        }
        MatchMenu.give(player)
    }

    private fun giveStartIngots(current: MinecraftServer, team: String) {
        val players = onlineTeam(current, team).map { it.first }
        if (players.isEmpty()) return
        val chosen = players[players.first().level().random.nextInt(players.size)]
        for (stack in listOf(
            ItemStack(Items.IRON_INGOT, MatchRules.START_IRON),
            ItemStack(Items.COPPER_INGOT, MatchRules.START_COPPER),
        )) {
            if (!chosen.inventory.add(stack)) chosen.drop(stack, false)
        }
    }

    private fun rememberSpawn(player: ServerPlayer, team: String) {
        val spawn = spawnOf(team) ?: return
        val pos = BlockPos.containing(spawn.x, spawn.y, spawn.z)
        player.setRespawnPosition(
            ServerPlayer.RespawnConfig(LevelData.RespawnData.of(MatchRules.DIMENSION, pos, spawn.yaw, 0f), true),
            false,
        )
    }

    private fun teleportSpawn(player: ServerPlayer, team: String) {
        val arena = player.level().server.getLevel(MatchRules.DIMENSION) ?: return
        val spawn = spawnOf(team) ?: return
        teleport(player, arena, spawn.x, spawn.y, spawn.z, spawn.yaw)
    }

    private fun teleport(player: ServerPlayer, level: ServerLevel, x: Double, y: Double, z: Double, yaw: Float) {
        player.teleportTo(level, x, y, z, setOf(), yaw, 0f, false)
    }

    private fun teamDone(current: MinecraftServer, team: String, test: (Member) -> Boolean): Boolean {
        val online = onlineTeam(current, team)
        if (online.isEmpty()) return false
        return online.all { test(it.second) }
    }

    private fun onlineMembers(current: MinecraftServer): List<ServerPlayer> =
        current.playerList.players.filter { members.containsKey(it.uuid) }

    private fun onlineTeam(current: MinecraftServer, team: String): List<Pair<ServerPlayer, Member>> =
        current.playerList.players.mapNotNull { player ->
            val member = members[player.uuid] ?: return@mapNotNull null
            if (member.team != team) return@mapNotNull null
            player to member
        }

    private fun broadcast(current: MinecraftServer, text: String) {
        current.playerList.broadcastSystemMessage(Component.literal(text), false)
    }

    private fun file(current: MinecraftServer) =
        current.getWorldPath(LevelResource.ROOT).resolve("data").resolve("wallwar-match.nbt")

    private fun save(current: MinecraftServer) {
        val tag = CompoundTag()
        tag.putString("Phase", phase.name)
        tag.putBoolean("WorldReady", worldReady)
        tag.putInt("MapSize", mapSize)
        tag.putLong("TerrainSeed", terrainSeed)
        tag.putLong("RedBase", redBase.asLong())
        tag.putLong("BlueBase", blueBase.asLong())
        writeSpawn(tag, "RedSpawn", redSpawn)
        writeSpawn(tag, "BlueSpawn", blueSpawn)
        val list = ListTag()
        val ops = current.registryAccess().createSerializationContext(NbtOps.INSTANCE)
        for (member in members.values) {
            val entry = CompoundTag()
            entry.putString("Id", member.id.toString())
            entry.putString("Team", member.team)
            entry.putString("ClassId", member.classId)
            entry.putBoolean("ClassLocked", member.classLocked)
            entry.putLong("MinerUntil", member.minerUntil)
            entry.putBoolean("Ready", member.ready)
            entry.putBoolean("Started", member.started)
            entry.putBoolean("IronGiven", member.ironGiven)
            entry.putInt("RespawnAt", member.respawnAt)
            entry.putBoolean("PendingReset", member.pendingReset)
            entry.putInt("HeldLevel", member.heldLevel)
            entry.putFloat("HeldProgress", member.heldProgress)
            val items = ListTag()
            for (stack in member.heldItems) {
                val saved = ItemStack.CODEC.encodeStart(ops, stack).resultOrPartial { }.orElse(null)
                if (saved is CompoundTag) items.add(saved)
            }
            entry.put("HeldItems", items)
            list.add(entry)
        }
        tag.put("Members", list)
        TeamWarehouses.write(tag, ops)
        val path = file(current)
        java.nio.file.Files.createDirectories(path.parent)
        NbtIo.writeCompressed(tag, path)
    }

    private fun load(current: MinecraftServer) {
        val path = file(current)
        if (!java.nio.file.Files.exists(path)) return
        val tag = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap())
        phase = runCatching { Phase.valueOf(tag.getStringOr("Phase", Phase.LOBBY.name)) }.getOrDefault(Phase.LOBBY)
        worldReady = tag.getBooleanOr("WorldReady", false)
        val loadedSize = tag.getIntOr("MapSize", MatchRules.MAP_SIZE)
        mapSize = if (loadedSize >= MatchRules.minimumMapSize() && (loadedSize - MatchRules.MAP_SIZE) % MatchRules.MAP_SIZE_STEP == 0) {
            loadedSize
        } else {
            MatchRules.MAP_SIZE
        }
        terrainSeed = tag.getLongOr("TerrainSeed", 0L)
        if (terrainSeed != 0L) MatchWorld.reseed(current, terrainSeed)
        redBase = BlockPos.of(tag.getLongOr("RedBase", 0L))
        blueBase = BlockPos.of(tag.getLongOr("BlueBase", 0L))
        redSpawn = readSpawn(tag, "RedSpawn")
        blueSpawn = readSpawn(tag, "BlueSpawn")
        members.clear()
        val ops = current.registryAccess().createSerializationContext(NbtOps.INSTANCE)
        TeamWarehouses.read(tag, ops)
        val list = tag.getListOrEmpty("Members")
        for (index in 0 until list.size) {
            val entry = list.getCompoundOrEmpty(index)
            val id = runCatching { UUID.fromString(entry.getStringOr("Id", "")) }.getOrNull() ?: continue
            val member = Member(id)
            member.team = entry.getStringOr("Team", "")
            member.classId = Classes.MINER
            member.classLocked = true
            member.minerUntil = entry.getLongOr("MinerUntil", 0L)
            member.ready = entry.getBooleanOr("Ready", false)
            member.started = entry.getBooleanOr("Started", false)
            member.ironGiven = entry.getBooleanOr("IronGiven", false)
            member.respawnAt = entry.getIntOr("RespawnAt", 0)
            member.pendingReset = entry.getBooleanOr("PendingReset", false)
            member.heldLevel = entry.getIntOr("HeldLevel", 0)
            member.heldProgress = entry.getFloatOr("HeldProgress", 0f)
            val items = entry.getListOrEmpty("HeldItems")
            val stacks = ArrayList<ItemStack>()
            for (itemIndex in 0 until items.size) {
                val saved = items.getCompoundOrEmpty(itemIndex)
                val stack = ItemStack.CODEC.parse(ops, saved).resultOrPartial { }.orElse(ItemStack.EMPTY)
                if (stack != null) {
                    stacks.add(stack)
                }
            }
            member.heldItems = stacks
            members[id] = member
        }
    }

    private fun writeSpawn(tag: CompoundTag, key: String, spawn: Spawn) {
        tag.putDouble(key + "X", spawn.x)
        tag.putDouble(key + "Y", spawn.y)
        tag.putDouble(key + "Z", spawn.z)
        tag.putFloat(key + "Yaw", spawn.yaw)
    }

    private fun readSpawn(tag: CompoundTag, key: String): Spawn = Spawn(
        tag.getDoubleOr(key + "X", 0.5),
        tag.getDoubleOr(key + "Y", 80.0),
        tag.getDoubleOr(key + "Z", 0.5),
        tag.getFloatOr(key + "Yaw", 0f),
    )

    fun horizontal(a: BlockPos, b: BlockPos): Double {
        val dx = (a.x - b.x).toDouble()
        val dz = (a.z - b.z).toDouble()
        return hypot(dx, dz)
    }
}
