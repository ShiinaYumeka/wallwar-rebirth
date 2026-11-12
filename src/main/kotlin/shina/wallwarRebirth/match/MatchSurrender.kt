package shina.wallwarRebirth.match

import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import shina.wallwarRebirth.contract.WallwarTeams
import shina.wallwarRebirth.shop.match.PortableShop
import java.util.UUID

object MatchSurrender {
    private val agreed = HashMap<String, HashSet<UUID>>()

    fun clear() {
        agreed.clear()
    }

    fun names(team: String): List<String> {
        val current = Match.server ?: return emptyList()
        val votes = agreed[team] ?: return emptyList()
        return MatchRoster.onlineTeam(current, team)
            .filter { it.first.uuid in votes }
            .map { it.first.scoreboardName }
    }

    fun toggle(player: ServerPlayer) {
        if (Match.phase != Match.Phase.FIGHT) {
            player.sendSystemMessage(Component.literal("只有战斗阶段可以投降"))
            return
        }
        val member = Match.members[player.uuid]
        val team = member?.team ?: ""
        if (team != WallwarTeams.RED && team != WallwarTeams.BLUE) {
            player.sendSystemMessage(Component.literal("只有队伍成员可以投降"))
            return
        }
        if (PortableShop.baseOf(team) == null) {
            player.sendSystemMessage(Component.literal("基地已经不在"))
            return
        }
        val votes = agreed.getOrPut(team) { HashSet() }
        if (player.uuid in votes) {
            votes.remove(player.uuid)
            player.sendSystemMessage(Component.literal("已撤回投降"))
        } else {
            votes.add(player.uuid)
            player.sendSystemMessage(Component.literal("已同意投降"))
        }
        if (allAgreed(team)) {
            val world = player.level().server.getLevel(shina.wallwarRebirth.rules.MatchRules.DIMENSION) ?: return
            Match.onBaseFallen(world, team)
        }
    }

    private fun allAgreed(team: String): Boolean {
        val current = Match.server ?: return false
        val online = MatchRoster.onlineTeam(current, team)
        if (online.isEmpty()) return false
        val votes = agreed[team] ?: return false
        return online.all { it.first.uuid in votes }
    }
}
