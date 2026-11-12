package shina.wallwarRebirth.profession

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import shina.wallwarRebirth.item.ModItems
import shina.wallwarRebirth.item.SkillItem
import shina.wallwarRebirth.match.Match

object SkillInventory {
    fun tick(server: MinecraftServer) {
        if (!Match.isFight()) return
        for (player in server.playerList.players) offer(player)
    }

    fun offer(player: ServerPlayer) {
        if (!Match.isFight()) return
        if (player.isSpectator) return
        val member = Match.member(player.uuid) ?: return
        if (member.team.isEmpty()) return
        val item = itemOf(member.classId) ?: return
        if (holds(player, item)) return
        val stack = ItemStack(item)
        if (!player.inventory.add(stack)) player.drop(stack, false)
    }

    fun strip(player: Player) {
        val inventory = player.inventory
        for (slot in 0 until inventory.containerSize) {
            if (SkillItem.isSkill(inventory.getItem(slot))) inventory.setItem(slot, ItemStack.EMPTY)
        }
        if (SkillItem.isSkill(player.containerMenu.carried)) {
            player.containerMenu.carried = ItemStack.EMPTY
        }
    }

    fun itemOf(classId: String): Item? = when (Classes.normalize(classId)) {
        Classes.MINER -> ModItems.SKILL_MINER
        Classes.WRECKER -> ModItems.SKILL_WRECKER
        Classes.BASTION -> ModItems.SKILL_BASTION
        Classes.SWORDSMAN -> ModItems.SKILL_SWORDSMAN
        else -> null
    }

    private fun holds(player: ServerPlayer, item: Item): Boolean {
        val inventory = player.inventory
        for (slot in 0 until inventory.containerSize) {
            if (inventory.getItem(slot).`is`(item)) return true
        }
        return player.containerMenu.carried.`is`(item)
    }
}
