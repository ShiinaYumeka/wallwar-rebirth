package shina.wallwarRebirth.monster.ai

import net.minecraft.server.level.ServerLevel
import shina.wallwarRebirth.monster.SummonedCreeper

object CreeperAi {
    fun tick(mob: SummonedCreeper, level: ServerLevel) {
        if (mob.isDoomed()) {
            mob.mind.armCreeper()
            return
        }
        mob.mind.tickCombat(level)
        mob.mind.armCreeper()
    }
}
