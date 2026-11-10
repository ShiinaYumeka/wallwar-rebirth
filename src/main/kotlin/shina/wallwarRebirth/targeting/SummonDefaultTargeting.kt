package shina.wallwarRebirth.targeting

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

/**
 * Current chase radius core, then a summon in that radius, then a player, then the nearest core on the map.
 * Feet-to-feet for creatures, feet-to-core-center for buildings. Ties keep the previous ordering.
 */
object SummonDefaultTargeting {
    fun chooseCore(
        world: TargetWorld,
        level: ServerLevel,
        origin: Vec3,
        team: String,
        radius: Double?,
        skipDefensive: Boolean,
    ): CoreTarget? {
        val choices = world.cores(level).filter { core ->
            core.health > 0 &&
                core.buildingId.isNotEmpty() &&
                !(skipDefensive && core.defensive) &&
                Targeting.hostile(team, core.team) &&
                (radius == null || origin.distanceTo(Vec3.atCenterOf(core.pos)) <= radius)
        }
        return choices.minWithOrNull(
            compareBy<CoreTarget> { (origin.distanceTo(Vec3.atCenterOf(it.pos)) * 1000.0).toLong() }
                .thenBy { it.health }
                .thenBy { if (it.base) 0 else 1 }
                .thenBy { it.pos.x }
                .thenBy { it.pos.z }
                .thenBy { it.pos.y },
        )
    }

    fun nearestSummon(
        world: TargetWorld,
        level: ServerLevel,
        origin: Vec3,
        team: String,
        radius: Double,
        self: LivingEntity,
    ): LivingEntity? {
        val found = world.summons(level).filter { actor ->
            actor.entity !== self &&
                actor.entity.isAlive &&
                Targeting.hostile(team, actor.team) &&
                origin.distanceTo(actor.entity.position()) <= radius
        }
        return found.minWithOrNull(
            compareBy<ActorTarget> { origin.distanceTo(it.entity.position()) }.thenBy { it.entity.id },
        )?.entity
    }

    fun nearestPlayer(
        world: TargetWorld,
        level: ServerLevel,
        origin: Vec3,
        team: String,
        radius: Double,
    ): LivingEntity? {
        val found = world.players(level).filter { actor ->
            actor.entity.isAlive &&
                Targeting.hostile(team, actor.team) &&
                origin.distanceTo(actor.entity.position()) <= radius
        }
        return found.minWithOrNull(
            compareBy<ActorTarget> { origin.distanceTo(it.entity.position()) }.thenBy { it.entity.id },
        )?.entity
    }
}
