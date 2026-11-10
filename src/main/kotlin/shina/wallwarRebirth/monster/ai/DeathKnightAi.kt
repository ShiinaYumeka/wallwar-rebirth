package shina.wallwarRebirth.monster.ai

import net.minecraft.server.level.ServerLevel
import shina.wallwarRebirth.monster.SummonedDeathknight

object DeathKnightAi {
    fun tick(mob: SummonedDeathknight, level: ServerLevel) {
        mob.mind.tickCombat(level)
    }
}
