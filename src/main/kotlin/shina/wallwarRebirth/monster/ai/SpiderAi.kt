package shina.wallwarRebirth.monster.ai

import net.minecraft.server.level.ServerLevel
import shina.wallwarRebirth.monster.SummonedSpider

/** [shina.wallwarRebirth.targeting.SpiderTargeting] skips defensive buildings such as walls. */
object SpiderAi {
    fun tick(mob: SummonedSpider, level: ServerLevel) {
        mob.mind.ignoreDefensiveBuildings = true
        mob.mind.tickCombat(level)
    }
}
