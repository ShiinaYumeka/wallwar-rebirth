package shina.wallwarRebirth.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import shina.wallwarRebirth.building.FarmlandWork;

@Mixin(TntBlock.class)
public class TntBlockMixin {
    @Inject(method = "onPlace", at = @At("HEAD"), cancellable = true)
    private void wallwar$protectPlacedTnt(
        BlockState state,
        Level level,
        BlockPos pos,
        BlockState oldState,
        boolean moved,
        CallbackInfo ci
    ) {
        if (FarmlandWork.isProtected(level, pos)) {
            if (state.hasProperty(BlockStateProperties.UNSTABLE) && state.getValue(BlockStateProperties.UNSTABLE)) {
                level.setBlock(pos, state.setValue(BlockStateProperties.UNSTABLE, false), 3);
            }
            ci.cancel();
        }
    }

    @Inject(method = "neighborChanged", at = @At("HEAD"), cancellable = true)
    private void wallwar$protectNeighborTnt(
        BlockState state,
        Level level,
        BlockPos pos,
        net.minecraft.world.level.block.Block neighbor,
        net.minecraft.world.level.redstone.Orientation orientation,
        boolean moved,
        CallbackInfo ci
    ) {
        if (FarmlandWork.isProtected(level, pos)) ci.cancel();
    }

    @Inject(method = "wasExploded", at = @At("HEAD"), cancellable = true)
    private void wallwar$protectExplodedTnt(
        ServerLevel level,
        BlockPos pos,
        Explosion explosion,
        CallbackInfo ci
    ) {
        if (FarmlandWork.isProtected(level, pos)) ci.cancel();
    }

    @Inject(method = "onProjectileHit", at = @At("HEAD"), cancellable = true)
    private void wallwar$protectProjectileTnt(
        Level level,
        BlockState state,
        net.minecraft.world.phys.BlockHitResult hit,
        Projectile projectile,
        CallbackInfo ci
    ) {
        if (FarmlandWork.isProtected(level, hit.getBlockPos())) ci.cancel();
    }

    @Inject(
        method = "prime(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/LivingEntity;)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void wallwar$protectPrimeTnt(
        Level level,
        BlockPos pos,
        LivingEntity igniter,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (FarmlandWork.isProtected(level, pos)) {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }
}
