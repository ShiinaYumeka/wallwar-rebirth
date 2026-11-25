package shina.wallwarRebirth.mixin;

import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shina.wallwarRebirth.monster.SummonedUnit;

@Mixin(Mob.class)
public abstract class SummonedMobTargetMixin {
    @Inject(method = "serverAiStep", at = @At("HEAD"))
    private void wallwar$dropHiddenTarget(CallbackInfo ci) {
        if ((Object) this instanceof SummonedUnit unit) {
            unit.getMind().dropHiddenTarget();
        }
    }
}
