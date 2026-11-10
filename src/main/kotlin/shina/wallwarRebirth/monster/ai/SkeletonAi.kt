package shina.wallwarRebirth.monster.ai

import net.minecraft.server.level.ServerLevel
import shina.wallwarRebirth.monster.SummonedSkeleton

/** Default chase. The bow shot stays on the skeleton entity. */
object SkeletonAi {
    fun tick(mob: SummonedSkeleton, level: ServerLevel) {
        mob.mind.tickCombat(level)
    }
}
