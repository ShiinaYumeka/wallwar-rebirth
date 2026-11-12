package shina.wallwarRebirth.monster.ai

import net.minecraft.server.level.ServerLevel
import shina.wallwarRebirth.monster.SummonedEvoker

object EvokerAi {
    fun tick(unit: SummonedEvoker, level: ServerLevel) {
        unit.mind.tickCombat(level)
    }
}
