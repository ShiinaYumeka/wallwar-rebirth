package shina.wallwarRebirth.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shina.wallwarRebirth.building.FarmlandWork;

@Mixin(CropBlock.class)
public class CropBlockMixin {
    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void wallwar$skipProtectedWheat(
        BlockState state,
        ServerLevel level,
        BlockPos pos,
        RandomSource random,
        CallbackInfo ci
    ) {
        if (FarmlandWork.isProtected(level, pos)) ci.cancel();
    }

    @Inject(method = "performBonemeal", at = @At("HEAD"), cancellable = true)
    private void wallwar$skipProtectedBonemeal(
        ServerLevel level,
        RandomSource random,
        BlockPos pos,
        BlockState state,
        CallbackInfo ci
    ) {
        if (FarmlandWork.isProtected(level, pos)) ci.cancel();
    }
}
