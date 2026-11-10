package shina.wallwarRebirth.monster.ai

import net.minecraft.server.level.ServerLevel
import shina.wallwarRebirth.monster.SummonedCreeper

object CreeperAi {
    fun tick(mob: SummonedCreeper, level: ServerLevel) {
        mob.mind.tickCombat(level)
        mob.mind.armCreeper()
    }
}
