package shina.wallwarRebirth.match
import shina.wallwarRebirth.profession.SkillInventory

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import net.minecraft.world.level.storage.LevelData
import shina.wallwarRebirth.contract.WallwarTeams
import shina.wallwarRebirth.rules.MatchRules
import shina.wallwarRebirth.util.clearTitle
import shina.wallwarRebirth.util.showTitle

object MatchRoster {
    internal fun offerMenu(player: ServerPlayer) {
        if (Match.phase == Match.Phase.LOBBY && player.isSpectator) player.setGameMode(GameType.ADVENTURE)
        MatchMenu.give(player)
    }

    internal fun tickRespawns(current: MinecraftServer) {
        var changed = false
        for (player in current.playerList.players) {
            val member = Match.members[player.uuid] ?: continue
            if (member.respawnAt <= 0) continue
            val fighting = Match.phase == Match.Phase.FIGHT && (member.team == WallwarTeams.RED || member.team == WallwarTeams.BLUE)
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
        if (changed) MatchPersistence.save(current)
    }

    internal fun startRespawnWait(player: ServerPlayer, member: Match.Member) {
        player.removeAllEffects()
        player.clearFire()
        member.respawnAt = player.level().server.tickCount + MatchRules.RESPAWN_TICKS
        teleportThenSpectate(player, member.team)
        MatchMenu.give(player)
        showRespawnTitle(player, MatchRules.RESPAWN_TICKS / MatchRules.TICKS_PER_SECOND)
    }

    internal fun showRespawnTitle(player: ServerPlayer, seconds: Int) {
        player.showTitle(Component.literal("复活倒计时 $seconds"), fadeIn = 0, stay = 25, fadeOut = 0)
    }

    internal fun applyBodyReset(player: ServerPlayer, member: Match.Member): Boolean {
        if (!member.pendingReset) return false
        member.pendingReset = false
        resetVitals(player)
        clearBelongings(player)
        SkillInventory.strip(player)
        MatchMenu.give(player)
        return true
    }

    internal fun clearBelongings(player: ServerPlayer) {
        player.inventory.clearContent()
        player.containerMenu.carried = ItemStack.EMPTY
        for (slot in EquipmentSlot.entries) player.setItemSlot(slot, ItemStack.EMPTY)
    }

    internal fun enterFight(player: ServerPlayer, member: Match.Member) {
        player.setGameMode(GameType.SURVIVAL)
        rememberSpawn(player, member.team)
        teleportSpawn(player, member.team)
        if (!member.ironGiven) {
            giveKit(player)
            member.ironGiven = true
        }
        MatchMenu.give(player)
        SkillInventory.offer(player)
    }

    internal fun resetVitals(player: ServerPlayer) {
        player.health = player.maxHealth
        val food = player.foodData
        food.foodLevel = 20
        food.setSaturation(5f)
    }

    internal fun giveKit(player: ServerPlayer) {
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

    internal fun giveStartIngots(current: MinecraftServer, team: String) {
        for ((player, _) in onlineTeam(current, team)) {
            for (stack in listOf(
                ItemStack(Items.IRON_INGOT, MatchRules.START_IRON),
                ItemStack(Items.COPPER_INGOT, MatchRules.START_COPPER),
            )) {
                if (!player.inventory.add(stack)) player.drop(stack, false)
            }
        }
    }

    internal fun rememberSpawn(player: ServerPlayer, team: String) {
        val spawn = Match.spawnOf(team) ?: return
        val pos = BlockPos.containing(spawn.x, spawn.y, spawn.z)
        player.setRespawnPosition(
            ServerPlayer.RespawnConfig(LevelData.RespawnData.of(MatchRules.DIMENSION, pos, spawn.yaw, 0f), true),
            false,
        )
    }

    internal fun teleportSpawn(player: ServerPlayer, team: String) {
        val arena = player.level().server.getLevel(MatchRules.DIMENSION) ?: return
        val spawn = Match.spawnOf(team) ?: return
        teleport(player, arena, spawn.x, spawn.y, spawn.z, spawn.yaw)
    }

    internal fun teleportThenSpectate(player: ServerPlayer, team: String) {
        teleportSpawn(player, team)
        player.setGameMode(GameType.SPECTATOR)
    }

    internal fun teleportThenSpectate(
        player: ServerPlayer,
        level: ServerLevel,
        x: Double,
        y: Double,
        z: Double,
        yaw: Float,
    ) {
        teleport(player, level, x, y, z, yaw)
        player.setGameMode(GameType.SPECTATOR)
    }

    internal fun teleportSpectator(player: ServerPlayer) {
        val current = player.level().server
        val arena = current.getLevel(MatchRules.DIMENSION) ?: return
        if (!Match.worldReady) return
        val x = (Match.redSpawn.x + Match.blueSpawn.x) / 2.0
        val y = (Match.redSpawn.y + Match.blueSpawn.y) / 2.0
        val z = (Match.redSpawn.z + Match.blueSpawn.z) / 2.0
        teleportThenSpectate(player, arena, x, y, z, player.yRot)
    }

    internal fun teleport(player: ServerPlayer, level: ServerLevel, x: Double, y: Double, z: Double, yaw: Float) {
        player.teleportTo(level, x, y, z, setOf(), yaw, 0f, false)
    }

    internal fun sendToLobby(player: ServerPlayer) {
        val current = player.level().server
        val overworld = current.overworld()
        val spawn = overworld.respawnData.pos()
        teleport(player, overworld, spawn.x + 0.5, spawn.y.toDouble(), spawn.z + 0.5, player.yRot)
        player.setGameMode(GameType.ADVENTURE)
        rememberLobbySpawn(player)
        player.closeContainer()
        player.clearTitle()
        player.clearFire()
        player.removeAllEffects()
    }

    internal fun rememberLobbySpawn(player: ServerPlayer) {
        val overworld = player.level().server.overworld()
        player.setRespawnPosition(ServerPlayer.RespawnConfig(overworld.respawnData, false), false)
    }

    internal fun clearArenaRespawn(player: ServerPlayer) {
        val config = player.respawnConfig ?: return
        if (config.respawnData.dimension() != MatchRules.DIMENSION) return
        rememberLobbySpawn(player)
    }

    internal fun clearScoreboardTeams(current: MinecraftServer) {
        WallwarTeams.ensure(current)
        val board = current.scoreboard
        for (name in listOf(WallwarTeams.RED, WallwarTeams.BLUE)) {
            val team = board.getPlayerTeam(name) ?: continue
            for (entry in team.players.toList()) board.removePlayerFromTeam(entry)
        }
    }

    internal fun discardLooseItems(current: MinecraftServer) {
        for (level in current.allLevels) {
            val present = ArrayList<ItemEntity>()
            level.allEntities.forEach { entity ->
                if (entity is ItemEntity && !entity.isRemoved) present.add(entity)
            }
            for (entity in present) entity.discard()
        }
    }

    internal fun notifyTeam(current: MinecraftServer, team: String, text: String) {
        for ((player, _) in onlineTeam(current, team)) {
            player.sendSystemMessage(Component.literal(text))
        }
    }

    internal fun teamDone(current: MinecraftServer, team: String, test: (Match.Member) -> Boolean): Boolean {
        val online = onlineTeam(current, team)
        if (online.isEmpty()) return false
        return online.all { test(it.second) }
    }

    internal fun onlineMembers(current: MinecraftServer): List<ServerPlayer> =
        current.playerList.players.filter { Match.members.containsKey(it.uuid) }

    internal fun onlineTeam(current: MinecraftServer, team: String): List<Pair<ServerPlayer, Match.Member>> =
        current.playerList.players.mapNotNull { player ->
            val member = Match.members[player.uuid] ?: return@mapNotNull null
            if (member.team != team) return@mapNotNull null
            player to member
        }

    internal fun broadcast(current: MinecraftServer, text: String) {
        current.playerList.broadcastSystemMessage(Component.literal(text), false)
    }
}
