package shina.wallwarRebirth.shop.element

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import shina.wallwarRebirth.shop.element.type.TypedShopItem
import net.minecraft.util.ExtraCodecs

data class ShopLayer(
    val rows: Int,
    val items: List<ShopItemWithIndex>
) {
    companion object {
        @JvmField
        val CODEC: Codec<ShopLayer> = RecordCodecBuilder.create { instance ->
            instance.group(
                ExtraCodecs.intRange(1, 6).fieldOf("rows")
                    .forGetter(ShopLayer::rows),
                Codec.list(TypedShopItem.CODEC).fieldOf("items")
                    .forGetter { shopType -> shopType.items.map { it as TypedShopItem } }
            ).apply(instance, ::ShopLayer)
        }
    }
}
