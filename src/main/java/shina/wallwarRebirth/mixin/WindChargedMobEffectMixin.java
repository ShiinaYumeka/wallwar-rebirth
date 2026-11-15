package shina.wallwarRebirth.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobEffectInstance.class)
public class WindChargedMobEffectMixin {
    @Inject(method = "onMobRemoved", at = @At("HEAD"), cancellable = true)
    private void wallwar$noDeathBurst(ServerLevel level, LivingEntity entity, Entity.RemovalReason reason, CallbackInfo ci) {
        if (((MobEffectInstance) (Object) this).is(MobEffects.WIND_CHARGED)) {
            ci.cancel();
        }
    }
}
