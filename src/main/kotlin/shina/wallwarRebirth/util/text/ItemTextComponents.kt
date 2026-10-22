package shina.wallwarRebirth.util.text

import net.minecraft.ChatFormatting
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.data.AtlasIds
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.contents.objects.AtlasSprite
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack

object ItemTextComponents {
    private const val ITEMS_PER_ROW = 3

    val gridRowBreak: MutableComponent = Component.literal("\n")
    val styleGrayNoItalic: Style = Style.EMPTY.withColor(ChatFormatting.GRAY).withItalic(false)

    fun actorDisplayNameWithEntityHover(actor: ServerPlayer): MutableComponent {
        val teamColor = actor.team?.color ?: ChatFormatting.GOLD
        val entityHover = HoverEvent.ShowEntity(
            HoverEvent.EntityTooltipInfo(actor.type, actor.uuid, actor.displayName),
        )
        return actor.displayName.copy().withStyle {
            it.withColor(teamColor).withHoverEvent(entityHover).withItalic(false)
        }
    }

    fun appendItemGrid(root: MutableComponent, stacks: List<Pair<ItemStack, Int>>) {
        if (stacks.isEmpty()) return
        stacks.chunked(ITEMS_PER_ROW).forEachIndexed { rowIdx, row ->
            row.forEachIndexed { colIdx, (stack, count) ->
                when {
                    colIdx > 0 -> root.append("    ")
                    rowIdx > 0 -> root.append(gridRowBreak)
                }
                root.append(itemEntry(stack, count))
            }
        }
    }

    private fun itemEntry(stack: ItemStack, count: Int): MutableComponent {
        val shown = stack.copyWithCount(1)
        val itemId = BuiltInRegistries.ITEM.getKey(shown.item)
        val blockItem = shown.item is BlockItem
        val atlas = if (blockItem) AtlasIds.BLOCKS else AtlasIds.ITEMS
        val sprite = if (blockItem) {
            Identifier.withDefaultNamespace("block/${itemId.path}")
        } else {
            Identifier.withDefaultNamespace("item/${itemId.path}")
        }
        return component {
            empty()
            raw(Component.`object`(AtlasSprite(atlas, sprite)))
            text(" ")
            raw(shown.hoverName)
            text(" ×$count") provide styleGrayNoItalic
        }.withStyle { it.withHoverEvent(HoverEvent.ShowItem(shown)) }
    }
}
