package shina.wallwarRebirth.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.LavaFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shina.wallwarRebirth.building.FarmlandWork;

@Mixin(LavaFluid.class)
public class LavaFluidMixin {
    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void wallwar$keepProtectedLava(
        ServerLevel level,
        BlockPos pos,
        FluidState state,
        RandomSource random,
        CallbackInfo ci
    ) {
        if (FarmlandWork.isProtected(level, pos)) ci.cancel();
    }
}
