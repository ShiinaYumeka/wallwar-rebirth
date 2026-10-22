package shina.wallwarRebirth.shop

import net.minecraft.server.level.ServerPlayer

fun interface UpgradeForkQuery {
    fun currentFork(player: ServerPlayer, upgradeType: String, expectedLevel: Int): String?
}

object UpgradeForks {
    var query: UpgradeForkQuery = UpgradeForkQuery { _, _, _ -> null }
}
