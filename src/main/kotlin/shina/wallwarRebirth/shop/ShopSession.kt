package shina.wallwarRebirth.shop

import net.minecraft.world.entity.Entity

/**
 * What a shop is attached to. Game code fills the subject, furnace level, and upgrade callback.
 * With the defaults, the shop only spends the player's own inventory.
 */
class ShopSession(
    var furnaceLevel: Int = 0,
    var subject: Entity? = null,
    var valid: Boolean = true,
    var supportsFurnaceUpgrade: Boolean = false,
    var snowCount: Int = 0,
    var canAcceptSnow: Boolean = false,
    private val onUpgrade: ((ShopSession) -> Boolean)? = null,
) {
    fun isValid(): Boolean = valid

    fun upgradeFurnace(): Boolean {
        val handler = onUpgrade
        if (handler != null) return handler(this)
        if (!supportsFurnaceUpgrade || furnaceLevel >= 2) return false
        furnaceLevel += 1
        return true
    }
}
