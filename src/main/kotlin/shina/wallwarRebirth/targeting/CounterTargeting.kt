package shina.wallwarRebirth.targeting

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

/** Prefer the attacker when it is inside the radius. Otherwise summons outrank players. Cores are not counter targets. */
object CounterTargeting {
    fun select(
        world: TargetWorld,
        level: ServerLevel,
        origin: Vec3,
        team: String,
        radius: Double,
        self: LivingEntity,
        source: LivingEntity?,
    ): LivingEntity? {
        val enemies = ArrayList<ActorTarget>()
        enemies.addAll(world.players(level).filter { inReach(origin, team, radius, it) })
        enemies.addAll(world.summons(level).filter { it.entity !== self && inReach(origin, team, radius, it) })
        if (enemies.isEmpty()) return null
        if (source != null && enemies.any { it.entity === source }) return source
        return enemies.minWithOrNull(
            compareBy<ActorTarget> { if (it.entity is ServerPlayer) 1 else 0 }
                .thenBy { origin.distanceTo(it.entity.position()) }
                .thenBy { it.entity.id },
        )?.entity
    }

    private fun inReach(origin: Vec3, team: String, radius: Double, actor: ActorTarget): Boolean =
        actor.entity.isAlive &&
            Targeting.hostile(team, actor.team) &&
            origin.distanceTo(actor.entity.position()) <= radius
}
