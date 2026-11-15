package shina.wallwarRebirth.item

import eu.pb4.polymer.core.api.item.SimplePolymerItem
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
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
import shina.wallwarRebirth.building.Buildings
import shina.wallwarRebirth.building.OminousBanners
import shina.wallwarRebirth.building.SiteCores
import shina.wallwarRebirth.match.Match
import shina.wallwarRebirth.rules.MatchRules
import shina.wallwarRebirth.util.showActionBar

class PlunderBannerItem(
    properties: Properties,
    polymerItem: Item,
) : SimplePolymerItem(properties, polymerItem, false) {
    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
        val serverPlayer = player as? ServerPlayer ?: return InteractionResult.PASS
        if (level.isClientSide) return InteractionResult.SUCCESS
        val hit = serverPlayer.pick(MatchRules.COMMAND_RANGE, 1f, false)
        val pos = (hit as? BlockHitResult)?.blockPos
        if (pos == null) {
            serverPlayer.showActionBar(Component.literal("对准要固守的方块"))
            return InteractionResult.SUCCESS
        }
        assign(serverPlayer, serverPlayer.getItemInHand(hand), pos)
        return InteractionResult.SUCCESS
    }

    override fun useOn(context: UseOnContext): InteractionResult {
        val player = context.player as? ServerPlayer ?: return InteractionResult.PASS
        if (context.level.isClientSide) return InteractionResult.SUCCESS
        assign(player, context.itemInHand, context.clickedPos)
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
        @JvmStatic
        fun isBanner(stack: ItemStack): Boolean = stack.item is PlunderBannerItem

        fun tower(stack: ItemStack): BlockPos? {
            val packed = stack.get(ModComponents.BANNER_TOWER) ?: return null
            return BlockPos.of(packed)
        }

        fun create(tower: BlockPos, lookup: HolderLookup.Provider): ItemStack {
            val stack = ItemStack(ModItems.PLUNDER_BANNER)
            OminousBanners.apply(stack, lookup)
            applyPresentation(stack, tower, null)
            return stack
        }

        private fun assign(player: ServerPlayer, stack: ItemStack, pos: BlockPos) {
            if (!isBanner(stack) || !Match.isFight()) return
            val member = Match.member(player.uuid) ?: return
            if (member.team.isEmpty()) return
            val tower = tower(stack) ?: return
            val site = SiteCores.all().firstOrNull { core ->
                core.level === player.level() &&
                    core.blockPos == tower &&
                    !core.isRemoved &&
                    core.health > 0 &&
                    core.buildingId == Buildings.PILLAGER.id &&
                    core.team == member.team &&
                    !core.diffuse.isEmpty
            }
            if (site == null) {
                player.showActionBar(Component.literal("这座哨塔不在"))
                return
            }
            val chosen = pos.immutable()
            site.holdAnchor = chosen
            site.setChanged()
            applyPresentation(stack, tower, chosen)
            player.showActionBar(Component.literal("固守地点已改为 ${chosen.x} ${chosen.y} ${chosen.z}"))
        }

        private fun applyPresentation(stack: ItemStack, tower: BlockPos, anchor: BlockPos?) {
            stack.set(ModComponents.BANNER_TOWER, tower.asLong())
            stack[DataComponents.CUSTOM_NAME] = Component.literal("掠夺之旗").withStyle { style ->
                style.withItalic(false).withColor(ChatFormatting.WHITE)
            }
            val current = if (anchor == null) "哨塔" else "${anchor.x} ${anchor.y} ${anchor.z}"
            stack[DataComponents.LORE] = ItemLore(
                listOf(
                    Component.literal("所属哨塔 ${tower.x} ${tower.y} ${tower.z}").withStyle { style ->
                        style.withItalic(false).withColor(ChatFormatting.GRAY)
                    },
                    Component.literal("右键方块，把这座哨塔的固守地点改到那里").withStyle { style ->
                        style.withItalic(false).withColor(ChatFormatting.GRAY)
                    },
                    Component.literal("当前固守：$current").withStyle { style ->
                        style.withItalic(false).withColor(ChatFormatting.GRAY)
                    },
                ),
            )
        }
    }
}
