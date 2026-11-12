package shina.wallwarRebirth.shop.match

import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import shina.wallwarRebirth.building.Buildings
import shina.wallwarRebirth.building.SiteCoreBlockEntity
import shina.wallwarRebirth.building.SiteCores
import shina.wallwarRebirth.item.PortableTerminalItem

object PortableShop {
    fun open(player: ServerPlayer, stack: ItemStack? = null) {
        val team = player.team?.name ?: ""
        if (team.isEmpty()) {
            player.sendSystemMessage(Component.literal("该界面尚未开放"))
            return
        }
        val base = baseOf(team)
        if (base == null) {
            player.sendSystemMessage(Component.literal("基地不在"))
            return
        }
        BaseShop.open(player, base, BaseShop.Page.WAREHOUSE, 0, BaseShop.Access.TERMINAL)
    }

    fun strip(player: Player) {
        val inventory = player.inventory
        for (slot in 0 until inventory.containerSize) {
            if (PortableTerminalItem.isTerminal(inventory.getItem(slot))) inventory.setItem(slot, ItemStack.EMPTY)
        }
        if (PortableTerminalItem.isTerminal(player.containerMenu.carried)) {
            player.containerMenu.carried = ItemStack.EMPTY
        }
    }

    fun baseOf(team: String): SiteCoreBlockEntity? = SiteCores.all().firstOrNull { site ->
        !site.isRemoved &&
            site.health > 0 &&
            site.buildingId == Buildings.BASE.id &&
            site.team == team
    }
}
