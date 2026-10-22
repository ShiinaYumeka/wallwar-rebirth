package shina.wallwarRebirth.gui

import it.unimi.dsi.fastutil.objects.ReferenceSortedSets
import net.minecraft.ChatFormatting
import net.minecraft.ChatFormatting.*
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.item.component.TooltipDisplay

object GuiTheme {
    const val HEX_TEXT = 0xB3B6B7
    const val HEX_HL = 0xF6B894
    const val HEX_OK = 0x58D68D
    const val HEX_NO = 0xEC7063
    const val HEX_ACT = 0x5DADE2
    const val HEX_MUTED = 0x7F8C8D
    const val HEX_ALT = 0xC39BD3

    val GLASS_BLACK: ItemStack = blankPane(Items.BLACK_STAINED_GLASS_PANE)
    val GLASS_BLUE: ItemStack = blankPane(Items.BLUE_STAINED_GLASS_PANE)
    val GLASS_GREEN: ItemStack = blankPane(Items.GREEN_STAINED_GLASS_PANE)
    val GLASS_LIGHT_BLUE: ItemStack = blankPane(Items.LIGHT_BLUE_STAINED_GLASS_PANE)
    val GLASS_RED: ItemStack = blankPane(Items.RED_STAINED_GLASS_PANE)

    @JvmField
    val GLASS_PURPLE: ItemStack = blankPane(Items.PURPLE_STAINED_GLASS_PANE)
    val GLASS_CYAN: ItemStack = blankPane(Items.CYAN_STAINED_GLASS_PANE)
    val GLASS_ORANGE: ItemStack = blankPane(Items.ORANGE_STAINED_GLASS_PANE)
    val GLASS_GRAY: ItemStack = blankPane(Items.GRAY_STAINED_GLASS_PANE)
    val GLASS_WHITE: ItemStack = blankPane(Items.WHITE_STAINED_GLASS_PANE)
    val GLASS_LIME: ItemStack = blankPane(Items.LIME_STAINED_GLASS_PANE)
    val GLASS_MAGENTA: ItemStack = blankPane(Items.MAGENTA_STAINED_GLASS_PANE)
    val GLASS_YELLOW: ItemStack = blankPane(Items.YELLOW_STAINED_GLASS_PANE)
    val GLASS_LIGHT_GRAY: ItemStack = blankPane(Items.LIGHT_GRAY_STAINED_GLASS_PANE)
    val GLASS_BROWN: ItemStack = blankPane(Items.BROWN_STAINED_GLASS_PANE)

    val GLASS_ACTION: ItemStack = blankPane(Items.LIGHT_BLUE_STAINED_GLASS_PANE)

    private fun blankPane(item: Item): ItemStack = ItemStackBuilder.of(item)
        .set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay(true, ReferenceSortedSets.emptySet()))
        .build()

    @JvmStatic
    fun getDecoratedGlassPane(color: ChatFormatting?): ItemStack {
        if (color == null) return GLASS_GRAY.copy()
        return when (color) {
            BLACK -> GLASS_BLACK
            BLUE, DARK_BLUE -> GLASS_BLUE
            DARK_GREEN -> GLASS_GREEN
            DARK_AQUA -> GLASS_CYAN
            RED, DARK_RED -> GLASS_RED
            DARK_PURPLE -> GLASS_PURPLE
            GOLD -> GLASS_ORANGE
            GREEN -> GLASS_LIME
            AQUA -> GLASS_LIGHT_BLUE
            LIGHT_PURPLE -> GLASS_MAGENTA
            YELLOW -> GLASS_YELLOW
            WHITE -> GLASS_WHITE
            else -> GLASS_GRAY
        }.copy()
    }

    fun translatable(key: String, vararg args: Any, color: Int = HEX_TEXT): MutableComponent =
        Component.translatable(key, *args).withStyle { it.withColor(color).withItalic(false) }

    fun literal(text: String, color: Int = HEX_TEXT): MutableComponent =
        Component.literal(text).withStyle { it.withColor(color).withItalic(false) }

    internal fun unitalicizeLore(stack: ItemStack) {
        val currentLore = stack[DataComponents.LORE]?.lines ?: return
        val newLore = currentLore.map { it.copy().withStyle { s -> s.withItalic(false) } as Component }
        stack[DataComponents.LORE] = ItemLore(newLore)
    }
}

internal fun MutableComponent.hl() = this.withStyle { it.withColor(GuiTheme.HEX_HL) }
internal fun MutableComponent.no() = this.withStyle { it.withColor(GuiTheme.HEX_NO) }
