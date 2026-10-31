package shina.wallwarRebirth.monster

import net.minecraft.ChatFormatting
import net.minecraft.server.MinecraftServer
import net.minecraft.world.scores.Scoreboard

object TestTeams {
    const val RED = "红队"
    const val BLUE = "蓝队"

    fun ensure(server: MinecraftServer) {
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
