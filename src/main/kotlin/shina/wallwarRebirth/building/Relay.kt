package shina.wallwarRebirth.building

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import shina.wallwarRebirth.match.Match
import shina.wallwarRebirth.match.MatchRules
import shina.wallwarRebirth.monster.Monsters
import shina.wallwarRebirth.monster.SummonedUnit
import shina.wallwarRebirth.monster.SummonedUnits

object Relay {
    fun teamBase(level: ServerLevel, team: String): SiteCoreBlockEntity? {
        if (team.isEmpty()) return null
        return SiteCores.all().firstOrNull {
            it.level === level &&
                !it.isRemoved &&
                it.health > 0 &&
                it.buildingId == Buildings.BASE.id &&
                it.team == team
        }
    }

    fun summon(site: SiteCoreBlockEntity, level: ServerLevel): Boolean {
        if (!Match.isFight()) return false
        val origin = Vec3.atCenterOf(site.blockPos)
        val miners = SummonedUnits.all().filter { unit ->
            val mob = unit.living()
            mob.level() === level &&
                unit.summonedTeam.isNotEmpty() &&
                unit.summonedTeam == site.team &&
                Monsters.byId(unit.kindId)?.miner == true &&
                !unit.mind.called()
        }
        val chosen = miners.minWithOrNull(
            compareBy<SummonedUnit> { bucket(origin.distanceTo(it.living().position())) }
                .thenBy { it.living().health }
                .thenBy { it.living().id },
        ) ?: return false
        chosen.mind.acceptCall(site.blockPos)
        return true
    }

    fun bucket(distance: Double): Long = (distance / MatchRules.CANNON_TIE_DISTANCE).toLong()
}
