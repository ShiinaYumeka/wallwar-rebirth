package shina.wallwarRebirth.shop.element.type

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import eu.pb4.sgui.api.ClickType
import shina.wallwarRebirth.gui.ItemStackBuilder
import shina.wallwarRebirth.shop.branch.BranchGroupRegistry
import shina.wallwarRebirth.shop.element.BaseShopItem
import shina.wallwarRebirth.shop.element.view.ShopItemViewModel
import shina.wallwarRebirth.shop.gui.ShopGui
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack

class BranchEntryItem(
    index: Int,
    val branchGroup: Identifier,
    val displayItem: BaseShopItem
) : TypedShopItem(index) {
    private var animationTick: Int = 0

    companion object {
        val CODEC: MapCodec<BranchEntryItem> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                Codec.intRange(0, 81).fieldOf("index").forGetter(BranchEntryItem::index),
                Identifier.CODEC.fieldOf("branch_group").forGetter(BranchEntryItem::branchGroup),
                BaseShopItem.CODEC.fieldOf("display_item").forGetter(BranchEntryItem::displayItem)
            ).apply(instance, ::BranchEntryItem)
        }
    }

    override fun type(): Type = Type.BRANCH_ENTRY

    override fun getBranchGroupId(): Identifier = branchGroup

    override fun getDefaultIcon(): ItemStack = displayItem.icon.copy()

    override fun getIcon(gui: ShopGui, viewModel: ShopItemViewModel): ItemStack {
        val group = BranchGroupRegistry.get(branchGroup) ?: return displayItem.icon.copy()
        val branches = group.branches
        if (branches.isEmpty()) return displayItem.icon.copy()

        val lookup = gui.player.level().registryAccess()
        val detectedId = group.detect.detectCurrent(gui.player, lookup)

        val branchIcon = if (detectedId != null) {
            branches.firstOrNull { it.id == detectedId }?.item?.icon
        } else {
            val rotationIdx = (animationTick / 20) % branches.size
            branches.getOrNull(rotationIdx)?.item?.icon
        } ?: displayItem.icon

        val displayLore = displayItem.icon.get(DataComponents.LORE)?.lines()?.toList().orEmpty()

        return ItemStackBuilder.of(branchIcon)
            .setName(displayItem.icon.hoverName)
            .setLore(displayLore)
            .addCostInfo(gui.player, displayItem, locked = false, hasEnoughMaterialsOverride = group.hasPurchasableBranch(gui.player))
            .build()
    }

    override fun updateView(gui: ShopGui, viewModel: ShopItemViewModel) {
        animationTick++
    }

    override fun getBaseItem(gui: ShopGui, clickType: ClickType, viewModel: ShopItemViewModel): BaseShopItem? =
        if (clickType.isLeft) displayItem else null
}
