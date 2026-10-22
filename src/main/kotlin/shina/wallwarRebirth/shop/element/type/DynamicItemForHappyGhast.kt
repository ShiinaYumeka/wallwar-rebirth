package shina.wallwarRebirth.shop.element.type

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import eu.pb4.sgui.api.ClickType
import shina.wallwarRebirth.shop.element.BaseShopItem
import shina.wallwarRebirth.shop.element.view.ShopItemViewModel
import shina.wallwarRebirth.shop.gui.ShopGui
import shina.wallwarRebirth.util.playNotifySound
import shina.wallwarRebirth.util.text.component
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import org.slf4j.LoggerFactory
import java.util.*

class DynamicItemForHappyGhast(
    index: Int,
    valueCommand: String,
    entries: List<DynamicItemEntry>,
    fallback: BaseShopItem?
) : DynamicItem(index, valueCommand, entries, fallback) {

    companion object {
        private val logger = LoggerFactory.getLogger("hgwar/DynamicItemForHappyGhast")

        @JvmField
        val CODEC: MapCodec<DynamicItemForHappyGhast> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                Codec.intRange(0, 81).fieldOf("index")
                    .forGetter(DynamicItemForHappyGhast::index),
                Codec.STRING.fieldOf("value_command")
                    .forGetter(DynamicItemForHappyGhast::valueCommand),
                ENTRY_CODEC.listOf().optionalFieldOf("entries")
                    .forGetter { Optional.of(it.entries) },
                BaseShopItem.CODEC.optionalFieldOf("fallback")
                    .forGetter { Optional.ofNullable(it.fallback) }
            ).apply(instance) { index, valueCommand, entries, fallbackOpt ->
                DynamicItemForHappyGhast(
                    index,
                    if (valueCommand.startsWith('/')) valueCommand.substring(1) else valueCommand,
                    entries.orElseGet { emptyList() },
                    fallbackOpt.orElse(null)
                )
            }
        }
    }

    override fun type(): Type = Type.DYNAMIC_HAPPY_GHAST

    override fun shouldSkipAdd(gui: ShopGui): Boolean = gui.session.subject == null

    override fun executeBuying(item: BaseShopItem, gui: ShopGui, clickType: ClickType, materialSuffix: Component?) {
        val commandList = item.onBuy
        if (commandList.isEmpty()) return
        val happyGhast = gui.session.subject ?: return
        val level = happyGhast.level() as? ServerLevel ?: return
        val source = level.createCommandSourceStack()
            .withEntity(happyGhast)
            .withPosition(happyGhast.position())
            .withPermission(LevelBasedPermissionSet.GAMEMASTER)
            .withSuppressedOutput()
        val manager = level.server.commands
        commandList.forEach {
            manager.performPrefixedCommand(source, it)
        }

        val player = gui.player
        val broadcastMsg = component {
            empty()
            translatable(
                "text.hgwar.shop_layer.ghast_armor.buy",
                player.displayName,
                happyGhast.displayName,
                Component.empty().withStyle(ChatFormatting.WHITE).append(item.icon.hoverName)
            )
        }

        val playerMsg = if (!materialSuffix?.string.isNullOrEmpty()) {
            broadcastMsg.copy().append("  ").append(materialSuffix)
        } else {
            broadcastMsg.copy()
        }
        player.sendSystemMessage(playerMsg)
        // 队伍播报
        player.team
            ?.players
            ?.mapNotNull { level.server.playerList.getPlayerByName(it) }
            ?.filter { it != player && it.level() == level }
            ?.forEach { serverPlayer ->
                serverPlayer.sendSystemMessage(broadcastMsg)
                serverPlayer.playNotifySound(
                    SoundEvents.NOTE_BLOCK_PLING.value(),
                    SoundSource.PLAYERS,
                    0.7f,
                    1.6f
                )
            }
    }

    private fun ServerLevel.createCommandSourceStack() = CommandSourceStack(
        this.server,
        Vec3.atLowerCornerOf(respawnData.pos()),
        Vec2.ZERO,
        this,
        LevelBasedPermissionSet.GAMEMASTER,
        "Server",
        Component.translatable("text.hgwar.command_source.server"),
        this.server,
        null
    )

    override fun resolveCurrentEntry(gui: ShopGui, dynamicIndex: Int?): BaseShopItem? =
        dynamicIndex?.let { entriesByValue[it] }?.item ?: fallback

    override fun executeValueCommand(gui: ShopGui, viewModel: ShopItemViewModel) {
        val happyGhast = gui.session.subject ?: return
        val level = happyGhast.level() as? ServerLevel ?: return
        try {
            val source = level
                .createCommandSourceStack()
                .withEntity(happyGhast)
                .withSuppressedOutput()
            if (commandParseResult == null && !triedParse) {
                triedParse = true
                commandParseResult =
                    tryParseCommand(level.server, Collections.singletonList("return run $valueCommand"))
            }
            commandParseResult?.executeFor(source) { _, result ->
                viewModel.cachedDynamicIndex = result
            }
        } catch (e: Exception) {
            logger.error("处理乐魂动态物品时出错: 指令未正常解析")
            logger.error(e.message, e)
        }
    }
}
