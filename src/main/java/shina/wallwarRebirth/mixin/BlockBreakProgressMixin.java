package shina.wallwarRebirth.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import shina.wallwarRebirth.building.FarmlandWork;

@Mixin(BlockBehaviour.class)
public class BlockBreakProgressMixin {
    @Inject(method = "getDestroyProgress", at = @At("HEAD"), cancellable = true)
    private void wallwar$protectWheat(
        BlockState state,
        Player player,
        BlockGetter level,
        BlockPos pos,
        CallbackInfoReturnable<Float> cir
    ) {
        if (state.is(Blocks.WHEAT) && FarmlandWork.isProtected(level, pos)) {
            cir.setReturnValue(0.0f);
        }
    }
}
