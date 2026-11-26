package shina.wallwarRebirth.world

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.Heightmap
import kotlin.math.abs

/** Ground-level walking distances over the generated arena. One-block steps are allowed. */
internal class ArenaRoutes(
    private val level: ServerLevel,
    val min: Int,
    val max: Int,
) {
    private val span = max - min + 1
    val cells = 0 until span * span
    private val height = IntArray(cells.last + 1)
    private val open = BooleanArray(height.size)
    private val clearable = BooleanArray(height.size)
    private val soil = BooleanArray(height.size)

    init {
        for (cell in cells) refresh(cell)
    }

    fun cell(x: Int, z: Int): Int = (x - min) * span + z - min
    fun x(cell: Int): Int = min + cell / span
    fun z(cell: Int): Int = min + cell % span
    fun ground(cell: Int): BlockPos = BlockPos(x(cell), height[cell], z(cell))
    fun mineable(cell: Int): Boolean = soil[cell] && open[cell]
    fun inside(x: Int, z: Int): Boolean = x in min..max && z in min..max

    // Reflection across the line separating the two bases leaves both corner reactors on the line.
    fun opposite(cell: Int): Int = cell(min + max - z(cell), min + max - x(cell))

    fun neighbors(cell: Int, visit: (Int) -> Unit) {
        if (cell >= span) visit(cell - span)
        if (cell < height.size - span) visit(cell + span)
        if (cell % span > 0) visit(cell - 1)
        if (cell % span < span - 1) visit(cell + 1)
    }

    fun refresh(cell: Int) {
        val x = x(cell)
        val z = z(cell)
        var y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1
        val probe = BlockPos.MutableBlockPos(x, y, z)
        var state = level.getBlockState(probe)
        while (y > level.minY && (state.`is`(BlockTags.LOGS) || state.`is`(BlockTags.LEAVES))) {
            probe.setY(--y)
            state = level.getBlockState(probe)
        }
        height[cell] = y
        val floor = !state.isAir && state.fluidState.isEmpty && !state.getCollisionShape(level, probe).isEmpty
        soil[cell] = state.`is`(Blocks.GRASS_BLOCK) || state.`is`(Blocks.DIRT)
        val feet = probe.above()
        val head = feet.above()
        open[cell] = floor && empty(feet) && empty(head)
        clearable[cell] = floor && (empty(feet) || vegetation(feet)) && (empty(head) || vegetation(head))
    }

    private fun empty(pos: BlockPos): Boolean {
        val state = level.getBlockState(pos)
        return state.fluidState.isEmpty && state.getCollisionShape(level, pos).isEmpty
    }

    private fun vegetation(pos: BlockPos): Boolean {
        val state = level.getBlockState(pos)
        return state.`is`(BlockTags.LOGS) || state.`is`(BlockTags.LEAVES)
    }

    fun clearWalkingRoute(route: List<Int>) {
        for (cell in route) {
            if (open[cell]) continue
            val floor = ground(cell)
            for (offset in 1..2) {
                val pos = floor.above(offset)
                if (vegetation(pos)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_NONE)
            }
            refresh(cell)
        }
    }

    class Paths(val distances: IntArray, private val previous: IntArray) {
        fun routeTo(cell: Int): List<Int> {
            if (distances[cell] < 0) return emptyList()
            val route = ArrayList<Int>(distances[cell] + 1)
            var at = cell
            while (at >= 0) {
                route.add(at)
                at = previous[at]
            }
            return route
        }
    }

    fun pathsFrom(start: Int, paired: Boolean = false, protected: Set<Int> = emptySet()): Paths {
        val distance = IntArray(height.size) { -1 }
        val previous = IntArray(height.size) { -1 }
        if (!canWalk(start, paired, protected)) return Paths(distance, previous)
        val queue = IntArray(height.size)
        var front = 0
        var end = 0
        queue[end++] = start
        distance[start] = 0
        while (front < end) {
            val cell = queue[front++]
            neighbors(cell) { next ->
                if (distance[next] >= 0 || !canWalk(next, paired, protected)) return@neighbors
                if (abs(height[cell] - height[next]) > 1) return@neighbors
                if (paired && abs(height[opposite(cell)] - height[opposite(next)]) > 1) return@neighbors
                distance[next] = distance[cell] + 1
                previous[next] = cell
                queue[end++] = next
            }
        }
        return Paths(distance, previous)
    }

    private fun canWalk(cell: Int, paired: Boolean, protected: Set<Int>): Boolean {
        if (!paired) return open[cell]
        val other = opposite(cell)
        return (open[cell] || (clearable[cell] && cell !in protected)) &&
            (open[other] || (clearable[other] && other !in protected))
    }
}
