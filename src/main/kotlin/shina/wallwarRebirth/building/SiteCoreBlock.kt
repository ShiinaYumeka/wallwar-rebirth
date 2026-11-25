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
import net.minecraft.core.Direction
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.DispenserBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.phys.BlockHitResult
import shina.wallwarRebirth.shop.match.BaseShop
import xyz.nucleoid.packettweaker.PacketContext

class SiteCoreBlock(properties: Properties) : BaseEntityBlock(properties), PolymerBlock {
    init {
        registerDefaultState(stateDefinition.any().setValue(FORM, CoreForm.BEACON))
    }

    override fun codec(): MapCodec<out SiteCoreBlock> = simpleCodec(::SiteCoreBlock)

    override fun getPolymerBlockState(state: BlockState, context: PacketContext): BlockState = when (state.getValue(FORM)) {
        CoreForm.BEACON -> Blocks.BEACON.defaultBlockState()
        CoreForm.BLAST_FURNACE -> Blocks.BLAST_FURNACE.defaultBlockState()
        CoreForm.FURNACE -> Blocks.FURNACE.defaultBlockState()
        CoreForm.FLETCHING_TABLE -> Blocks.FLETCHING_TABLE.defaultBlockState()
        CoreForm.CRAFTER -> Blocks.CRAFTER.defaultBlockState()
        CoreForm.COPPER_BULB -> Blocks.WAXED_COPPER_BULB.defaultBlockState()
        CoreForm.DISPENSER -> Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.UP)
        CoreForm.HOPPER -> Blocks.HOPPER.defaultBlockState()
        CoreForm.LIGHTNING_ROD -> Blocks.LIGHTNING_ROD.defaultBlockState()
        CoreForm.PILLAGER_BANNER -> Blocks.WHITE_BANNER.defaultBlockState()
        CoreForm.COMPOSTER -> Blocks.COMPOSTER.defaultBlockState()
        CoreForm.REDSTONE_LAMP -> Blocks.REDSTONE_LAMP.defaultBlockState()
        CoreForm.SEA_LANTERN -> Blocks.SEA_LANTERN.defaultBlockState()
        CoreForm.LODESTONE -> Blocks.LODESTONE.defaultBlockState()
    }

    override fun onPolymerBlockSend(
        state: BlockState,
        pos: BlockPos.MutableBlockPos,
        context: PacketContext.NotNullWithPlayer,
    ) {
        if (state.getValue(FORM) != CoreForm.PILLAGER_BANNER) return
        val player = context.player ?: return
        player.connection.send(OminousBanners.packet(pos.immutable(), player.registryAccess()))
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

    private fun open(level: Level, pos: BlockPos, player: Player): InteractionResult =
        openAt(level, pos, player)

    companion object {
        val FORM: EnumProperty<CoreForm> = EnumProperty.create("form", CoreForm::class.java)

        fun openAt(level: Level, pos: BlockPos, player: Player): InteractionResult {
            if (level.isClientSide) return InteractionResult.SUCCESS
            val serverPlayer = player as? ServerPlayer ?: return InteractionResult.PASS
            val direct = level.getBlockEntity(pos) as? SiteCoreBlockEntity
            val site = direct ?: run {
                val part = level.getBlockEntity(pos) as? SiteStructureBlockEntity ?: return InteractionResult.PASS
                SiteCores.find(level, part.corePos)
            } ?: return InteractionResult.PASS
            openSite(serverPlayer, site)
            return InteractionResult.SUCCESS
        }

        private fun openSite(player: ServerPlayer, site: SiteCoreBlockEntity) {
            if (CannonCrew.tryEnter(site, player)) return
            if (site.buildingId == Buildings.MINERAL_REACTOR.id) {
                val refusal = MineralReactorCapture.openRefusal(player)
                if (refusal != null) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(refusal))
                    return
                }
                BuildingMenus.open(player, site)
                return
            }
            if (site.team.isNotEmpty() && !site.isAlly(player)) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("敌方打不开"))
                return
            }
            if (site.buildingId == Buildings.BASE.id) {
                BaseShop.open(player, site)
            } else {
                BuildingMenus.open(player, site)
            }
        }
    }

}

enum class CoreForm : StringRepresentable {
    BEACON,
    BLAST_FURNACE,
    FURNACE,
    FLETCHING_TABLE,
    CRAFTER,
    COPPER_BULB,
    DISPENSER,
    HOPPER,
    LIGHTNING_ROD,
    PILLAGER_BANNER,
    COMPOSTER,
    REDSTONE_LAMP,
    SEA_LANTERN,
    LODESTONE,
    ;

    override fun getSerializedName(): String = name.lowercase()
}
