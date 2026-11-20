package shina.wallwarRebirth.targeting

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3

/** Default chase, but walls are not cores. Quake towers stay valid targets. */
object SpiderTargeting {
    fun chooseCore(
        world: TargetWorld,
        level: ServerLevel,
        origin: Vec3,
        team: String,
        radius: Double?,
        ignore: Collection<BlockPos> = emptyList(),
    ): CoreTarget? = SummonDefaultTargeting.chooseCore(world, level, origin, team, radius, skipDefensive = true, ignore = ignore)

}
