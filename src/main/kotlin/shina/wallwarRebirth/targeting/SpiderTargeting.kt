package shina.wallwarRebirth.targeting

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

/** Default chase, but defensive buildings such as walls are not cores. */
object SpiderTargeting {
    fun chooseCore(
        world: TargetWorld,
        level: ServerLevel,
        origin: Vec3,
        team: String,
        radius: Double?,
    ): CoreTarget? = SummonDefaultTargeting.chooseCore(world, level, origin, team, radius, skipDefensive = true)

    fun nearestSummon(
        world: TargetWorld,
        level: ServerLevel,
        origin: Vec3,
        team: String,
        radius: Double,
        self: LivingEntity,
    ): LivingEntity? = SummonDefaultTargeting.nearestSummon(world, level, origin, team, radius, self)

    fun nearestPlayer(
        world: TargetWorld,
        level: ServerLevel,
        origin: Vec3,
        team: String,
        radius: Double,
    ): LivingEntity? = SummonDefaultTargeting.nearestPlayer(world, level, origin, team, radius)
}
