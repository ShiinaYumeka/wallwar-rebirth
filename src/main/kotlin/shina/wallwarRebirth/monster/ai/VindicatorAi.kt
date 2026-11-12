package shina.wallwarRebirth.monster.ai

import net.minecraft.server.level.ServerLevel
import shina.wallwarRebirth.monster.SummonedVindicator

object VindicatorAi {
    fun tick(unit: SummonedVindicator, level: ServerLevel) {
        unit.mind.tickCombat(level)
    }
}
