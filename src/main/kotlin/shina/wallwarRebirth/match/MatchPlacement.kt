package shina.wallwarRebirth.match
import shina.wallwarRebirth.rules.MatchRules

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import shina.wallwarRebirth.building.BuildingDefinition
import shina.wallwarRebirth.building.BuildingFootprints
import shina.wallwarRebirth.building.Buildings
import shina.wallwarRebirth.building.SiteCores
import shina.wallwarRebirth.contract.WallwarTeams
import kotlin.math.abs
import kotlin.math.max

object MatchPlacement {
    fun rejection(
        level: ServerLevel,
        origin: BlockPos,
        definition: BuildingDefinition,
        facing: Direction,
        team: String,
    ): String? {
        if (!Match.isArena(level)) return null
        if (Match.phase != Match.Phase.LOBBY && !Match.isFight()) return "现在不能放置"
        if (team != WallwarTeams.RED && team != WallwarTeams.BLUE) return "没有队伍"
        val cells = BuildingFootprints.cells(definition, origin, facing)
        val bases = Match.bases()
        if (bases.isEmpty()) return "基地还没放好"
        for (cell in cells) {
            if (bases.any { chebyshev(cell, it.second) <= MatchRules.BASE_FORBID }) return "离基地太近"
        }
        if (bases.none { it.first == team }) return "没有队伍"
        if (!hasFriendlyNearby(level, origin, team)) return "附近 16 格内需要有己方建筑才能放置"
        return null
    }

    fun hasFriendlyNearby(level: ServerLevel, origin: BlockPos, team: String): Boolean {
        val range = MatchRules.BUILDING_PLACE_NEAR
        for (core in SiteCores.all()) {
            if (core.level !== level || core.isRemoved || core.buildingId.isEmpty()) continue
            if (core.health <= 0 || core.team != team) continue
            if (within(origin, core.blockPos, range)) return true
            for (part in core.partPositions) {
                if (within(origin, part, range)) return true
            }
            val definition = Buildings.byId(core.buildingId) ?: continue
            for (cell in BuildingFootprints.cells(definition, core.blockPos, core.facingDirection())) {
                if (within(origin, cell, range)) return true
            }
        }
        return false
    }

    private fun within(origin: BlockPos, other: BlockPos, range: Double): Boolean {
        val dx = (origin.x + 0.5) - (other.x + 0.5)
        val dy = (origin.y + 0.5) - (other.y + 0.5)
        val dz = (origin.z + 0.5) - (other.z + 0.5)
        return dx * dx + dy * dy + dz * dz <= range * range
    }

    fun ownHalf(level: ServerLevel, pos: BlockPos, team: String): Boolean {
        if (!Match.isArena(level)) return true
        val bases = Match.bases()
        val own = bases.firstOrNull { it.first == team }?.second ?: return false
        val enemy = bases.firstOrNull { it.first != team }?.second ?: return false
        return Match.horizontal(pos, own) < Match.horizontal(pos, enemy)
    }

    private fun chebyshev(a: BlockPos, b: BlockPos): Int = max(abs(a.x - b.x), abs(a.z - b.z))
}
