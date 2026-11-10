package shina.wallwarRebirth.monster.ai

import net.minecraft.server.level.ServerLevel
import shina.wallwarRebirth.monster.SummonedMiner

/** Mining and delivery stay here. Counter targets use [shina.wallwarRebirth.targeting.MinerTargeting]. */
object MinerZombieAi {
    fun tick(mob: SummonedMiner, level: ServerLevel) {
        mob.mind.tickMinerWork(level)
    }
}
