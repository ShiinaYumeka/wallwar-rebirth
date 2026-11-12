package shina.wallwarRebirth.monster.ai

import net.minecraft.server.level.ServerLevel
import shina.wallwarRebirth.monster.SummonedIllusioner

object IllusionerAi {
    fun tick(unit: SummonedIllusioner, level: ServerLevel) {
        unit.mind.tickCombat(level)
    }
}
