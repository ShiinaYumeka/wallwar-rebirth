package shina.wallwarRebirth.building

import eu.pb4.polymer.core.api.block.PolymerBlockUtils
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BannerBlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.entity.raid.Raid

object OminousBanners {
    fun apply(stack: ItemStack, lookup: HolderLookup.Provider): ItemStack {
        val patterns = Raid.getOminousBannerInstance(lookup.lookupOrThrow(Registries.BANNER_PATTERN))
            .get(DataComponents.BANNER_PATTERNS)
        if (patterns != null) stack.set(DataComponents.BANNER_PATTERNS, patterns)
        return stack
    }

    fun packet(pos: BlockPos, lookup: HolderLookup.Provider): ClientboundBlockEntityDataPacket {
        return PolymerBlockUtils.createBlockEntityPacket(pos, BlockEntityType.BANNER, updateTag(pos, lookup))
    }

    private fun updateTag(pos: BlockPos, lookup: HolderLookup.Provider): CompoundTag {
        val banner = BannerBlockEntity(pos, Blocks.WHITE_BANNER.defaultBlockState())
        val stack = Raid.getOminousBannerInstance(lookup.lookupOrThrow(Registries.BANNER_PATTERN))
        banner.applyComponentsFromItemStack(stack)
        return banner.getUpdateTag(lookup)
    }
}
