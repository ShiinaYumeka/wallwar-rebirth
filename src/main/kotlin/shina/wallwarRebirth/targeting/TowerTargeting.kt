package shina.wallwarRebirth.targeting

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

/** Nearest enemy summon in range, otherwise the nearest enemy player. No buildings. */
object TowerTargeting {
    fun select(world: TargetWorld, level: ServerLevel, origin: Vec3, team: String, range: Double, tie: Double): LivingEntity? {
        val summons = world.summons(level).filter { actor ->
            actor.entity.isAlive &&
                Targeting.hostile(team, actor.team) &&
                Targeting.distanceToBox(origin, actor.entity.boundingBox) <= range + tie
        }
        Targeting.nearest(summons, { Targeting.distanceToBox(origin, it.entity.boundingBox) }, { it.entity.health }, { it.entity.id }, tie)
            ?.let { return it.entity }
        val players = world.players(level).filter { actor ->
            actor.entity.isAlive &&
                Targeting.hostile(team, actor.team) &&
                Targeting.distanceToBox(origin, actor.entity.boundingBox) <= range + tie
        }
        return Targeting.nearest(players, { Targeting.distanceToBox(origin, it.entity.boundingBox) }, { it.entity.health }, { it.entity.id }, tie)
            ?.entity
    }
}
