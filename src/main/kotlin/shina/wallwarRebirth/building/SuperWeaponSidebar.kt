package shina.wallwarRebirth.building

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.numbers.FixedFormat
import net.minecraft.server.MinecraftServer
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.ScoreHolder
import net.minecraft.world.scores.Scoreboard
import net.minecraft.world.scores.criteria.ObjectiveCriteria
import shina.wallwarRebirth.contract.WallwarTeams
import shina.wallwarRebirth.match.Match
import shina.wallwarRebirth.rules.MatchRules
import kotlin.math.ceil

object SuperWeaponSidebar {
    private const val OBJECTIVE = "ww_super"
    private val READY_SCORE = FixedFormat(Component.literal("就绪").withStyle(ChatFormatting.GREEN))

    fun tick(server: MinecraftServer) {
        val board = server.scoreboard
        if (Match.phase == Match.Phase.LOBBY) {
            clear(board)
            return
        }
        val arena = server.getLevel(MatchRules.DIMENSION)
        if (arena == null) {
            clear(board)
            return
        }
        val lines = ArrayList<Line>()
        for (core in SiteCores.all()) {
            if (core.level !== arena || core.isRemoved || core.health <= 0) continue
            if (!SuperWeapons.isSuperWeapon(core.buildingId)) continue
            val definition = Buildings.byId(core.buildingId) ?: continue
            val seconds = if (core.weaponCooldown <= 0) {
                0
            } else {
                ceil(core.weaponCooldown / MatchRules.TICKS_PER_SECOND.toDouble()).toInt()
            }
            lines.add(Line(definition.displayName, seconds, colorOf(core.team), core.team, core.blockPos.x, core.blockPos.z, core.blockPos.y, core.buildingId))
        }
        lines.sortWith(compareBy({ it.team }, { it.id }, { it.x }, { it.z }, { it.y }))
        if (lines.isEmpty()) {
            clear(board)
            return
        }
        removeLegacyTeams(board)
        val objective = objective(board)
        board.setDisplayObjective(DisplaySlot.SIDEBAR, objective)
        val keep = HashSet<String>()
        for ((index, line) in lines.withIndex()) {
            val entry = "wws$index"
            keep.add(entry)
            val score = board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(entry), objective)
            score.display(Component.literal(line.name).withStyle(line.color))
            if (line.seconds <= 0) {
                score.set(0)
                score.numberFormatOverride(READY_SCORE)
            } else {
                score.set(line.seconds)
                score.numberFormatOverride(null)
            }
        }
        prune(board, objective, keep)
    }

    fun clear(server: MinecraftServer) {
        clear(server.scoreboard)
    }

    private fun clear(board: Scoreboard) {
        val objective = board.getObjective(OBJECTIVE) ?: return
        if (board.getDisplayObjective(DisplaySlot.SIDEBAR) === objective) {
            board.setDisplayObjective(DisplaySlot.SIDEBAR, null)
        }
        prune(board, objective, emptySet())
        board.removeObjective(objective)
        removeLegacyTeams(board)
    }

    private fun removeLegacyTeams(board: Scoreboard) {
        for (index in 0..15) {
            val team = board.getPlayerTeam("wwt$index") ?: continue
            for (member in ArrayList(team.players)) board.removePlayerFromTeam(member, team)
            board.removePlayerTeam(team)
        }
    }

    private fun objective(board: Scoreboard): Objective {
        board.getObjective(OBJECTIVE)?.let {
            it.setNumberFormat(null)
            return it
        }
        return board.addObjective(
            OBJECTIVE,
            ObjectiveCriteria.DUMMY,
            Component.literal("超级武器"),
            ObjectiveCriteria.RenderType.INTEGER,
            false,
            null,
        )
    }

    private fun prune(board: Scoreboard, objective: Objective, keep: Set<String>) {
        val scores = ArrayList(board.listPlayerScores(objective))
        for (score in scores) {
            val name = score.owner()
            if (name in keep) continue
            board.resetSinglePlayerScore(ScoreHolder.forNameOnly(name), objective)
        }
    }

    private fun colorOf(team: String): ChatFormatting = when (team) {
        WallwarTeams.RED -> ChatFormatting.RED
        WallwarTeams.BLUE -> ChatFormatting.BLUE
        else -> ChatFormatting.WHITE
    }

    private data class Line(
        val name: String,
        val seconds: Int,
        val color: ChatFormatting,
        val team: String,
        val x: Int,
        val z: Int,
        val y: Int,
        val id: String,
    )
}
