package shina.wallwarRebirth.monster.ai

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.ai.util.DefaultRandomPos
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3
import shina.wallwarRebirth.building.FarmlandWork
import shina.wallwarRebirth.monster.MonsterRules
import shina.wallwarRebirth.monster.SummonedUnits
import shina.wallwarRebirth.monster.SummonedVillager
import shina.wallwarRebirth.rules.MatchRules

object VillagerAi {
    private const val WANDER_HORIZONTAL = 10
    private const val WANDER_VERTICAL = 7
    private const val WANDER_SPEED = 0.6
    private const val WORK_SPEED = 1.0

    fun tick(mob: SummonedVillager, level: ServerLevel) {
        if (!mob.isAlive) return
        SummonedUnits.add(mob)
        mob.target = null
        mob.forgetPanic()
        val destination = mob.mind.callDestination()
        if (destination != null) {
            tickCall(mob, level, destination)
            return
        }
        if (mob.isFarmer()) {
            tickFarmer(mob, level)
            return
        }
        wander(mob)
    }

    private fun tickCall(mob: SummonedVillager, level: ServerLevel, destination: BlockPos) {
        val site = FarmlandWork.live(level, destination, mob.summonedTeam)
        if (site == null) {
            mob.mind.finishCall()
            mob.navigation.stop()
            return
        }
        if (mob.mind.distanceToCenter(site.blockPos) <= MatchRules.FARMLAND_CALL_RANGE) {
            mob.navigation.stop()
            mob.mind.finishCall()
            mob.becomeFarmer()
            return
        }
        pathTo(mob, site.blockPos, WORK_SPEED)
    }

    private fun tickFarmer(mob: SummonedVillager, level: ServerLevel) {
        if (mob.workAge > 0) mob.workAge--
        val origin = mob.position()
        val target = FarmlandWork.nearestWheat(level, mob.summonedTeam, origin)
        if (target != null) {
            if (origin.distanceTo(Vec3.atCenterOf(target.pos)) <= MatchRules.FARMER_WORK_RANGE) {
                mob.navigation.stop()
                mob.lookControl.setLookAt(Vec3.atCenterOf(target.pos))
                if (mob.workAge > 0) return
                if (!FarmlandWork.harvest(level, target)) return
                mob.takeWheat()
                mob.swing(InteractionHand.MAIN_HAND)
                mob.workAge = MatchRules.FARMER_HARVEST_TICKS
                return
            }
            pathTo(mob, target.pos, WORK_SPEED)
            return
        }
        if (hasWheat(mob)) {
            tickDeposit(mob, level)
            return
        }
        wanderNearFarm(mob, level)
    }

    private fun tickDeposit(mob: SummonedVillager, level: ServerLevel) {
        val site = FarmlandWork.nearestFriendly(level, mob.summonedTeam, mob.position())
        if (site == null) {
            wander(mob)
            return
        }
        if (mob.mind.distanceToCenter(site.blockPos) <= MatchRules.FARMLAND_CALL_RANGE) {
            mob.navigation.stop()
            if (FarmlandWork.deposit(site, mob)) {
                mob.workAge = 0
            }
            return
        }
        pathTo(mob, site.blockPos, WORK_SPEED)
    }

    private fun wanderNearFarm(mob: SummonedVillager, level: ServerLevel) {
        val site = FarmlandWork.nearestFriendly(level, mob.summonedTeam, mob.position())
        if (site == null) {
            wander(mob)
            return
        }
        if (mob.mind.distanceToCenter(site.blockPos) > 8.0) {
            pathTo(mob, site.blockPos, WANDER_SPEED)
            return
        }
        wander(mob)
    }

    private fun wander(mob: SummonedVillager) {
        if (mob.navigation.isInProgress) return
        if (mob.tickCount % 20 != 0) return
        val dest = DefaultRandomPos.getPos(mob, WANDER_HORIZONTAL, WANDER_VERTICAL) ?: return
        mob.navigation.moveTo(dest.x, dest.y, dest.z, WANDER_SPEED)
    }

    private fun hasWheat(mob: SummonedVillager): Boolean {
        val inventory = mob.inventory
        for (slot in 0 until inventory.containerSize) {
            val stack = inventory.getItem(slot)
            if (!stack.isEmpty && stack.`is`(Items.WHEAT)) return true
        }
        return false
    }

    private fun pathTo(mob: SummonedVillager, pos: BlockPos, speed: Double) {
        if (mob.tickCount % MonsterRules.REPATH_TICKS != 0) return
        val center = Vec3.atCenterOf(pos)
        mob.navigation.moveTo(center.x, center.y, center.z, speed)
    }
}
