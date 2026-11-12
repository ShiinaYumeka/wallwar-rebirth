package shina.wallwarRebirth.monster.ai

import net.minecraft.server.level.ServerLevel
import shina.wallwarRebirth.monster.SummonedRavager

object RavagerAi {
    fun tick(unit: SummonedRavager, level: ServerLevel) {
        unit.mind.tickCombat(level)
    }
}
