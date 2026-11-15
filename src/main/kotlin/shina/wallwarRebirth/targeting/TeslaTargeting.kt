package shina.wallwarRebirth.targeting

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

/** Nearest enemy without Wind Charged. Players come before summons. No buildings. */
object TeslaTargeting {
    fun select(world: TargetWorld, level: ServerLevel, origin: Vec3, team: String, range: Double, tie: Double): LivingEntity? {
        fun eligible(actor: ActorTarget): Boolean =
            actor.entity.isAlive &&
                !actor.entity.hasEffect(MobEffects.WIND_CHARGED) &&
                Targeting.hostile(team, actor.team) &&
                Targeting.distanceToBox(origin, actor.entity.boundingBox) <= range + tie

        val players = world.players(level).filter(::eligible)
        Targeting.nearest(players, { Targeting.distanceToBox(origin, it.entity.boundingBox) }, { it.entity.health }, { it.entity.id }, tie)
            ?.let { return it.entity }
        val summons = world.summons(level).filter(::eligible)
        return Targeting.nearest(summons, { Targeting.distanceToBox(origin, it.entity.boundingBox) }, { it.entity.health }, { it.entity.id }, tie)
            ?.entity
    }
}
