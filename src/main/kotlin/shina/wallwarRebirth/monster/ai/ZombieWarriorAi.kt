package shina.wallwarRebirth.monster.ai

import net.minecraft.server.level.ServerLevel
import shina.wallwarRebirth.monster.SummonedWarrior

object ZombieWarriorAi {
    fun tick(mob: SummonedWarrior, level: ServerLevel) {
        mob.mind.tickCombat(level)
    }
}
