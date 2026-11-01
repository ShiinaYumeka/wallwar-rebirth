package shina.wallwarRebirth.item

import eu.pb4.polymer.core.api.item.SimplePolymerItem
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import shina.wallwarRebirth.building.Buildings
import shina.wallwarRebirth.building.ModSites
import shina.wallwarRebirth.building.SiteCoreBlockEntity
import shina.wallwarRebirth.building.reportBuilding
import shina.wallwarRebirth.item.core.PolymerInteractionType
import shina.wallwarRebirth.match.Match

class BuildingCoreItem(
    properties: Properties,
    polymerItem: Item,
    private val buildingId: String,
) : SimplePolymerItem(properties, polymerItem, false) {
    private val interactionType = PolymerInteractionType.SERVER

    override fun useOn(context: UseOnContext): InteractionResult {
        val level = context.level
        if (level.isClientSide) return InteractionResult.SUCCESS
        val player = context.player as? ServerPlayer ?: return InteractionResult.PASS
        val world = level as? ServerLevel ?: return InteractionResult.PASS
        if (!Match.isArena(world)) {
            player.sendSystemMessage(Component.literal("只能放在对局地图里"))
            return InteractionResult.FAIL
        }
        if (Match.phase != Match.Phase.LOBBY && !Match.isFight()) {
            player.reportBuilding("现在不能放置")
            return InteractionResult.FAIL
        }
        val definition = Buildings.byId(buildingId) ?: return InteractionResult.FAIL
        val feet = context.clickedPos.relative(context.clickedFace)
        if (world.isOutsideBuildHeight(feet)) {
            player.reportBuilding("这个位置不能放置")
            return InteractionResult.FAIL
        }
        val hit = BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(feet), context.clickedFace, feet, false)
        val place = BlockPlaceContext(player, context.hand, ItemStack.EMPTY, hit)
        if (!world.getBlockState(feet).canBeReplaced(place)) {
            player.reportBuilding("这个位置不能放置")
            return InteractionResult.FAIL
        }
        world.setBlock(feet, ModSites.CORE.defaultBlockState(), Block.UPDATE_ALL)
        val site = world.getBlockEntity(feet) as? SiteCoreBlockEntity
        val facing = Direction.byName(player.direction.serializedName) ?: player.direction
        val error = site?.assign(definition, false, player.team?.name ?: "", facing)
        if (site == null || error != null) {
            world.setBlock(feet, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)
            player.reportBuilding(error ?: "这个位置不能放置")
            return InteractionResult.FAIL
        }
        context.itemInHand.shrink(1)
        player.sendSystemMessage(Component.literal("已放置${definition.displayName}"))
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
