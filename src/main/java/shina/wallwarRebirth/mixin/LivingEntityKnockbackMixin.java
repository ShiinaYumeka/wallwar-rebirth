package shina.wallwarRebirth.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import shina.wallwarRebirth.combat.ArrowKnockback;

@Mixin(LivingEntity.class)
public class LivingEntityKnockbackMixin {
    @Inject(method = "hurtServer", at = @At("HEAD"))
    private void wallwar$enterArrowKnockback(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        ArrowKnockback.enter(source);
    }

    @Inject(method = "hurtServer", at = @At("RETURN"))
    private void wallwar$exitArrowKnockback(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        ArrowKnockback.exit();
    }

    @ModifyVariable(method = "knockback(DDD)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double wallwar$scaleArrowKnockback(double strength) {
        return ArrowKnockback.apply(strength);
    }
}
