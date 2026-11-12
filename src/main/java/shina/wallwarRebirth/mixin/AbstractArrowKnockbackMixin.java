package shina.wallwarRebirth.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import shina.wallwarRebirth.rules.MatchRules;

@Mixin(AbstractArrow.class)
public class AbstractArrowKnockbackMixin {
    @Redirect(
        method = "doKnockback",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;push(DDD)V")
    )
    private void wallwar$scalePunchKnockback(LivingEntity target, double x, double y, double z) {
        double scale = MatchRules.ARROW_KNOCKBACK_SCALE;
        target.push(x * scale, y * scale, z * scale);
    }
}
