package shina.wallwarRebirth.shop.gui

import eu.pb4.sgui.api.ClickType
import eu.pb4.sgui.api.elements.GuiElementInterface
import eu.pb4.sgui.api.gui.GuiInterface
import shina.wallwarRebirth.shop.branch.BranchGroupRegistry
import shina.wallwarRebirth.shop.element.ShopItemWithIndex
import shina.wallwarRebirth.shop.element.view.ShopItemViewModel
import shina.wallwarRebirth.util.playNotifySound
import shina.wallwarRebirth.util.text.component
import net.minecraft.ChatFormatting
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack

class ShopGuiEntry(shopItem: ShopItemWithIndex, private val gui: ShopGui) : GuiElementInterface, MaterialConsumeAccess {
    private val viewModel = ShopItemViewModel(shopItem, gui)

    override fun getItemStack(): ItemStack = viewModel.getDefaultIcon()

    override fun getItemStackForDisplay(gui: GuiInterface): ItemStack = viewModel.getIcon()

    override fun getGuiCallback() = GuiElementInterface.ClickCallback { _, clickType, _, _ ->
        if (clickType.isLeft && !clickType.shift && !clickType.isDragging) {
            handlePurchase(clickType)
        }
    }

    fun tick() {
        viewModel.update()
    }

    override fun getPlayer(): ServerPlayer = gui.player

    private fun handleBranchEntryClick() {
        val branchGroupId = viewModel.getBranchGroupId() ?: return
        val branchGroup = BranchGroupRegistry.get(branchGroupId) ?: return
        val player = getPlayer()
        gui.close()
        player.level().server.execute {
            BranchGroupGUI(player, branchGroup, onClose = { gui.open() }).open()
        }
    }

    @Suppress("ReturnCount")
    private fun handlePurchase(clickType: ClickType) {
        viewModel.update()

        if (viewModel.getBranchGroupId() != null) {
            val item = viewModel.getBaseItem(clickType)
            if (item != null && viewModel.isLocked(clickType)) {
                val message = item.lockMessage
                if (message != null) getPlayer().sendSystemMessage(message)
                playFailSound()
                return
            }
            handleBranchEntryClick()
            return
        }

        val player = getPlayer()
        val item = viewModel.getBaseItem(clickType) ?: return

        if (viewModel.isLocked(clickType)) {
            val message = item.lockMessage
            if (message != null) {
                player.sendSystemMessage(message)
            }
            playFailSound()
            return
        }

        if (item.checkEmptySlots && player.inventory.freeSlot == Inventory.NOT_FOUND_INDEX) {
            player.sendSystemMessage(
                component {
                    translatable("text.hgwar.gui.item_upgrade.inventory_full") formatted ChatFormatting.RED
                }
            )
            playFailSound()
            return
        }

        val deductResult = tryDeduct(item.costs)
        if (deductResult !is MaterialConsumeAccess.DeductResult.Success) return

        try {
            val breakdown = deductResult.breakdown
                ?: MaterialConsumeAccess.DeductBreakdown(emptyMap(), emptyMap())
            viewModel.executeBuying(item, clickType, deductResult.teamRepoSuffix, breakdown)
            player.playNotifySound(
                SoundEvents.NOTE_BLOCK_PLING.value(),
                SoundSource.PLAYERS,
                0.7f,
                1.6f
            )
        } catch (_: Exception) {
            player.sendSystemMessage(
                component {
                    translatable("text.hgwar.gui.item_upgrade.buy_error") formatted ChatFormatting.RED
                }
            )
            playFailSound()
            return
        }
    }
}
