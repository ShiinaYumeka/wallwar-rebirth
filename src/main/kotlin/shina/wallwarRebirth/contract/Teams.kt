package shina.wallwarRebirth.contract

import net.minecraft.ChatFormatting
import net.minecraft.server.MinecraftServer
import net.minecraft.world.scores.Scoreboard

/**
 * Hostile when the team strings differ. Two empty strings are the same side.
 * Spectators and creative players are not removed here.
 */
interface Teams {
    val red: String
    val blue: String
    fun hostile(left: String, right: String): Boolean
    fun ensure(server: MinecraftServer)
}

object WallwarTeams : Teams {
    override val red: String = "红队"
    override val blue: String = "蓝队"
    const val RED: String = "红队"
    const val BLUE: String = "蓝队"

    override fun hostile(left: String, right: String): Boolean = left != right

    override fun ensure(server: MinecraftServer) {
        val board = server.scoreboard
        ensure(board, RED, ChatFormatting.RED)
        ensure(board, BLUE, ChatFormatting.BLUE)
    }

    private fun ensure(board: Scoreboard, name: String, color: ChatFormatting) {
        val existing = board.getPlayerTeam(name)
        val team = existing ?: board.addPlayerTeam(name)
        if (existing == null) team.color = color
        team.setAllowFriendlyFire(false)
    }
}
