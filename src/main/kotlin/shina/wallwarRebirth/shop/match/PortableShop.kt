package shina.wallwarRebirth.shop.match
import shina.wallwarRebirth.match.Match

import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import shina.wallwarRebirth.building.Buildings
import shina.wallwarRebirth.building.SiteCores
import shina.wallwarRebirth.item.ModComponents
import shina.wallwarRebirth.item.ModItems
import shina.wallwarRebirth.item.PortableTerminalItem

object PortableShop {
    fun tick(server: MinecraftServer) {
        if (!Match.isFight()) return
        for (player in server.playerList.players) offer(player)
    }

    fun offer(player: ServerPlayer) {
        if (!Match.isFight()) return
        if (player.isSpectator) return
        val team = player.team?.name ?: return
        if (team.isEmpty() || baseOf(team) == null) return
        if (holds(player, team)) return
        val stack = ItemStack(ModItems.TERMINAL)
        stack.set(ModComponents.TERMINAL_TEAM, team)
        player.inventory.add(stack)
    }

    fun open(player: ServerPlayer, stack: ItemStack) {
        val bound = stack.get(ModComponents.TERMINAL_TEAM) ?: ""
        val own = player.team?.name ?: ""
        if (bound.isEmpty() || own != bound) {
            player.sendSystemMessage(Component.literal("敌方打不开"))
            return
        }
        val base = baseOf(bound)
        if (base == null) {
            player.sendSystemMessage(Component.literal("基地不在"))
            return
        }
        BaseShop.open(player, base)
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

    private fun holds(player: ServerPlayer, team: String): Boolean {
        val inventory = player.inventory
        for (slot in 0 until inventory.containerSize) {
            if (matches(inventory.getItem(slot), team)) return true
        }
        return matches(player.containerMenu.carried, team)
    }

    private fun matches(stack: ItemStack, team: String): Boolean =
        PortableTerminalItem.isTerminal(stack) && stack.get(ModComponents.TERMINAL_TEAM) == team

    private fun baseOf(team: String) = SiteCores.all().firstOrNull { site ->
        !site.isRemoved &&
            site.health > 0 &&
            site.buildingId == Buildings.BASE.id &&
            site.team == team &&
            !site.moduleStack(shina.wallwarRebirth.building.ModuleSlot.DIFFUSION).isEmpty
    }
}
