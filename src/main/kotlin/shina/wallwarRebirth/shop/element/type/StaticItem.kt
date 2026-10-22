package shina.wallwarRebirth.shop.element.type

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import eu.pb4.sgui.api.ClickType
import shina.wallwarRebirth.gui.ItemStackBuilder
import shina.wallwarRebirth.shop.element.BaseShopItem
import shina.wallwarRebirth.shop.element.view.ShopItemViewModel
import shina.wallwarRebirth.shop.gui.ShopGui
import net.minecraft.world.item.ItemStack

class StaticItem(index: Int, val item: BaseShopItem) : TypedShopItem(index) {
    companion object {
        val CODEC: MapCodec<StaticItem> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                Codec.intRange(0, 81).fieldOf("index").forGetter(StaticItem::index),
                BaseShopItem.CODEC.fieldOf("item").forGetter(StaticItem::item)
            ).apply(instance, ::StaticItem)
        }
    }

    override fun type(): Type = Type.STATIC

    override fun getDefaultIcon(): ItemStack = item.icon.copy()

    override fun getIcon(gui: ShopGui, viewModel: ShopItemViewModel): ItemStack =
        ItemStackBuilder.of(item.icon)
            .addCostInfo(gui.player, item, isLocked(gui, item))
            .build()

    override fun getBaseItem(gui: ShopGui, clickType: ClickType, viewModel: ShopItemViewModel): BaseShopItem? =
        if (clickType.isLeft) item else null
}
