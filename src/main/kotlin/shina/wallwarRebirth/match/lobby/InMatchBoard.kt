package shina.wallwarRebirth.match.lobby

import eu.pb4.sgui.api.elements.GuiElement
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.ChatFormatting
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.Items
import shina.wallwarRebirth.match.Match

class InMatchBoard(private val viewer: ServerPlayer) : SimpleGui(MenuType.GENERIC_9x3, viewer, false) {
    init {
        title = line("局内面板", ChatFormatting.YELLOW)
        fillGlass(this)
        setSlot(
            13,
            GuiElement(
                named(Items.BOOK, "局内面板", ChatFormatting.WHITE, listOf("内容与操作另行说明")),
            ) { _, _, _, _ -> },
        )
        val member = Match.member(viewer.uuid)
        if (viewer.isSpectator && (member == null || member.team.isEmpty())) {
            setSlot(
                15,
                GuiElement(
                    named(Items.OAK_DOOR, "返回大厅", ChatFormatting.GREEN, listOf("离开旁观，回到大厅")),
                ) { _, click, _, _ ->
                    if (!click.isLeft || click.shift) return@GuiElement
                    close()
                    Match.leaveSpectate(viewer)
                },
            )
        }
    }
}
