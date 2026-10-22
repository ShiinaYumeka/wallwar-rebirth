package shina.wallwarRebirth.util

import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.MenuType

object ContainerMenuUtil {
    fun getMenuType(rows: Int): MenuType<ChestMenu> = when (rows) {
        1 -> MenuType.GENERIC_9x1
        2 -> MenuType.GENERIC_9x2
        3 -> MenuType.GENERIC_9x3
        4 -> MenuType.GENERIC_9x4
        5 -> MenuType.GENERIC_9x5
        else -> MenuType.GENERIC_9x6
    }
}
