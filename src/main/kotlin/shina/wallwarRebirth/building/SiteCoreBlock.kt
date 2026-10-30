package shina.wallwarRebirth.building

import com.mojang.serialization.MapCodec
import eu.pb4.polymer.core.api.block.PolymerBlock
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.util.StringRepresentable
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.phys.BlockHitResult
import xyz.nucleoid.packettweaker.PacketContext

class SiteCoreBlock(properties: Properties) : BaseEntityBlock(properties), PolymerBlock {
    init {
        registerDefaultState(stateDefinition.any().setValue(FORM, CoreForm.BEACON))
    }

    override fun codec(): MapCodec<out SiteCoreBlock> = simpleCodec(::SiteCoreBlock)

    override fun getPolymerBlockState(state: BlockState, context: PacketContext): BlockState = when (state.getValue(FORM)) {
        CoreForm.BEACON -> Blocks.BEACON.defaultBlockState()
        CoreForm.BLAST_FURNACE -> Blocks.BLAST_FURNACE.defaultBlockState()
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FORM)
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = SiteCoreBlockEntity(pos, state)

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun getDestroyProgress(
        state: BlockState,
        player: Player,
        level: net.minecraft.world.level.BlockGetter,
        pos: BlockPos,
    ): Float = BuildingMining.progress(state, player, level, pos) { super.getDestroyProgress(state, player, level, pos) }

    override fun <T : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        type: BlockEntityType<T>,
    ): BlockEntityTicker<T>? {
        if (level.isClientSide) return null
        return BlockEntityTicker { server, _, _, blockEntity ->
            (blockEntity as? SiteCoreBlockEntity)?.serverTick(server as ServerLevel)
        }
    }

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hit: BlockHitResult,
    ): InteractionResult = open(level, pos, player)

    override fun useItemOn(
        stack: ItemStack,
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hit: BlockHitResult,
    ): InteractionResult {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS
        return open(level, pos, player)
    }

    private fun open(level: Level, pos: BlockPos, player: Player): InteractionResult {
        if (level.isClientSide) return InteractionResult.SUCCESS
        val serverPlayer = player as? ServerPlayer ?: return InteractionResult.PASS
        val site = level.getBlockEntity(pos) as? SiteCoreBlockEntity ?: return InteractionResult.PASS
        if (site.team.isNotEmpty() && !site.isAlly(serverPlayer)) {
            serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal("敌方打不开"))
            return InteractionResult.SUCCESS
        }
        BuildingMenus.open(serverPlayer, site)
        return InteractionResult.SUCCESS
    }

    companion object {
        val FORM: EnumProperty<CoreForm> = EnumProperty.create("form", CoreForm::class.java)
    }
}

enum class CoreForm : StringRepresentable {
    BEACON,
    BLAST_FURNACE,
    ;

    override fun getSerializedName(): String = name.lowercase()
}
