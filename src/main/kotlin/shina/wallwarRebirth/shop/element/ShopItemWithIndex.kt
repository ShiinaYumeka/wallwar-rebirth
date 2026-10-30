package shina.wallwarRebirth.shop.element

import eu.pb4.sgui.api.ClickType
import shina.wallwarRebirth.shop.element.view.ShopItemViewModel
import shina.wallwarRebirth.shop.gui.ShopGui
import shina.wallwarRebirth.util.text.component
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.permissions.PermissionSet
import net.minecraft.world.item.ItemStack

interface ShopItemWithIndex {
    fun index(): Int

    /**
     * 获取默认图标（无状态，用于初始化）
     */
    fun getDefaultIcon(): ItemStack

    /**
     * 是否应该跳过添加（配置相关，在构建 GUI 时调用）
     */
    fun shouldSkipAdd(gui: ShopGui) = false

    /**
     * 获取显示图标
     */
    fun getIcon(gui: ShopGui, viewModel: ShopItemViewModel): ItemStack

    /**
     * 获取基础商品数据
     */
    fun getBaseItem(gui: ShopGui, clickType: ClickType, viewModel: ShopItemViewModel): BaseShopItem?

    /**
     * 更新商品状态
     */
    fun updateView(gui: ShopGui, viewModel: ShopItemViewModel) = Unit

    /**
     * 获取分支组ID（仅当 isBranchEntry() 返回 true 时有效）
     */
    fun getBranchGroupId(): Identifier? = null

    /**
     * 是否认为这个商品锁定
     */
    fun isLocked(gui: ShopGui, item: BaseShopItem?): Boolean = gui.getFurnaceLevel() < (item?.checkFurnaceLevel ?: 0)

    /**
     * 执行购买（不带材料后缀，向后兼容）
     */
    fun executeBuying(
        item: BaseShopItem,
        gui: ShopGui,
        clickType: ClickType,
        materialSuffix: Component? = null
    ) {
        val commandList = item.onBuy
        if (commandList.isEmpty()) return
        val player = gui.player

        val server = player.level().server
        val manager = server.commands
        commandList.forEach {
            val source = server.createCommandSourceStack()
                .withEntity(player)
                .withPosition(player.position())
                .withSource(player.commandSource())
                .withPermission(PermissionSet.ALL_PERMISSIONS)
                .withSuppressedOutput()
            manager.performPrefixedCommand(source, it)
        }
        if (!item.hideBuyText) {
            val msg = component {
                text("已购买 ") formatted ChatFormatting.YELLOW
                raw(Component.empty().withStyle(ChatFormatting.WHITE).append(item.icon.hoverName))
            }
            if (!materialSuffix?.string.isNullOrEmpty()) {
                msg.append("  ").append(materialSuffix)
            }
            player.sendSystemMessage(msg)
        }
    }
}
