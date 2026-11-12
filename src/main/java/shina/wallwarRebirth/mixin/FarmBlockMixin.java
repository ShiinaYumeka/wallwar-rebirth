package shina.wallwarRebirth.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shina.wallwarRebirth.building.FarmlandWork;

@Mixin(FarmBlock.class)
public class FarmBlockMixin {
    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void wallwar$skipProtectedFarmlandTick(
        BlockState state,
        ServerLevel level,
        BlockPos pos,
        RandomSource random,
        CallbackInfo ci
    ) {
        if (FarmlandWork.isProtected(level, pos)) ci.cancel();
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void wallwar$skipProtectedFarmlandSchedule(
        BlockState state,
        ServerLevel level,
        BlockPos pos,
        RandomSource random,
        CallbackInfo ci
    ) {
        if (FarmlandWork.isProtected(level, pos)) ci.cancel();
    }

    @Inject(method = "fallOn", at = @At("HEAD"), cancellable = true)
    private void wallwar$skipProtectedTrample(
        Level level,
        BlockState state,
        BlockPos pos,
        Entity entity,
        double fallDistance,
        CallbackInfo ci
    ) {
        if (FarmlandWork.isProtected(level, pos)) ci.cancel();
    }

    @Inject(method = "turnToDirt", at = @At("HEAD"), cancellable = true)
    private static void wallwar$keepProtectedFarmland(
        Entity entity,
        BlockState state,
        Level level,
        BlockPos pos,
        CallbackInfo ci
    ) {
        if (FarmlandWork.isProtected(level, pos)) ci.cancel();
    }
}
