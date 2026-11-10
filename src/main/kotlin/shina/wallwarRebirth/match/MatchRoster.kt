package shina.wallwarRebirth.match

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import net.minecraft.world.level.storage.LevelData
import shina.wallwarRebirth.monster.TestTeams
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
            val fighting = Match.phase == Match.Phase.FIGHT && (member.team == TestTeams.RED || member.team == TestTeams.BLUE)
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
        player.setGameMode(GameType.SPECTATOR)
        member.respawnAt = player.level().server.tickCount + MatchRules.RESPAWN_TICKS
        teleportSpawn(player, member.team)
        showRespawnTitle(player, MatchRules.RESPAWN_TICKS / MatchRules.TICKS_PER_SECOND)
    }

    internal fun showRespawnTitle(player: ServerPlayer, seconds: Int) {
        player.showTitle(Component.literal("复活倒计时 $seconds"), fadeIn = 0, stay = 25, fadeOut = 0)
    }

    internal fun applyBodyReset(player: ServerPlayer, member: Match.Member): Boolean {
        if (!member.pendingReset) return false
        member.pendingReset = false
        if (member.team != TestTeams.RED && member.team != TestTeams.BLUE) return true
        resetVitals(player)
        clearBelongings(player)
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
        PortableShop.offer(player)
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

    internal fun teleport(player: ServerPlayer, level: ServerLevel, x: Double, y: Double, z: Double, yaw: Float) {
        player.teleportTo(level, x, y, z, setOf(), yaw, 0f, false)
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
