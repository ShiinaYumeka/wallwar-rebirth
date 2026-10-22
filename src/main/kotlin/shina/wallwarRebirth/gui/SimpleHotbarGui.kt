package shina.wallwarRebirth.gui

import eu.pb4.sgui.api.elements.GuiElementInterface
import eu.pb4.sgui.api.gui.BaseSlotGui
import eu.pb4.sgui.mixin.ServerPlayerEntityAccessor
import eu.pb4.sgui.virtual.inventory.VirtualSlot
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.inventory.Slot

open class SimpleHotbarGui(player: ServerPlayer) : BaseSlotGui(player, SIZE) {
    companion object {
        const val SIZE = 46
    }

    var selectedSlot = 0
        set(value) {
            field = Mth.clamp(value, 0, 8)
            if (this.isOpen) {
                player.connection.send(ClientboundSetHeldSlotPacket(field))
            }
        }
    protected var hasRedirects = false
    private val screenHandler: HotbarScreenHandler by lazy { HotbarScreenHandler(null, 0, this, player) }
    private var clicksPerTick = 0

    override fun setSlot(index: Int, element: GuiElementInterface?) {
        super.setSlot(index, element)
        if (this.isOpen && this.autoUpdate) {
            screenHandler.setSlot(index, VirtualSlot(this, index, 0, 0))
        }
    }

    override fun setSlotRedirect(index: Int, slot: Slot) {
        super.setSlotRedirect(index, slot)
        if (this.isOpen && this.autoUpdate) {
            screenHandler.setSlot(index, slot)
        }
        hasRedirects = true
    }

    override fun clearSlot(index: Int) {
        super.clearSlot(index)
        if (this.isOpen && this.autoUpdate) {
            screenHandler.setSlot(index, VirtualSlot(this, index, 0, 0))
        }
    }

    override fun open(): Boolean = if (!player.hasDisconnected() && !isOpen) {
        beforeOpen()
        openScreenHandler()
        afterOpen()
        true
    } else {
        false
    }

    private fun openScreenHandler() {
        onOpen()
        if (player.containerMenu != player.inventoryMenu && player.containerMenu != screenHandler) {
            player.closeContainer()
        }

        val handler = screenHandler
        player.containerMenu = handler

        (player as ServerPlayerEntityAccessor).callInitMenu(handler)
        player.connection.send(ClientboundSetHeldSlotPacket(selectedSlot))
    }

    override fun onTick() {
        clicksPerTick = 0
        super.onTick()
    }

    override fun close(screenHandlerIsClosed: Boolean) {
        if ((isOpen || screenHandlerIsClosed) && !reOpen) {
            if (!screenHandlerIsClosed && player.containerMenu == screenHandler) {
                player.closeContainer()
                player.connection.send(ClientboundSetHeldSlotPacket(player.inventory.selectedSlot))
            }
            player.containerMenu.sendAllDataToRemote()
            onClose()
        } else {
            reOpen = false
        }
    }

    override fun isIncludingPlayer(): Boolean = true
    override fun getVirtualSize(): Int = SIZE
    override fun isRedirectingSlots(): Boolean = hasRedirects
    override fun getSyncId(): Int = 0
    override fun getHeight(): Int = 4
    override fun getWidth(): Int = 9
    override fun getType(): MenuType<*>? = null
    override fun getLockPlayerInventory(): Boolean = true
    override fun setLockPlayerInventory(value: Boolean) = Unit
    override fun getTitle(): Component? = null
    override fun setTitle(title: Component) = Unit
}
