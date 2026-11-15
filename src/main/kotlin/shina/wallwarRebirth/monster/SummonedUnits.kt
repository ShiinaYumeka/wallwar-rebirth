package shina.wallwarRebirth.monster

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import shina.wallwarRebirth.match.Match
import shina.wallwarRebirth.rules.MatchRules

object SummonedUnits {
    private val units = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<SummonedUnit, Boolean>())

    fun add(unit: SummonedUnit) {
        units.add(unit)
    }

    fun remove(unit: SummonedUnit) {
        units.remove(unit)
    }

    fun all(): List<SummonedUnit> {
        units.removeIf { unit ->
            val entity = unit as Entity
            entity.isRemoved || !entity.isAlive
        }
        return ArrayList(units)
    }

    fun rebindHomes(level: ServerLevel, from: BlockPos, to: BlockPos) {
        for (unit in all()) {
            if (unit.living().level() !== level) continue
            unit.mind.rebindHome(from, to)
        }
    }

    fun reassertHome(site: shina.wallwarRebirth.building.SiteCoreBlockEntity) {
        val world = site.level as? ServerLevel ?: return
        for (unit in all()) {
            if (unit.living().level() !== world) continue
            unit.mind.reassertIfHome(site.blockPos)
        }
    }

    fun recallHold(site: shina.wallwarRebirth.building.SiteCoreBlockEntity) {
        val world = site.level as? ServerLevel ?: return
        for (unit in all()) {
            if (unit.living().level() !== world) continue
            unit.mind.recallHold(site)
        }
    }

    fun commandNearby(player: ServerPlayer, follow: Boolean): Int {
        val team = Match.member(player.uuid)?.team ?: return 0
        if (team.isEmpty()) return 0
        val origin = player.position()
        var count = 0
        for (unit in all()) {
            val living = unit.living()
            if (living.level() !== player.level()) continue
            if (unit.summonedTeam != team) continue
            if (origin.distanceTo(living.position()) > MatchRules.COMMAND_RANGE) continue
            unit.mind.applyCommand(player, follow)
            count++
        }
        return count
    }

    fun commandFacing(player: ServerPlayer, follow: Boolean): Int {
        val team = Match.member(player.uuid)?.team ?: return 0
        if (team.isEmpty()) return 0
        val unit = facing(player, team) ?: return 0
        unit.mind.applyCommand(player, follow)
        return 1
    }

    private fun facing(player: ServerPlayer, team: String): SummonedUnit? {
        val eye = player.eyePosition
        val end = eye.add(player.lookAngle.scale(MatchRules.COMMAND_RANGE))
        var best: SummonedUnit? = null
        var bestDist = Double.POSITIVE_INFINITY
        for (unit in all()) {
            val living = unit.living()
            if (!living.isAlive || living.level() !== player.level() || unit.summonedTeam != team) continue
            val hit = living.boundingBox.inflate(0.3).clip(eye, end)
            if (hit.isEmpty) continue
            val dist = eye.distanceToSqr(hit.get())
            if (dist < bestDist) {
                bestDist = dist
                best = unit
            }
        }
        return best
    }
}
