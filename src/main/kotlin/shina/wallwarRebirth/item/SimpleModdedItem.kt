package shina.wallwarRebirth.item

import eu.pb4.polymer.core.api.item.SimplePolymerItem
import shina.wallwarRebirth.item.core.PolymerInteractionType
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import xyz.nucleoid.packettweaker.PacketContext

/**
 * @param properties     物品属性（堆叠大小、耐久等）
 * @param polymerItem    在客户端显示的 vanilla 物品，默认为爆裂紫颂果
 * @property modelProvider  自定义模型提供器，`null` 则使用 polymerItem 的默认模型
 * @property interactionType 控制交互与音效异常标记的组合
 */
class SimpleModdedItem(
    properties: Item.Properties,
    polymerItem: Item = Items.POPPED_CHORUS_FRUIT,
    private val modelProvider: ModelProvider? = null,
    private val interactionType: PolymerInteractionType = PolymerInteractionType.DEFAULT,
) : SimplePolymerItem(properties, polymerItem, true) {

    override fun getPolymerItemModel(stack: ItemStack, context: PacketContext): Identifier? {
        if (modelProvider != null) {
            return modelProvider.getModel(stack, context)
        }
        return super.getPolymerItemModel(stack, context)
    }

    // ==================== PolymerItem 覆写 — 交互 / 音效标记（枚举驱动）====================

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

    fun interface ModelProvider {
        fun getModel(stack: ItemStack, context: PacketContext): Identifier?

        class FinalModelProvider(private val cached: Identifier?) : ModelProvider {
            override fun getModel(
                stack: ItemStack,
                context: PacketContext
            ): Identifier? = cached
        }

        companion object {
            fun of(item: Item) = FinalModelProvider(item.components()[DataComponents.ITEM_MODEL])
        }
    }
}
