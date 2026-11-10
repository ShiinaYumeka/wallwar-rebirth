package shina.wallwarRebirth.mixin;

import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shina.wallwarRebirth.monster.SummonedCreeper;

@Mixin(Creeper.class)
public class CreeperExplodeMixin {
    @Inject(method = "explodeCreeper", at = @At("HEAD"), cancellable = true)
    private void wallwar$replaceExplosion(CallbackInfo ci) {
        if ((Object) this instanceof SummonedCreeper creeper) {
            creeper.burst();
            ci.cancel();
        }
    }
}
