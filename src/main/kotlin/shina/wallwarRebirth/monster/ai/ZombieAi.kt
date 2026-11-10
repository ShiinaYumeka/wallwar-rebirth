package shina.wallwarRebirth.monster.ai

import net.minecraft.server.level.ServerLevel
import shina.wallwarRebirth.monster.SummonedZombie

/** Melee siege and chase. Targeting is [SummonDefaultTargeting]. */
object ZombieAi {
    fun tick(mob: SummonedZombie, level: ServerLevel) {
        mob.mind.tickCombat(level)
    }
}
