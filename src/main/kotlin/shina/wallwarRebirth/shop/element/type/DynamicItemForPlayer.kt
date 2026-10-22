package shina.wallwarRebirth.shop.element.type

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import shina.wallwarRebirth.gui.ItemStackBuilder
import shina.wallwarRebirth.shop.ShopHooks
import shina.wallwarRebirth.shop.element.BaseShopItem
import shina.wallwarRebirth.shop.element.view.ShopItemViewModel
import shina.wallwarRebirth.shop.gui.ShopGui
import shina.wallwarRebirth.util.text.component
import net.minecraft.ChatFormatting
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.permissions.PermissionSet
import net.minecraft.world.item.ItemStack
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.jvm.optionals.getOrNull

class DynamicItemForPlayer(
    index: Int,
    valueCommand: String,
    entries: List<DynamicItemEntry>,
    val upgradeType: String?,
    fallback: BaseShopItem?
) : DynamicItem(index, valueCommand, entries, fallback) {

    companion object {
        private val logger = LoggerFactory.getLogger("hgwar/DynamicItemForPlayer")

        val CODEC: MapCodec<DynamicItemForPlayer> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                Codec.intRange(0, 81).fieldOf("index")
                    .forGetter(DynamicItemForPlayer::index),
                Codec.STRING.fieldOf("value_command")
                    .forGetter(DynamicItemForPlayer::valueCommand),
                ENTRY_CODEC.listOf().optionalFieldOf("entries", emptyList())
                    .forGetter(DynamicItemForPlayer::entries),
                Codec.STRING.optionalFieldOf("upgrade_type")
                    .forGetter { Optional.ofNullable(it.upgradeType) },
                BaseShopItem.CODEC.optionalFieldOf("fallback")
                    .forGetter { Optional.ofNullable(it.fallback) }
            ).apply(instance) { index, valueCommand, entries, upgradeType, fallback ->
                DynamicItemForPlayer(
                    index,
                    if (valueCommand.startsWith('/')) valueCommand.substring(1) else valueCommand,
                    entries,
                    upgradeType.orElse(null),
                    fallback.getOrNull()
                )
            }
        }
    }

    override fun type(): Type = Type.DYNAMIC_PLAYER

    fun getIcon(gui: ShopGui, dynamicIndex: Int?): ItemStack {
        val matched = resolveCurrentEntry(gui, dynamicIndex) ?: return ItemStack.EMPTY
        return ItemStackBuilder.of(matched.icon)
            .addCostInfo(gui.player, matched, isLocked(gui, matched))
            .build()
    }

    fun getValueFromCommand(gui: ShopGui): Int {
        var result: Int? = null
        runValueCommand(gui.player) { result = it }
        return result ?: -1
    }

    override fun resolveCurrentEntry(gui: ShopGui, dynamicIndex: Int?): BaseShopItem? {
        val matched = (dynamicIndex?.let { entriesByValue[it] }?.item) ?: fallback
        return ShopHooks.modifyDynamicForPlayer(gui.session, gui.player, this, matched)
    }

    override fun executeValueCommand(gui: ShopGui, viewModel: ShopItemViewModel) {
        runValueCommand(gui.player) { result ->
            viewModel.cachedDynamicIndex = result
        }
    }

    private fun runValueCommand(player: ServerPlayer, onResult: (Int) -> Unit) {
        try {
            val source = player.createCommandSourceStack()
                .withPermission(PermissionSet.ALL_PERMISSIONS)
                .withSuppressedOutput()
            if (commandParseResult == null && !triedParse) {
                triedParse = true
                commandParseResult =
                    tryParseCommand(player.level().server, listOf("return run $valueCommand"))
            }
            commandParseResult?.executeFor(source) { _, result ->
                onResult(result)
            }
        } catch (e: Exception) {
            player.sendSystemMessage(
                component {
                    translatable("text.hgwar.shop_layer.dynamic_error") formatted ChatFormatting.RED
                }
            )
            logger.error(e.message, e)
        }
    }
}
