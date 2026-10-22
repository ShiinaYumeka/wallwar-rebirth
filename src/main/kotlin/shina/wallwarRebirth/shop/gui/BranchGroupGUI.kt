package shina.wallwarRebirth.shop.gui

import eu.pb4.sgui.api.elements.GuiElementInterface
import eu.pb4.sgui.api.gui.SimpleGui
import shina.wallwarRebirth.gui.ItemStackBuilder
import shina.wallwarRebirth.shop.branch.BranchGroup
import shina.wallwarRebirth.shop.branch.BranchOption
import shina.wallwarRebirth.shop.element.BaseShopItem
import shina.wallwarRebirth.util.ContainerMenuUtil.getMenuType
import shina.wallwarRebirth.util.playNotifySound
import shina.wallwarRebirth.util.text.TextStyles
import shina.wallwarRebirth.util.text.component
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.permissions.PermissionSet
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.ItemStack

class BranchGroupGUI(
    player: ServerPlayer,
    private val branchGroup: BranchGroup,
    private val onClose: (() -> Unit)? = null
) : SimpleGui(getMenuType(branchGroup.rows), player, false), MaterialConsumeAccess {
    override fun getPlayer(): ServerPlayer = player

    init {
        title = branchGroup.title
        setupGui()
    }

    private fun setupGui() {
        val currentBranchId = branchGroup.detect.detectCurrent(player, player.level().registryAccess())
        branchGroup.branches.forEach { branch ->
            setSlot(branch.slotIndex, BranchButton(branch, currentBranchId))
        }
    }

    private inner class BranchButton(
        private val branch: BranchOption,
        private val currentBranchId: String?
    ) : GuiElementInterface {
        override fun getItemStack(): ItemStack {
            val isActive = currentBranchId == branch.id
            return if (isActive) {
                ItemStackBuilder.of(branch.item.icon)
                    .set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
                    .setLore(
                        component {
                            translatable("text.hgwar.branch_group.active") provide TextStyles.LIGHTER_GRAY_ITEM_LORE
                        }
                    )
                    .build()
            } else {
                ItemStackBuilder.of(branch.item.icon)
                    .addCostInfo(player, branch.item, locked = isLockedByFurnaceLevel(branch.item))
                    .build()
            }
        }

        override fun getGuiCallback() = GuiElementInterface.ClickCallback { _, clickType, _, _ ->
            if (clickType.isLeft && currentBranchId != branch.id) {
                handlePurchase(branch)
            } else if (clickType.isLeft) {
                player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.UI, 1.0f, 1.0f)
            }
        }
    }

    private fun isLockedByFurnaceLevel(item: BaseShopItem): Boolean {
        return item.checkFurnaceLevel > 0
    }

    private fun handlePurchase(branch: BranchOption) {
        val item = branch.item

        if (isLockedByFurnaceLevel(item)) {
            item.lockMessage?.let { player.sendSystemMessage(it) }
            playFailSound()
            return
        }

        val deductResult = tryDeduct(item.costs)
        if (deductResult !is MaterialConsumeAccess.DeductResult.Success) return

        executeOnBuyCommands(item, deductResult)
        if (branchGroup.closeOnBuy) {
            this.close()
            onClose?.invoke()
        } else {
            setupGui()
        }
    }

    private fun executeOnBuyCommands(
        item: BaseShopItem,
        deductResult: MaterialConsumeAccess.DeductResult.Success
    ) {
        val commands = item.onBuy
        val server = player.level().server
        val manager = server.commands
        commands.forEach { command ->
            val source = server.createCommandSourceStack()
                .withEntity(player)
                .withPosition(player.position())
                .withPermission(PermissionSet.ALL_PERMISSIONS)
                .withSuppressedOutput()
            manager.performPrefixedCommand(source, command)
        }

        val itemName = item.icon.hoverName.string
        val msg = component {
            translatable("text.hgwar.branch_group.selected", itemName) formatted ChatFormatting.GREEN
            if (deductResult.teamRepoSuffix != null) {
                text("  ")
                raw(deductResult.teamRepoSuffix)
            }
        }
        player.sendSystemMessage(msg)
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.UI, 1.0f, 1.0f)
    }
}
