package shina.wallwarRebirth.shop.element.type

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import eu.pb4.sgui.api.ClickType
import shina.wallwarRebirth.gui.ItemStackBuilder
import shina.wallwarRebirth.shop.element.BaseShopItem
import shina.wallwarRebirth.shop.element.view.ShopItemViewModel
import shina.wallwarRebirth.shop.gui.ShopGui
import shina.wallwarRebirth.util.text.component
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.resources.Identifier
import net.minecraft.util.ExtraCodecs
import net.minecraft.world.item.ItemStack

/**
 * 用于野生熔炉方块的高炉升级
 */
class SpecialBlockBlastUpgradeItem(
    idx: Int,
    val iconAt0: ItemStack,
    val iconAt1: ItemStack,
    val iconAt2: ItemStack,
    val costsAt0: Map<Identifier, Int>,
    val costsAt1: Map<Identifier, Int>,
    val alreadyBoughtMessage: Component
) : TypedShopItem(idx) {

    // 预构建的 BaseShopItem 实例，避免每次 getIcon/getBaseItem 时重复创建
    private val baseItemAt0 = BaseShopItem(iconAt0, costsAt0, emptyList(), false, 0, false, alreadyBoughtMessage)
    private val baseItemAt1 = BaseShopItem(iconAt1, costsAt1, emptyList(), false, 1, false, alreadyBoughtMessage)
    private val baseItemAt2 = BaseShopItem(iconAt2, emptyMap(), emptyList(), false, 999, false, alreadyBoughtMessage)

    companion object {
        val CODEC: MapCodec<SpecialBlockBlastUpgradeItem> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                Codec.intRange(0, 81)
                    .fieldOf("index")
                    .forGetter(SpecialBlockBlastUpgradeItem::index),
                ItemStack.STRICT_CODEC
                    .fieldOf("icon_at_0")
                    .forGetter(SpecialBlockBlastUpgradeItem::iconAt0),
                ItemStack.STRICT_CODEC
                    .fieldOf("icon_at_1")
                    .forGetter(SpecialBlockBlastUpgradeItem::iconAt1),
                ItemStack.STRICT_CODEC
                    .fieldOf("icon_at_2")
                    .forGetter(SpecialBlockBlastUpgradeItem::iconAt2),
                Codec.unboundedMap(Identifier.CODEC, ExtraCodecs.POSITIVE_INT)
                    .optionalFieldOf("costs_at_0", emptyMap())
                    .forGetter(SpecialBlockBlastUpgradeItem::costsAt0),
                Codec.unboundedMap(Identifier.CODEC, ExtraCodecs.POSITIVE_INT)
                    .optionalFieldOf("costs_at_1", emptyMap())
                    .forGetter(SpecialBlockBlastUpgradeItem::costsAt1),
                ComponentSerialization.CODEC
                    .fieldOf("already_bought_message")
                    .forGetter(SpecialBlockBlastUpgradeItem::alreadyBoughtMessage),
            ).apply(instance, ::SpecialBlockBlastUpgradeItem)
        }
    }

    override fun type() = Type.SPECIAL_BLOCK_BLAST_UPGRADING

    override fun shouldSkipAdd(gui: ShopGui): Boolean = !gui.session.supportsFurnaceUpgrade

    override fun getDefaultIcon(): ItemStack = iconAt1

    override fun getIcon(gui: ShopGui, viewModel: ShopItemViewModel): ItemStack {
        val byBaseItem = getBaseItem(gui, ClickType.MOUSE_LEFT, viewModel)
        return ItemStackBuilder.of(byBaseItem.icon)
            .addCostInfo(gui.player, byBaseItem, isLocked(gui, byBaseItem))
            .build()
    }

    override fun getBaseItem(gui: ShopGui, clickType: ClickType, viewModel: ShopItemViewModel): BaseShopItem =
        when (gui.session.furnaceLevel) {
            0 -> baseItemAt0
            1 -> baseItemAt1
            else -> baseItemAt2
        }

    override fun executeBuying(item: BaseShopItem, gui: ShopGui, clickType: ClickType, materialSuffix: Component?) {
        gui.session.upgradeFurnace()
        if (!item.hideBuyText) {
            val msg = component {
                empty()
                text("已购买 ") formatted ChatFormatting.YELLOW
                raw(item.icon.hoverName)
            }
            if (!materialSuffix?.string.isNullOrEmpty()) msg.append("  ").append(materialSuffix)
            gui.player.sendSystemMessage(msg)
        }
    }
}
