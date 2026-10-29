package shina.wallwarRebirth.building

import com.mojang.serialization.MapCodec
import eu.pb4.polymer.core.api.block.PolymerBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import xyz.nucleoid.packettweaker.PacketContext

class SitePartBlock(properties: Properties, private val appearance: Block) : Block(properties), PolymerBlock {
    override fun codec(): MapCodec<out SitePartBlock> = simpleCodec { props -> SitePartBlock(props, appearance) }

    override fun getPolymerBlockState(state: BlockState, context: PacketContext): BlockState =
        appearance.defaultBlockState()
}
