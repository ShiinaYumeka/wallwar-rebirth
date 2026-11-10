package shina.wallwarRebirth.monster.ai

import net.minecraft.server.level.ServerLevel
import shina.wallwarRebirth.monster.SummonedHusk

object HuskAi {
    fun tick(mob: SummonedHusk, level: ServerLevel) {
        mob.mind.tickCombat(level)
    }
}
