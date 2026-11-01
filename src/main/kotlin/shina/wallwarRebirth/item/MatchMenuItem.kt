package shina.wallwarRebirth.item

import eu.pb4.polymer.core.api.item.SimplePolymerItem
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import shina.wallwarRebirth.match.MatchGui

class MatchMenuItem(
    properties: Properties,
    polymerItem: Item,
) : SimplePolymerItem(properties, polymerItem, true) {
    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
        val serverPlayer = player as? ServerPlayer ?: return InteractionResult.PASS
        if (level.isClientSide) return InteractionResult.SUCCESS
        MatchGui.openEntry(serverPlayer)
        return InteractionResult.SUCCESS
    }

    override fun useOn(context: UseOnContext): InteractionResult {
        val player = context.player as? ServerPlayer ?: return InteractionResult.PASS
        if (context.level.isClientSide) return InteractionResult.SUCCESS
        MatchGui.openEntry(player)
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
    ): Boolean = false

    override fun isPolymerItemInteraction(
        player: ServerPlayer?,
        hand: InteractionHand?,
        stack: ItemStack?,
        world: ServerLevel?,
        actionResult: InteractionResult?,
    ): Boolean = true

    companion object {
        fun isMenu(stack: ItemStack): Boolean = stack.item is MatchMenuItem
    }
}
