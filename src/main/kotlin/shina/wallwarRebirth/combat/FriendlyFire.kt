package shina.wallwarRebirth.combat

import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity

object FriendlyFire {
    @JvmStatic
    fun blocked(victim: Entity, source: DamageSource): Boolean {
        val attacker = source.entity ?: return false
        if (attacker === victim) return false
        val team = victim.team ?: return false
        if (!team.isAlliedTo(attacker.team)) return false
        return !team.isAllowFriendlyFire
    }
}
