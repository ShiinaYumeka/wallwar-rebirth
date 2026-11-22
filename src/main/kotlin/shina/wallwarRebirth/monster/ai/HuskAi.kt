package shina.wallwarRebirth.monster.ai

import net.minecraft.server.level.ServerLevel
import shina.wallwarRebirth.monster.SummonedUnit

object HuskAi {
    fun tick(mob: SummonedUnit, level: ServerLevel) {
        mob.mind.tickCombat(level)
    }
}
