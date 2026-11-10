package shina.wallwarRebirth.targeting

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3

sealed interface CannonAim {
    val point: Vec3

    data class Creature(override val point: Vec3) : CannonAim
    data class Building(val pos: BlockPos, override val point: Vec3) : CannonAim
}

/** Enemy summon, then enemy player, then enemy building including the base. */
object CannonTargeting {
    fun select(world: TargetWorld, level: ServerLevel, origin: Vec3, team: String, self: BlockPos, range: Double, tie: Double): CannonAim? {
        val summons = world.summons(level).filter { actor ->
            actor.entity.isAlive &&
                Targeting.hostile(team, actor.team) &&
                Targeting.distanceToBox(origin, actor.entity.boundingBox) <= range + tie
        }
        Targeting.nearest(summons, { Targeting.distanceToBox(origin, it.entity.boundingBox) }, { it.entity.health }, { it.entity.id }, tie)
            ?.let { return CannonAim.Creature(Vec3.atCenterOf(it.entity.blockPosition())) }
        val players = world.players(level).filter { actor ->
            actor.entity.isAlive &&
                Targeting.hostile(team, actor.team) &&
                Targeting.distanceToBox(origin, actor.entity.boundingBox) <= range + tie
        }
        Targeting.nearest(players, { Targeting.distanceToBox(origin, it.entity.boundingBox) }, { it.entity.health }, { it.entity.id }, tie)
            ?.let { return CannonAim.Creature(Vec3.atCenterOf(it.entity.blockPosition())) }
        val buildings = world.cores(level).filter { core ->
            core.pos != self &&
                core.health > 0 &&
                core.buildingId.isNotEmpty() &&
                Targeting.hostile(team, core.team) &&
                Targeting.distanceToBox(origin, core.box) <= range + tie
        }
        val building = buildings.minWithOrNull(
            compareBy<CoreTarget> { (Targeting.distanceToBox(origin, it.box) / tie).toLong() }
                .thenBy { it.health }
                .thenBy { it.pos.x }
                .thenBy { it.pos.z }
                .thenBy { it.pos.y },
        ) ?: return null
        return CannonAim.Building(building.pos, Vec3.atCenterOf(building.pos))
    }
}
