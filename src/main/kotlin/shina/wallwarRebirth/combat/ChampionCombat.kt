package shina.wallwarRebirth.combat

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import shina.wallwarRebirth.profession.ProfessionEffects
import shina.wallwarRebirth.rules.MatchRules

object ChampionCombat {
    @JvmStatic
    fun adjust(victim: LivingEntity, source: DamageSource, amount: Float): Float {
        if (amount <= 0f) return amount
        var next = amount
        val attacker = source.entity as? ServerPlayer
        if (attacker != null && ProfessionEffects.championArmed(attacker) && victim !is Player) {
            next *= MatchRules.CHAMPION_OUTGOING_CREATURE
        }
        val player = victim as? ServerPlayer
        if (player != null && ProfessionEffects.championArmed(player) && !fromPlayer(source)) {
            next *= MatchRules.CHAMPION_INCOMING_NONPLAYER
        }
        return next
    }

    private fun fromPlayer(source: DamageSource): Boolean =
        source.entity is Player || source.directEntity is Player
}
