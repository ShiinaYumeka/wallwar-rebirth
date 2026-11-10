package shina.wallwarRebirth.targeting

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3

/** Nearest enemy player in range. Summons and buildings are ignored. Straight-line distance, no line of sight. */
object TeslaTargeting {
    fun select(world: TargetWorld, level: ServerLevel, origin: Vec3, team: String, range: Double, tie: Double): ServerPlayer? {
        val players = world.players(level).mapNotNull { actor ->
            val player = actor.entity as? ServerPlayer ?: return@mapNotNull null
            if (!player.isAlive) return@mapNotNull null
            if (!Targeting.hostile(team, actor.team)) return@mapNotNull null
            if (Targeting.distanceToBox(origin, player.boundingBox) > range + tie) return@mapNotNull null
            actor
        }
        return Targeting.nearest(players, { Targeting.distanceToBox(origin, it.entity.boundingBox) }, { it.entity.health }, { it.entity.id }, tie)
            ?.entity as? ServerPlayer
    }
}
