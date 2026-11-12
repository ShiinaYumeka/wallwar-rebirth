package shina.wallwarRebirth.monster.ai

import net.minecraft.server.level.ServerLevel
import shina.wallwarRebirth.monster.SummonedVex

object VexAi {
    fun tick(unit: SummonedVex, level: ServerLevel) {
        unit.mind.tickCombat(level)
    }
}
