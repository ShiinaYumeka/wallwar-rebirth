package shina.wallwarRebirth.match.lobby

import eu.pb4.sgui.api.elements.GuiElement
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.ChatFormatting
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.MenuType
import shina.wallwarRebirth.match.Match
import shina.wallwarRebirth.profession.Classes

class ProfessionBoard(private val viewer: ServerPlayer) : SimpleGui(MenuType.GENERIC_9x6, viewer, false) {
    init {
        title = line("大厅终端 · 职业", ChatFormatting.YELLOW)
        paint()
    }

    override fun onTick() {
        super.onTick()
        paint()
    }

    private fun paint() {
        fillGlass(this)
        val slots = listOf(20, 21, 23, 24)
        for ((slot, spec) in slots.zip(Classes.specs)) {
            val current = Match.member(viewer.uuid)?.classId == spec.id
            val stack = named(
                spec.icon,
                spec.displayName,
                ChatFormatting.AQUA,
                listOf(
                    if (current) "当前职业" else "点击选择",
                    "被动：${spec.passive}",
                    "主动：${spec.active}",
                ),
                glow = current,
            )
            setSlot(slot, GuiElement(stack) { _, click, _, _ ->
                if (!click.isLeft || click.shift) return@GuiElement
                Match.chooseClass(viewer, spec.id)
            })
        }
        placeBack(this, viewer)
    }
}
