package shina.wallwarRebirth.monster.ai

import net.minecraft.server.level.ServerLevel
import shina.wallwarRebirth.monster.SummonedSpider

/** [shina.wallwarRebirth.targeting.SpiderTargeting] skips walls and still targets quake towers. */
object SpiderAi {
    fun tick(mob: SummonedSpider, level: ServerLevel) {
        mob.mind.ignoreDefensiveBuildings = true
        mob.mind.tickCombat(level)
    }
}
