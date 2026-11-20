package shina.wallwarRebirth.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import shina.wallwarRebirth.building.FarmlandWork;

@Mixin(FireBlock.class)
public class FireBlockMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void wallwar$keepProtectedFireTick(
        BlockState state,
        ServerLevel level,
        BlockPos pos,
        RandomSource random,
        CallbackInfo ci
    ) {
        if (FarmlandWork.isProtected(level, pos)) ci.cancel();
    }

    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
    private void wallwar$keepProtectedFireSurvive(
        BlockState state,
        LevelReader level,
        BlockPos pos,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (level instanceof net.minecraft.world.level.BlockGetter getter && FarmlandWork.isProtected(getter, pos)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "updateShape", at = @At("HEAD"), cancellable = true)
    private void wallwar$keepProtectedFireShape(
        BlockState state,
        LevelReader level,
        ScheduledTickAccess scheduledTicks,
        BlockPos pos,
        Direction direction,
        BlockPos neighborPos,
        BlockState neighborState,
        RandomSource random,
        CallbackInfoReturnable<BlockState> cir
    ) {
        if (level instanceof net.minecraft.world.level.BlockGetter getter && FarmlandWork.isProtected(getter, pos)) {
            cir.setReturnValue(state);
        }
    }
}
