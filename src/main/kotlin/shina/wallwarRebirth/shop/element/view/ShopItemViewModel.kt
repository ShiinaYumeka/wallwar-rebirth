package shina.wallwarRebirth.shop.element.view

import eu.pb4.sgui.api.ClickType
import shina.wallwarRebirth.shop.ShopHooks
import shina.wallwarRebirth.shop.element.BaseShopItem
import shina.wallwarRebirth.shop.element.ShopItemWithIndex
import shina.wallwarRebirth.shop.element.type.TypedShopItem
import shina.wallwarRebirth.shop.gui.MaterialConsumeAccess.DeductBreakdown
import shina.wallwarRebirth.shop.gui.ShopGui
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack

class ShopItemViewModel(private val shopItem: ShopItemWithIndex, private val gui: ShopGui) {
    // 用于动态物品
    var cachedDynamicIndex: Int? = null
        internal set

    fun getDefaultIcon(): ItemStack = shopItem.getDefaultIcon()

    fun getIcon(): ItemStack = shopItem.getIcon(gui, this)

    fun getBaseItem(clickType: ClickType): BaseShopItem? = shopItem.getBaseItem(gui, clickType, this)

    fun update() = shopItem.updateView(gui, this)

    fun getBranchGroupId(): Identifier? = shopItem.getBranchGroupId()

    fun isLocked(clickType: ClickType): Boolean = shopItem.isLocked(gui, getBaseItem(clickType))

    fun executeBuying(
        item: BaseShopItem,
        clickType: ClickType,
        materialSuffix: Component? = null,
        deductBreakdown: DeductBreakdown = DeductBreakdown(emptyMap(), emptyMap()),
    ) {
        shopItem.executeBuying(item, gui, clickType, materialSuffix)
        if (shopItem is TypedShopItem) {
            ShopHooks.onBought(
                gui.session,
                gui.player,
                shopItem,
                item,
                deductBreakdown.fromPlayerInventory,
                deductBreakdown.fromTeamRepo,
                deductBreakdown.fromTeamRepoInvalid,
            )
        }
    }
}
