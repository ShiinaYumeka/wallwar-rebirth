package shina.wallwarRebirth.gui

import eu.pb4.sgui.virtual.inventory.VirtualScreenHandler
import eu.pb4.sgui.virtual.inventory.VirtualSlot
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.MenuType

class HotbarScreenHandler(type: MenuType<*>?, syncId: Int, gui: SimpleHotbarGui, player: Player) :
    VirtualScreenHandler(type, syncId, gui, player) {

    override fun setupSlots(player: Player) {
        for (n in 0..<this.gui.size) {
            val slot = this.gui.getSlotRedirect(n)
            if (slot != null) {
                this.addSlot(slot)
            } else {
                this.addSlot(VirtualSlot(this.gui, n, 0, 0))
            }
        }
    }
}
