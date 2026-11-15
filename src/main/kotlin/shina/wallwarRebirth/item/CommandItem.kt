package shina.wallwarRebirth.item

import eu.pb4.polymer.core.api.item.SimplePolymerItem
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import shina.wallwarRebirth.match.Match
import shina.wallwarRebirth.monster.SummonedUnits
import shina.wallwarRebirth.util.showActionBar

class CommandItem(
    properties: Properties,
    polymerItem: Item,
) : SimplePolymerItem(properties, polymerItem, false) {
    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
        val serverPlayer = player as? ServerPlayer ?: return InteractionResult.PASS
        if (level.isClientSide) return InteractionResult.SUCCESS
        command(serverPlayer, serverPlayer.getItemInHand(hand))
        return InteractionResult.SUCCESS
    }

    override fun useOn(context: UseOnContext): InteractionResult {
        val player = context.player as? ServerPlayer ?: return InteractionResult.PASS
        if (context.level.isClientSide) return InteractionResult.SUCCESS
        command(player, context.itemInHand)
        return InteractionResult.SUCCESS
    }

    override fun isPolymerBlockInteraction(
        state: BlockState?,
        player: ServerPlayer?,
        hand: InteractionHand?,
        stack: ItemStack?,
        world: ServerLevel?,
        blockHitResult: BlockHitResult?,
        actionResult: InteractionResult?,
    ): Boolean = true

    override fun isPolymerEntityInteraction(
        player: ServerPlayer?,
        hand: InteractionHand?,
        stack: ItemStack?,
        world: ServerLevel?,
        entity: Entity?,
        actionResult: InteractionResult?,
    ): Boolean = true

    override fun isPolymerItemInteraction(
        player: ServerPlayer?,
        hand: InteractionHand?,
        stack: ItemStack?,
        world: ServerLevel?,
        actionResult: InteractionResult?,
    ): Boolean = true

    companion object {
        private val LORE = ItemLore(
            listOf(
                Component.literal("选中时按副手键（默认 F）切换跟随和出征").withStyle { style ->
                    style.withItalic(false).withColor(ChatFormatting.GRAY)
                },
                Component.literal("右键指挥面向的一只同队唤灵").withStyle { style ->
                    style.withItalic(false).withColor(ChatFormatting.GRAY)
                },
                Component.literal("蹲下右键指挥 8 格内的同队唤灵").withStyle { style ->
                    style.withItalic(false).withColor(ChatFormatting.GRAY)
                },
            ),
        )

        @JvmStatic
        fun isCommand(stack: ItemStack): Boolean = stack.item is CommandItem

        fun isFollow(stack: ItemStack): Boolean = stack.get(ModComponents.COMMAND_FOLLOW) != false

        fun create(follow: Boolean = true): ItemStack {
            val stack = ItemStack(ModItems.COMMAND)
            applyPresentation(stack, follow)
            return stack
        }

        @JvmStatic
        fun toggle(player: ServerPlayer, stack: ItemStack) {
            if (!isCommand(stack)) return
            applyPresentation(stack, !isFollow(stack))
        }

        fun applyPresentation(stack: ItemStack, follow: Boolean) {
            stack.set(ModComponents.COMMAND_FOLLOW, follow)
            val title = if (follow) "指挥：跟随" else "指挥：出征"
            stack[DataComponents.CUSTOM_NAME] = Component.literal(title).withStyle { style ->
                style.withItalic(false).withColor(ChatFormatting.WHITE)
            }
            stack[DataComponents.LORE] = LORE
        }

        private fun command(player: ServerPlayer, stack: ItemStack) {
            if (!isCommand(stack)) return
            if (!Match.isFight()) return
            val member = Match.member(player.uuid) ?: return
            if (member.team.isEmpty() || player.isSpectator) return
            val follow = isFollow(stack)
            val area = player.isShiftKeyDown
            val count = if (area) SummonedUnits.commandNearby(player, follow) else SummonedUnits.commandFacing(player, follow)
            val text = if (count == 0) {
                if (area) "附近没有可指挥的唤灵" else "没有面向可指挥的唤灵"
            } else {
                val mode = if (follow) "跟随" else "出征"
                "已指挥 $count 只唤灵$mode"
            }
            player.showActionBar(Component.literal(text))
        }
    }
}
