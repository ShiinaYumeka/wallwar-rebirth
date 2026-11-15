package shina.wallwarRebirth.match

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import shina.wallwarRebirth.building.Buildings
import shina.wallwarRebirth.building.SiteCoreBlockEntity
import shina.wallwarRebirth.building.SiteCores
import shina.wallwarRebirth.item.PlunderBannerItem

object PlunderBannerInventory {
    fun tick(server: MinecraftServer) {
        for (player in server.playerList.players) sync(player)
    }

    private fun sync(player: ServerPlayer) {
        val member = Match.member(player.uuid)
        val towers = if (Match.isFight() && member != null && member.team.isNotEmpty()) {
            towers(member.team)
        } else {
            emptyList()
        }
        val wanted = towers.map { it.blockPos.asLong() }.toSet()
        stripOthers(player, wanted)
        if (player.isSpectator) return
        val lookup = player.registryAccess()
        for (site in towers) {
            BoundItems.offerToMain(player, PlunderBannerItem.create(site.blockPos, lookup))
        }
    }

    private fun towers(team: String): List<SiteCoreBlockEntity> =
        SiteCores.all().filter { site ->
            !site.isRemoved &&
                site.health > 0 &&
                site.buildingId == Buildings.PILLAGER.id &&
                site.team == team &&
                !site.diffuse.isEmpty
        }

    private fun stripOthers(player: Player, wanted: Set<Long>) {
        val inventory = player.inventory
        for (slot in 0 until inventory.containerSize) {
            val stack = inventory.getItem(slot)
            if (unwanted(stack, wanted)) inventory.setItem(slot, ItemStack.EMPTY)
        }
        if (unwanted(player.offhandItem, wanted)) {
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY)
        }
        if (unwanted(player.containerMenu.carried, wanted)) {
            player.containerMenu.carried = ItemStack.EMPTY
        }
    }

    private fun unwanted(stack: ItemStack, wanted: Set<Long>): Boolean {
        if (!PlunderBannerItem.isBanner(stack)) return false
        val tower = PlunderBannerItem.tower(stack) ?: return true
        return tower.asLong() !in wanted
    }
}
