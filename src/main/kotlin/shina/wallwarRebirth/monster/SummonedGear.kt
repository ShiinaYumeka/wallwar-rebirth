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
import net.minecraft.world.item.component.ItemAttributeModifiers

object SummonedGear {
    fun applyStats(mob: LivingEntity, definition: MonsterDefinition, fill: Boolean) {
        mob.getAttribute(Attributes.MAX_HEALTH)?.baseValue = definition.health
        val worn = if (definition.wornArmor || definition.copperGear) 0.0 else definition.armor
        mob.getAttribute(Attributes.ARMOR)?.baseValue = worn
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
        if (definition.copperGear) {
            equip(mob, EquipmentSlot.HEAD, ItemStack(Items.COPPER_HELMET))
            equip(mob, EquipmentSlot.CHEST, ItemStack(Items.COPPER_CHESTPLATE))
            equip(mob, EquipmentSlot.LEGS, ItemStack(Items.COPPER_LEGGINGS))
            equip(mob, EquipmentSlot.FEET, ItemStack(Items.COPPER_BOOTS))
            val sword = ItemStack(Items.COPPER_SWORD)
            sword.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
            equip(mob, EquipmentSlot.MAINHAND, sword)
        } else if (definition.wornArmor) {
            equip(mob, EquipmentSlot.HEAD, ItemStack(Items.IRON_HELMET))
            equip(mob, EquipmentSlot.CHEST, ItemStack(Items.IRON_CHESTPLATE))
            equip(mob, EquipmentSlot.LEGS, ItemStack(Items.IRON_LEGGINGS))
            equip(mob, EquipmentSlot.FEET, ItemStack(Items.IRON_BOOTS))
            equip(mob, EquipmentSlot.MAINHAND, ItemStack(Items.IRON_PICKAXE))
        } else if (definition.undead) {
            val helmet = ItemStack(Items.LEATHER_HELMET)
            helmet.set(DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE)
            mob.setItemSlot(EquipmentSlot.HEAD, helmet)
            mob.setDropChance(EquipmentSlot.HEAD, 0f)
        }
        if (definition.sword) {
            val sword = ItemStack(Items.IRON_SWORD)
            sword.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
            equip(mob, EquipmentSlot.MAINHAND, sword)
        } else if (definition.bowRange != null && !definition.wornArmor) {
            val bow = ItemStack(Items.BOW)
            bow.set(DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE)
            mob.setItemSlot(EquipmentSlot.MAINHAND, bow)
            mob.setDropChance(EquipmentSlot.MAINHAND, 0f)
        }
    }

    private fun equip(mob: Mob, slot: EquipmentSlot, stack: ItemStack) {
        stack.set(DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE)
        mob.setItemSlot(slot, stack)
        mob.setDropChance(slot, 0f)
    }
}
