package shina.wallwarRebirth.monster.ai

import net.minecraft.server.level.ServerLevel
import shina.wallwarRebirth.monster.SummonedGolem

object GolemAi {
    fun tick(mob: SummonedGolem, level: ServerLevel) {
        mob.mind.buildingSwing = { world -> world.broadcastEntityEvent(mob, 4.toByte()) }
        mob.mind.tickCombat(level)
    }
}
