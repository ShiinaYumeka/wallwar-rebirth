package shina.wallwarRebirth.util.text

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Style

object TextStyles {
    const val LIGHTER_GRAY_COLOR = 0xDDDDDD

    val LIGHTER_GRAY_ITEM_LORE: Style = style {
        color(LIGHTER_GRAY_COLOR)
        italic(false)
    }

    val GRAY_ITEM_LORE: Style = style {
        color(ChatFormatting.GRAY.color)
        italic(false)
    }
}
