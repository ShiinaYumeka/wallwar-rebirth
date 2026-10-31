package shina.wallwarRebirth.monster

import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object SummonedGear {
    fun applyStats(mob: LivingEntity, definition: MonsterDefinition, fill: Boolean) {
        mob.getAttribute(Attributes.MAX_HEALTH)?.baseValue = definition.health
        mob.getAttribute(Attributes.ARMOR)?.baseValue = definition.armor
        mob.getAttribute(Attributes.ATTACK_DAMAGE)?.baseValue = definition.playerAttack
        if (fill) mob.health = mob.maxHealth
    }

    fun apply(mob: Mob, unit: SummonedUnit) {
        val definition = Monsters.byId(unit.kindId) ?: return
        val server = (mob.level() as? ServerLevel)?.server
        val scoreTeam = if (unit.summonedTeam.isEmpty() || server == null) {
            null
        } else {
            server.scoreboard.getPlayerTeam(unit.summonedTeam)
        }
        if (server != null) {
            if (scoreTeam != null) {
                server.scoreboard.addPlayerToTeam(mob.scoreboardName, scoreTeam)
            } else {
                server.scoreboard.removePlayerFromTeam(mob.scoreboardName)
            }
        }
        val color = scoreTeam?.color ?: ChatFormatting.WHITE
        val shown = if (color == ChatFormatting.RESET) ChatFormatting.WHITE else color
        mob.customName = Component.literal(definition.displayName).withStyle { style ->
            style.withColor(shown).withItalic(false)
        }
        mob.isCustomNameVisible = true
        if (definition.undead) {
            val helmet = ItemStack(Items.LEATHER_HELMET)
            helmet.set(DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE)
            mob.setItemSlot(EquipmentSlot.HEAD, helmet)
            mob.setDropChance(EquipmentSlot.HEAD, 0f)
        }
        if (definition.bowRange != null) {
            val bow = ItemStack(Items.BOW)
            bow.set(DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE)
            mob.setItemSlot(EquipmentSlot.MAINHAND, bow)
            mob.setDropChance(EquipmentSlot.MAINHAND, 0f)
        }
    }
}
