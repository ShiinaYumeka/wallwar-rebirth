package shina.wallwarRebirth.targeting

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

/** Miners do not seek. After they are hit they use [CounterTargeting]. */
object MinerTargeting {
    fun counter(
        world: TargetWorld,
        level: ServerLevel,
        origin: Vec3,
        team: String,
        radius: Double,
        self: LivingEntity,
        source: LivingEntity?,
    ): LivingEntity? = CounterTargeting.select(world, level, origin, team, radius, self, source)
}
