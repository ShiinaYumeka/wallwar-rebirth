package shina.wallwarRebirth.shop

import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class TeamRepoNotifyMode {
    NONE,
    WITHDRAW,
    ;

    fun receivesWithdraw(): Boolean = this == WITHDRAW
}

data class ShopPreference(
    var shopTeamRepoEnabled: Boolean = false,
    var teamRepoNotifyMode: TeamRepoNotifyMode = TeamRepoNotifyMode.WITHDRAW,
)

object PlayerShopSettings {
    private val preferences = ConcurrentHashMap<UUID, ShopPreference>()

    @JvmStatic
    fun of(player: ServerPlayer): ShopPreference =
        preferences.computeIfAbsent(player.uuid) { ShopPreference() }
}
