package shina.wallwarRebirth.shop.element

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.resources.Identifier
import net.minecraft.util.ExtraCodecs
import net.minecraft.world.item.ItemStack
import java.util.*

@JvmRecord
data class BaseShopItem(
    val icon: ItemStack,
    val costs: Map<Identifier, Int>,
    val onBuy: List<String>,
    val checkEmptySlots: Boolean,
    val checkFurnaceLevel: Int,
    val hideBuyText: Boolean,
    val lockMessage: Component?,
) {
    companion object {
        @JvmField
        val CODEC: Codec<BaseShopItem> = RecordCodecBuilder.create { instance ->
            instance.group(
                ItemStack.STRICT_CODEC
                    .fieldOf("icon")
                    .forGetter(BaseShopItem::icon),
                Codec.unboundedMap(Identifier.CODEC, ExtraCodecs.POSITIVE_INT)
                    .optionalFieldOf("costs", emptyMap())
                    .forGetter(BaseShopItem::costs),
                Codec.STRING.listOf()
                    .optionalFieldOf("on_buy", emptyList())
                    .forGetter(BaseShopItem::onBuy),
                Codec.BOOL
                    .optionalFieldOf("check_empty_slot", false)
                    .forGetter(BaseShopItem::checkEmptySlots),
                ExtraCodecs.NON_NEGATIVE_INT
                    .optionalFieldOf("check_furnace_level", 0)
                    .forGetter(BaseShopItem::checkFurnaceLevel),
                Codec.BOOL
                    .optionalFieldOf("hide_buy_text", false)
                    .forGetter(BaseShopItem::hideBuyText),
                ComponentSerialization.CODEC
                    .optionalFieldOf("lock_message")
                    .forGetter { Optional.ofNullable(it.lockMessage) },
            ).apply(instance) { icon, costs, onBuy, checkEmpty, checkLevel, hideBuyText, failMessageOpt ->
                BaseShopItem(icon, costs, onBuy, checkEmpty, checkLevel, hideBuyText, failMessageOpt.orElse(null))
            }
        }
    }
}
