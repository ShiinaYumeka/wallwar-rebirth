package shina.wallwarRebirth.shop

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.scores.PlayerTeam
import shina.wallwarRebirth.shop.element.Material

interface TeamMaterialStore {
    fun countMaterialAmount(material: Material): Int

    fun deductMaterial(material: Material, amount: Int): Boolean

    fun quickPut(stack: ItemStack): ItemStack

    fun open(player: ServerPlayer) = Unit
}

fun interface TeamStoreLookup {
    fun find(player: ServerPlayer, team: PlayerTeam): TeamMaterialStore?
}

object TeamStores {
    var lookup: TeamStoreLookup = TeamStoreLookup { _, _ -> null }

    fun find(player: ServerPlayer, team: PlayerTeam): TeamMaterialStore? = lookup.find(player, team)
}
