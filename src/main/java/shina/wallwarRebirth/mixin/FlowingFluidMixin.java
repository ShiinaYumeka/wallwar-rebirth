package shina.wallwarRebirth.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shina.wallwarRebirth.building.FarmlandWork;

@Mixin(FlowingFluid.class)
public class FlowingFluidMixin {
    @Inject(method = "spread", at = @At("HEAD"), cancellable = true)
    private void wallwar$keepProtectedFluid(
        ServerLevel level,
        BlockPos pos,
        BlockState state,
        FluidState fluid,
        CallbackInfo ci
    ) {
        if (FarmlandWork.isProtected(level, pos)) ci.cancel();
    }

    @Inject(method = "spreadTo", at = @At("HEAD"), cancellable = true)
    private void wallwar$keepProtectedFluidDest(
        LevelAccessor level,
        BlockPos pos,
        BlockState state,
        Direction direction,
        FluidState fluid,
        CallbackInfo ci
    ) {
        if (FarmlandWork.isProtected(level, pos)) ci.cancel();
    }
}
