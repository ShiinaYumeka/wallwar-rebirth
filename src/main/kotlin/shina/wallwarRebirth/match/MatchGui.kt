package shina.wallwarRebirth.match

import net.minecraft.server.level.ServerPlayer
import shina.wallwarRebirth.match.lobby.LobbyTab
import shina.wallwarRebirth.match.lobby.LobbyTerminal

object MatchGui {
    fun open(player: ServerPlayer) {
        LobbyTerminal.open(player, LobbyTab.JOIN)
    }

    fun openEntry(player: ServerPlayer) {
        LobbyTerminal.open(player)
    }
}
