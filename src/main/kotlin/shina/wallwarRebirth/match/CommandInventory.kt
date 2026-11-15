package shina.wallwarRebirth.match

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import shina.wallwarRebirth.building.Buildings
import shina.wallwarRebirth.building.SiteCores
import shina.wallwarRebirth.item.CommandItem

object CommandInventory {
    fun tick(server: MinecraftServer) {
        for (player in server.playerList.players) offer(player)
    }

    fun offer(player: ServerPlayer) {
        val member = Match.member(player.uuid)
        val want = Match.isFight() && member != null && member.team.isNotEmpty() && barracksReady(member.team)
        if (!want) {
            if (BoundItems.holds(player) { CommandItem.isCommand(it) }) strip(player)
            return
        }
        if (BoundItems.holds(player) { CommandItem.isCommand(it) }) {
            BoundItems.recoverMisplaced(player)
            return
        }
        if (player.isSpectator) return
        BoundItems.offerToMain(player, CommandItem.create())
    }

    private fun barracksReady(team: String): Boolean =
        SiteCores.all().any { site ->
            !site.isRemoved &&
                site.health > 0 &&
                site.buildingId == Buildings.BARRACKS.id &&
                site.team == team &&
                !site.diffuse.isEmpty
        }

    fun strip(player: Player) {
        val inventory = player.inventory
        for (slot in 0 until inventory.containerSize) {
            if (CommandItem.isCommand(inventory.getItem(slot))) inventory.setItem(slot, ItemStack.EMPTY)
        }
        if (CommandItem.isCommand(player.offhandItem)) {
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY)
        }
        if (CommandItem.isCommand(player.containerMenu.carried)) {
            player.containerMenu.carried = ItemStack.EMPTY
        }
    }
}
