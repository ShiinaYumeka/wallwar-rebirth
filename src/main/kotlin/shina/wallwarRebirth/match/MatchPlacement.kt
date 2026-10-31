package shina.wallwarRebirth.match

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import shina.wallwarRebirth.building.BuildingDefinition
import shina.wallwarRebirth.building.BuildingFootprints
import shina.wallwarRebirth.monster.TestTeams
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
        if (!Match.isFight()) return "现在不能放置"
        if (team != TestTeams.RED && team != TestTeams.BLUE) return "没有队伍"
        val cells = BuildingFootprints.cells(definition, origin, facing)
        val bases = Match.bases()
        if (bases.isEmpty()) return "基地还没放好"
        for (cell in cells) {
            if (bases.any { chebyshev(cell, it.second) <= MatchRules.BASE_FORBID }) return "离基地太近"
        }
        val own = bases.firstOrNull { it.first == team }?.second ?: return "没有队伍"
        val enemy = bases.firstOrNull { it.first != team }?.second ?: return "没有队伍"
        if (Match.horizontal(origin, own) >= Match.horizontal(origin, enemy)) return "只能放在离己方基地更近的一侧"
        return null
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
