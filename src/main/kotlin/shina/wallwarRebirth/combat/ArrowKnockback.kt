package shina.wallwarRebirth.combat

import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.projectile.arrow.AbstractArrow
import shina.wallwarRebirth.rules.MatchRules

object ArrowKnockback {
    private val scale = ThreadLocal.withInitial { 1.0 }

    @JvmStatic
    fun enter(source: DamageSource) {
        scale.set(if (source.directEntity is AbstractArrow) MatchRules.ARROW_KNOCKBACK_SCALE else 1.0)
    }

    @JvmStatic
    fun exit() {
        scale.set(1.0)
    }

    @JvmStatic
    fun apply(strength: Double): Double = strength * scale.get()
}
