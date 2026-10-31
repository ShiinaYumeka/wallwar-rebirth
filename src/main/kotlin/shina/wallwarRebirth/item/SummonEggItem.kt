package shina.wallwarRebirth.item

import eu.pb4.polymer.core.api.item.SimplePolymerItem
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import shina.wallwarRebirth.item.core.PolymerInteractionType
import shina.wallwarRebirth.match.Match
import shina.wallwarRebirth.match.MatchPlacement
import shina.wallwarRebirth.monster.Monsters
import shina.wallwarRebirth.monster.Summoning

class SummonEggItem(
    properties: Item.Properties,
    polymerItem: Item,
    private val monsterId: String,
) : SimplePolymerItem(properties, polymerItem, false) {
    private val interactionType = PolymerInteractionType.SERVER

    override fun useOn(context: UseOnContext): InteractionResult {
        val level = context.level
        if (level.isClientSide) return InteractionResult.SUCCESS
        val player = context.player as? ServerPlayer ?: return InteractionResult.PASS
        val world = level as? ServerLevel ?: return InteractionResult.PASS
        val definition = Monsters.byId(monsterId) ?: return InteractionResult.PASS
        if (Match.isArena(world) && !Match.isFight()) {
            player.sendSystemMessage(Component.literal("现在不能召唤"))
            return InteractionResult.FAIL
        }
        val feet = context.clickedPos.relative(context.clickedFace)
        if (Match.isArena(world) && !MatchPlacement.ownHalf(world, feet, player.team?.name ?: "")) {
            player.sendSystemMessage(Component.literal("只能放在离己方基地更近的一侧"))
            return InteractionResult.FAIL
        }
        val head = feet.above()
        if (!Summoning.canOccupy(world, feet, player) || !Summoning.canOccupy(world, head, player)) {
            player.sendSystemMessage(Component.literal("这个位置不能放置"))
            return InteractionResult.FAIL
        }
        val spawned = Summoning.spawn(
            world,
            definition,
            player.team?.name ?: "",
            feet.x + 0.5,
            feet.y.toDouble(),
            feet.z + 0.5,
            player.yRot,
        )
        if (!spawned) {
            player.sendSystemMessage(Component.literal("这个位置不能放置"))
            return InteractionResult.FAIL
        }
        context.itemInHand.shrink(1)
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
    ): Boolean = interactionType.blockInteraction

    override fun isPolymerEntityInteraction(
        player: ServerPlayer?,
        hand: InteractionHand?,
        stack: ItemStack?,
        world: ServerLevel?,
        entity: Entity?,
        actionResult: InteractionResult?,
    ): Boolean = interactionType.entityInteraction

    override fun isPolymerItemInteraction(
        player: ServerPlayer?,
        hand: InteractionHand?,
        stack: ItemStack?,
        world: ServerLevel?,
        actionResult: InteractionResult?,
    ): Boolean = interactionType.itemInteraction

    override fun isIgnoringBlockInteractionPlaySoundExceptedEntity(
        state: BlockState?,
        player: ServerPlayer?,
        hand: InteractionHand?,
        stack: ItemStack?,
        world: ServerLevel?,
        blockHitResult: BlockHitResult?,
    ): Boolean = interactionType.blockInteractionPlaySound

    override fun isIgnoringItemInteractionPlaySoundExceptedEntity(
        player: ServerPlayer?,
        hand: InteractionHand?,
        stack: ItemStack?,
        world: ServerLevel?,
    ): Boolean = interactionType.itemInteractionPlaySound

}
