package shina.wallwarRebirth.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shina.wallwarRebirth.match.MatchHooks;

@Mixin(CrafterBlockEntity.class)
public class CrafterBlockEntityMixin {
    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private static void wallwar$blockCraft(Level level, BlockPos pos, BlockState state, CrafterBlockEntity entity, CallbackInfo ci) {
        if (MatchHooks.blockCrafter()) ci.cancel();
    }
}
