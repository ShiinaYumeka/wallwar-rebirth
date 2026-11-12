package shina.wallwarRebirth.monster.ai

import net.minecraft.server.level.ServerLevel
import shina.wallwarRebirth.monster.SummonedPillager

object PillagerAi {
    fun tick(unit: SummonedPillager, level: ServerLevel) {
        unit.mind.tickCombat(level)
    }
}
