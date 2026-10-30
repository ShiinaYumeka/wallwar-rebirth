package shina.wallwarRebirth.building

import com.mojang.serialization.MapCodec
import eu.pb4.polymer.core.api.block.PolymerBlock
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import xyz.nucleoid.packettweaker.PacketContext

class SiteStructureBlock(properties: Properties) : BaseEntityBlock(properties), PolymerBlock {
    override fun codec(): MapCodec<out SiteStructureBlock> = simpleCodec(::SiteStructureBlock)

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = SiteStructureBlockEntity(pos, state)

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun getPolymerBlockState(state: BlockState, context: PacketContext): BlockState =
        Blocks.STONE.defaultBlockState()

    override fun onPolymerBlockSend(
        state: BlockState,
        pos: BlockPos.MutableBlockPos,
        context: PacketContext.NotNullWithPlayer,
    ) {
        val player = context.player ?: return
        val immutable = pos.immutable()
        val appearance = (player.level().getBlockEntity(immutable) as? SiteStructureBlockEntity)?.appearance
            ?: return
        player.connection.send(ClientboundBlockUpdatePacket(immutable, appearance))
    }

    override fun getDestroyProgress(
        state: BlockState,
        player: net.minecraft.world.entity.player.Player,
        level: net.minecraft.world.level.BlockGetter,
        pos: BlockPos,
    ): Float = BuildingMining.progress(state, player, level, pos) { super.getDestroyProgress(state, player, level, pos) }
}

class SiteStructureBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(ModSites.STRUCTURE_TYPE, pos, state) {
    var appearance: BlockState = Blocks.STONE.defaultBlockState()
    var corePos: BlockPos = BlockPos.ZERO

    override fun saveAdditional(output: ValueOutput) {
        super.saveAdditional(output)
        output.store("Appearance", BlockState.CODEC, appearance)
        output.putInt("CoreX", corePos.x)
        output.putInt("CoreY", corePos.y)
        output.putInt("CoreZ", corePos.z)
    }

    override fun loadAdditional(input: ValueInput) {
        super.loadAdditional(input)
        appearance = input.read("Appearance", BlockState.CODEC).orElse(Blocks.STONE.defaultBlockState())
        corePos = BlockPos(input.getIntOr("CoreX", 0), input.getIntOr("CoreY", 0), input.getIntOr("CoreZ", 0))
    }
}
