package shina.wallwarRebirth.building

import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.phys.Vec3
import shina.wallwarRebirth.match.Match
import shina.wallwarRebirth.monster.SummonedUnits
import shina.wallwarRebirth.rules.MatchRules
import shina.wallwarRebirth.targeting.Targeting

object PillagerAura {
    private const val MARK = 40

    fun tick() {
        val covered = HashSet<Int>()
        if (Match.isFight()) {
            for (site in SiteCores.all()) {
                if (site.isRemoved || site.health <= 0) continue
                if (site.buildingId != Buildings.PILLAGER.id || site.reinforceTwo.isEmpty) continue
                val level = site.level ?: continue
                val origin = Vec3.atCenterOf(site.blockPos)
                for (unit in SummonedUnits.all()) {
                    val living = unit.living()
                    if (!living.isAlive || living.level() !== level) continue
                    if (site.team.isEmpty() || unit.summonedTeam != site.team) continue
                    if (Targeting.distanceToBox(origin, living.boundingBox) > MatchRules.DIFFUSION_RANGE) continue
                    living.addEffect(MobEffectInstance(MobEffects.RESISTANCE, MARK, 0, false, true))
                    covered.add(living.id)
                }
            }
        }
        for (unit in SummonedUnits.all()) {
            val living = unit.living()
            if (living.id in covered) continue
            val effect = living.getEffect(MobEffects.RESISTANCE) ?: continue
            if (effect.amplifier == 0 && effect.duration <= MARK) living.removeEffect(MobEffects.RESISTANCE)
        }
    }
}
