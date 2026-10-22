package shina.wallwarRebirth.util

import it.unimi.dsi.fastutil.objects.ReferenceSortedSets
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.TooltipDisplay

fun ItemStack.hideTooltip(): ItemStack = apply {
    this[DataComponents.TOOLTIP_DISPLAY] = TooltipDisplay(true, ReferenceSortedSets.emptySet())
}

fun ChatFormatting.toColoredGlassPaneItem(): Item = when (this) {
    ChatFormatting.BLACK -> Items.BLACK_STAINED_GLASS_PANE
    ChatFormatting.DARK_BLUE -> Items.BLUE_STAINED_GLASS_PANE
    ChatFormatting.DARK_GREEN -> Items.GREEN_STAINED_GLASS_PANE
    ChatFormatting.DARK_AQUA -> Items.CYAN_STAINED_GLASS_PANE
    ChatFormatting.DARK_RED -> Items.BROWN_STAINED_GLASS_PANE
    ChatFormatting.DARK_PURPLE -> Items.PURPLE_STAINED_GLASS_PANE
    ChatFormatting.GOLD -> Items.ORANGE_STAINED_GLASS_PANE
    ChatFormatting.GRAY -> Items.LIGHT_GRAY_STAINED_GLASS_PANE
    ChatFormatting.DARK_GRAY -> Items.GRAY_STAINED_GLASS_PANE
    ChatFormatting.BLUE -> Items.LIGHT_BLUE_STAINED_GLASS_PANE
    ChatFormatting.GREEN -> Items.LIME_STAINED_GLASS_PANE
    ChatFormatting.AQUA -> Items.CYAN_STAINED_GLASS_PANE
    ChatFormatting.RED -> Items.RED_STAINED_GLASS_PANE
    ChatFormatting.LIGHT_PURPLE -> Items.MAGENTA_STAINED_GLASS_PANE
    ChatFormatting.YELLOW -> Items.YELLOW_STAINED_GLASS_PANE
    ChatFormatting.WHITE -> Items.WHITE_STAINED_GLASS_PANE
    else -> Items.WHITE_STAINED_GLASS_PANE
}
