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
import net.minecraft.world.level.storage.LevelResource
import net.minecraft.world.level.Level
import shina.wallwarRebirth.building.SiteCoreBlockEntity
import shina.wallwarRebirth.item.ModItems
import shina.wallwarRebirth.monster.TestTeams
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
        var heldItems: List<ItemStack> = emptyList()
        var heldLevel: Int = 0
        var heldProgress: Float = 0f
    }

    var phase: Phase = Phase.LOBBY
    var worldReady: Boolean = false
    var redBase: BlockPos = BlockPos.ZERO
    var blueBase: BlockPos = BlockPos.ZERO
    var redSpawn: Spawn = Spawn(0.5, 80.0, 0.5, 0f)
    var blueSpawn: Spawn = Spawn(0.5, 80.0, 0.5, 0f)
    private val members = HashMap<UUID, Member>()
    private var generating = false
    private var server: MinecraftServer? = null

    fun attach(current: MinecraftServer) {
        server = current
        load(current)
    }

    fun stop(current: MinecraftServer) {
        save(current)
    }

    fun tick(current: MinecraftServer) {
        server = current
        val arena = current.getLevel(MatchRules.DIMENSION)
        if (arena != null) {
            if (ArenaReset.holding()) ArenaReset.forget(arena)
            if (arena.dayTime != MatchRules.DAY_TIME) arena.dayTime = MatchRules.DAY_TIME
            if (arena.isRaining || arena.isThundering) arena.setWeatherParameters(6000, 0, false, false)
        }
        for (player in current.playerList.players) {
            val member = members[player.uuid] ?: continue
            if (member.respawnAt == 0 || current.tickCount < member.respawnAt) continue
            member.respawnAt = 0
            if (phase == Phase.FIGHT) {
                enterFight(player, member)
            } else {
                player.setGameMode(GameType.SPECTATOR)
            }
            save(current)
        }
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
        when (phase) {
            Phase.LOBBY -> player.setGameMode(GameType.ADVENTURE)
            Phase.WATCH -> {
                player.setGameMode(GameType.SPECTATOR)
                teleportSpawn(player, team)
            }
            Phase.FIGHT -> enterFight(player, member)
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

    fun confirmStart(player: ServerPlayer) {
        if (phase != Phase.WATCH) {
            player.sendSystemMessage(Component.literal("现在不是观察阶段"))
            return
        }
        val member = members[player.uuid]
        if (member == null || member.team.isEmpty()) {
            player.sendSystemMessage(Component.literal("先加入队伍"))
            return
        }
        if (member.started) {
            player.sendSystemMessage(Component.literal("已确认开始"))
            return
        }
        member.started = true
        player.sendSystemMessage(Component.literal("已确认开始"))
        save(player.level().server)
        tryBeginFight(player.level().server)
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
        if (arena != null && !ArenaReset.wipe(arena)) {
            actor?.sendSystemMessage(Component.literal("重置地形失败，请看服务器日志"))
        }
        phase = Phase.LOBBY
        worldReady = false
        generating = false
        for (member in members.values) {
            member.classId = Classes.MINER
            member.classLocked = true
            member.minerUntil = 0
            member.ready = false
            member.started = false
            member.ironGiven = false
            member.respawnAt = 0
            member.heldItems = emptyList()
            member.heldLevel = 0
            member.heldProgress = 0f
        }
        save(current)
        broadcast(current, "对局已重置")
        for (player in onlineMembers(current)) MatchGui.open(player)
    }

    fun onJoin(player: ServerPlayer) {
        val member = members[player.uuid] ?: return
        val current = player.level().server
        server = current
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
                } else {
                    member.respawnAt = 0
                    enterFight(player, member)
                }
            }
            Phase.SETTLE -> {
                player.setGameMode(GameType.SPECTATOR)
                if (worldReady) teleportSpawn(player, member.team)
            }
        }
    }

    fun onLeave(current: MinecraftServer) {
        server = current
        if (phase == Phase.LOBBY) tryBeginWatch(current)
        if (phase == Phase.WATCH) tryBeginFight(current)
    }

    fun holdDeath(player: ServerPlayer) {
        if (phase != Phase.FIGHT) return
        val member = members[player.uuid] ?: return
        if (member.team != TestTeams.RED && member.team != TestTeams.BLUE) return
        val items = ArrayList<ItemStack>()
        for (slot in 0 until player.inventory.containerSize) items.add(player.inventory.getItem(slot).copy())
        member.heldItems = items
        member.heldLevel = player.experienceLevel
        member.heldProgress = player.experienceProgress
        player.inventory.clearContent()
        player.experienceLevel = 0
        player.experienceProgress = 0f
        save(player.level().server)
    }

    fun restoreDeath(player: ServerPlayer) {
        val member = members[player.uuid] ?: return
        if (member.heldItems.isEmpty() && member.heldLevel == 0) {
            if (phase != Phase.FIGHT) player.setGameMode(GameType.SPECTATOR)
            return
        }
        for (slot in member.heldItems.indices) {
            if (slot < player.inventory.containerSize) player.inventory.setItem(slot, member.heldItems[slot].copy())
        }
        player.experienceLevel = member.heldLevel
        player.experienceProgress = member.heldProgress
        member.heldItems = emptyList()
        member.heldLevel = 0
        member.heldProgress = 0f
        if (phase == Phase.FIGHT) {
            player.setGameMode(GameType.SPECTATOR)
            member.respawnAt = player.level().server.tickCount + MatchRules.RESPAWN_TICKS
            teleportSpawn(player, member.team)
        } else {
            player.setGameMode(GameType.SPECTATOR)
            member.respawnAt = 0
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
        for (player in current.playerList.players) {
            player.showTitle(Component.literal(text))
            if (isArena(player.level()) || members.containsKey(player.uuid)) {
                player.setGameMode(GameType.SPECTATOR)
            }
            members[player.uuid]?.respawnAt = 0
        }
        save(current)
    }

    fun counts(current: MinecraftServer, team: String, readyFlag: Boolean): Pair<Int, Int> {
        val online = onlineTeam(current, team)
        val done = online.count { if (readyFlag) it.second.ready else it.second.started }
        return done to online.size
    }

    private fun tryBeginWatch(current: MinecraftServer) {
        if (phase != Phase.LOBBY || generating) return
        if (!teamDone(current, TestTeams.RED) { it.ready && it.classLocked }) return
        if (!teamDone(current, TestTeams.BLUE) { it.ready && it.classLocked }) return
        generating = true
        val arena = current.getLevel(MatchRules.DIMENSION)
        if (arena != null) {
            val overworld = current.overworld()
            for (player in current.playerList.players.toList()) {
                if (player.level().dimension() != MatchRules.DIMENSION) continue
                val spawn = overworld.respawnData.pos()
                teleport(player, overworld, spawn.x + 0.5, spawn.y.toDouble(), spawn.z + 0.5, player.yRot)
            }
            if (!ArenaReset.wipe(arena)) {
                abortReady(current, "地图重置失败")
                return
            }
        }
        val built = MatchWorld.build(current)
        generating = false
        if (built == null) {
            abortReady(current, "地图初始化失败")
            return
        }
        redBase = built.redBase
        blueBase = built.blueBase
        redSpawn = built.redSpawn
        blueSpawn = built.blueSpawn
        worldReady = true
        phase = Phase.WATCH
        for (member in members.values) member.started = false
        for (player in onlineMembers(current)) {
            player.setGameMode(GameType.SPECTATOR)
            teleportSpawn(player, members[player.uuid]?.team ?: "")
            MatchGui.open(player)
        }
        broadcast(current, "地图已准备好，确认开始后进入战斗")
        save(current)
    }

    private fun abortReady(current: MinecraftServer, message: String) {
        generating = false
        for (member in members.values) member.ready = false
        broadcast(current, message)
        save(current)
        for (player in onlineMembers(current)) MatchGui.open(player)
    }

    private fun tryBeginFight(current: MinecraftServer) {
        if (phase != Phase.WATCH) return
        if (!teamDone(current, TestTeams.RED) { it.started && it.classLocked }) return
        if (!teamDone(current, TestTeams.BLUE) { it.started && it.classLocked }) return
        phase = Phase.FIGHT
        for ((player, member) in onlineTeam(current, TestTeams.RED) + onlineTeam(current, TestTeams.BLUE)) {
            member.classLocked = true
            enterFight(player, member)
        }
        giveReactor(current, TestTeams.RED)
        giveReactor(current, TestTeams.BLUE)
        broadcast(current, "战斗开始")
        save(current)
    }

    private fun enterFight(player: ServerPlayer, member: Member) {
        player.setGameMode(GameType.SURVIVAL)
        teleportSpawn(player, member.team)
        if (!member.ironGiven) {
            giveKit(player)
            member.ironGiven = true
        }
    }

    private fun giveKit(player: ServerPlayer) {
        player.inventory.clearContent()
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack(Items.COPPER_HELMET))
        player.setItemSlot(EquipmentSlot.CHEST, ItemStack(Items.COPPER_CHESTPLATE))
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack(Items.COPPER_LEGGINGS))
        player.setItemSlot(EquipmentSlot.FEET, ItemStack(Items.COPPER_BOOTS))
        for (item in listOf(
            Items.COPPER_SWORD,
            Items.COPPER_AXE,
            Items.COPPER_PICKAXE,
            Items.COPPER_SHOVEL,
            Items.COPPER_HOE,
            Items.COPPER_SPEAR,
        )) {
            val stack = ItemStack(item)
            if (!player.inventory.add(stack)) player.drop(stack, false)
        }
    }

    private fun giveReactor(current: MinecraftServer, team: String) {
        val players = onlineTeam(current, team).map { it.first }
        if (players.isEmpty()) return
        val chosen = players[players.first().level().random.nextInt(players.size)]
        val stack = ItemStack(ModItems.CORE_REACTOR)
        if (!chosen.inventory.add(stack)) chosen.drop(stack, false)
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
        redBase = BlockPos.of(tag.getLongOr("RedBase", 0L))
        blueBase = BlockPos.of(tag.getLongOr("BlueBase", 0L))
        redSpawn = readSpawn(tag, "RedSpawn")
        blueSpawn = readSpawn(tag, "BlueSpawn")
        members.clear()
        val ops = current.registryAccess().createSerializationContext(NbtOps.INSTANCE)
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
            member.heldLevel = entry.getIntOr("HeldLevel", 0)
            member.heldProgress = entry.getFloatOr("HeldProgress", 0f)
            val items = entry.getListOrEmpty("HeldItems")
            val stacks = ArrayList<ItemStack>()
            for (itemIndex in 0 until items.size) {
                val saved = items.getCompoundOrEmpty(itemIndex)
                val stack = ItemStack.CODEC.parse(ops, saved).resultOrPartial { }.orElse(ItemStack.EMPTY)
                stacks.add(stack)
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
