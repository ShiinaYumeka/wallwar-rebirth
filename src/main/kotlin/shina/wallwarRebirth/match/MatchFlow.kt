package shina.wallwarRebirth.match

import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import shina.wallwarRebirth.building.CannonCrew
import shina.wallwarRebirth.contract.WallwarTeams
import shina.wallwarRebirth.rules.MatchRules
import shina.wallwarRebirth.shop.match.TeamWarehouses
import shina.wallwarRebirth.util.clearTitle
import shina.wallwarRebirth.util.showActionBar
import shina.wallwarRebirth.util.showTitle
import shina.wallwarRebirth.world.ArenaReset
import shina.wallwarRebirth.world.MatchWorld

object MatchFlow {
    internal fun showWatchRemaining(current: MinecraftServer, left: Int) {
        val seconds = (left + MatchRules.TICKS_PER_SECOND - 1) / MatchRules.TICKS_PER_SECOND
        if (seconds == Match.watchSecond) return
        Match.watchSecond = seconds
        val text = Component.literal("剩余 $seconds 秒")
        for (player in current.playerList.players) {
            val onTeam = Match.members[player.uuid]?.team?.isNotEmpty() == true
            if (!onTeam && !Match.isArena(player.level())) continue
            player.showActionBar(text)
        }
    }

    internal fun showTerrainTitle(current: MinecraftServer) {
        val text = Component.literal("正在加载地形")
        for ((player, _) in MatchRoster.onlineTeam(current, WallwarTeams.RED) + MatchRoster.onlineTeam(current, WallwarTeams.BLUE)) {
            player.showTitle(text, fadeIn = 0, stay = 1200, fadeOut = 10)
        }
    }

    internal fun clearTerrainTitle(current: MinecraftServer) {
        for ((player, _) in MatchRoster.onlineTeam(current, WallwarTeams.RED) + MatchRoster.onlineTeam(current, WallwarTeams.BLUE)) {
            player.clearTitle()
        }
    }

    internal fun tryBeginWatch(current: MinecraftServer) {
        if (Match.phase != Match.Phase.LOBBY || Match.generating || Match.watchBuildTick >= 0) return
        if (!MatchRoster.teamDone(current, WallwarTeams.RED) { it.ready && it.classLocked }) return
        if (!MatchRoster.teamDone(current, WallwarTeams.BLUE) { it.ready && it.classLocked }) return
        Match.watchBuildTick = current.tickCount + 1
        showTerrainTitle(current)
    }

    internal fun buildWatch(current: MinecraftServer) {
        if (Match.phase != Match.Phase.LOBBY || Match.generating) return
        if (!MatchRoster.teamDone(current, WallwarTeams.RED) { it.ready && it.classLocked }) {
            clearTerrainTitle(current)
            return
        }
        if (!MatchRoster.teamDone(current, WallwarTeams.BLUE) { it.ready && it.classLocked }) {
            clearTerrainTitle(current)
            return
        }
        Match.generating = true
        CannonCrew.settle(current, false)
        val arena = current.getLevel(MatchRules.DIMENSION)
        if (arena != null) {
            val overworld = current.overworld()
            for (player in current.playerList.players.toList()) {
                if (player.level().dimension() != MatchRules.DIMENSION) continue
                val spawn = overworld.respawnData.pos()
                MatchRoster.teleport(player, overworld, spawn.x + 0.5, spawn.y.toDouble(), spawn.z + 0.5, player.yRot)
            }
            MatchWorld.release(current)
            if (!ArenaReset.wipe(arena)) {
                abortReady(current, "地图重置失败")
                return
            }
        }
        Match.terrainSeed = System.currentTimeMillis()
        MatchWorld.reseed(current, Match.terrainSeed)
        MatchPersistence.save(current)
        if (!MatchWorld.prime(current)) {
            abortReady(current, "地图初始化失败")
            return
        }
        Match.loadDeadline = current.tickCount + 30 * MatchRules.TICKS_PER_SECOND
    }

    internal fun finishWatch(current: MinecraftServer) {
        if (Match.phase != Match.Phase.LOBBY) {
            abortReady(current, "地图初始化失败")
            return
        }
        if (!MatchWorld.loaded(current)) {
            if (Match.loadDeadline in 0..current.tickCount) abortReady(current, "地图初始化失败")
            return
        }
        val built = MatchWorld.build(current)
        if (built == null) {
            abortReady(current, "地图初始化失败")
            return
        }
        Match.generating = false
        Match.loadDeadline = -1
        Match.redBase = built.redBase
        Match.blueBase = built.blueBase
        Match.redSpawn = built.redSpawn
        Match.blueSpawn = built.blueSpawn
        Match.worldReady = true
        Match.phase = Match.Phase.WATCH
        Match.watchEndsAt = current.tickCount + MatchRules.WATCH_TICKS
        Match.watchSecond = -1
        for (member in Match.members.values) member.started = false
        clearTerrainTitle(current)
        for (player in current.playerList.players) {
            val member = Match.members[player.uuid] ?: continue
            if (member.team == WallwarTeams.RED || member.team == WallwarTeams.BLUE) {
                MatchRoster.teleportThenSpectate(player, member.team)
            } else if (member.spectating) {
                MatchRoster.teleportSpectator(player)
            } else {
                continue
            }
            player.closeContainer()
        }
        showWatchRemaining(current, MatchRules.WATCH_TICKS)
        MatchRoster.broadcast(current, "地图已准备好")
        MatchWorld.release(current)
        MatchPersistence.save(current)
    }

    internal fun abortReady(current: MinecraftServer, message: String) {
        Match.generating = false
        Match.loadDeadline = -1
        MatchWorld.release(current)
        Match.watchEndsAt = 0
        Match.watchSecond = -1
        for (member in Match.members.values) member.ready = false
        clearTerrainTitle(current)
        MatchRoster.broadcast(current, message)
        MatchPersistence.save(current)
        for (player in MatchRoster.onlineMembers(current)) MatchGui.open(player)
    }

    internal fun beginFight(current: MinecraftServer) {
        if (Match.phase != Match.Phase.WATCH) return
        Match.watchEndsAt = 0
        Match.watchSecond = -1
        Match.phase = Match.Phase.FIGHT
        TeamWarehouses.clear()
        for ((player, member) in MatchRoster.onlineTeam(current, WallwarTeams.RED) + MatchRoster.onlineTeam(current, WallwarTeams.BLUE)) {
            member.classLocked = true
            member.respawnAt = 0
            MatchRoster.resetVitals(player)
            player.clearTitle()
            MatchRoster.enterFight(player, member)
        }
        MatchRoster.giveStartIngots(current, WallwarTeams.RED)
        MatchRoster.giveStartIngots(current, WallwarTeams.BLUE)
        MatchRoster.broadcast(current, "战斗开始")
        MatchPersistence.save(current)
    }
}
