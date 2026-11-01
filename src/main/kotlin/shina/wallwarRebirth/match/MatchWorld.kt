package shina.wallwarRebirth.match

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.LeavesBlock
import net.minecraft.world.level.block.RotatedPillarBlock
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.material.Fluids
import net.minecraft.tags.BlockTags
import shina.wallwarRebirth.building.CoreForm
import shina.wallwarRebirth.building.ModSites
import shina.wallwarRebirth.building.SiteCoreBlock
import shina.wallwarRebirth.building.SiteCoreBlockEntity
import shina.wallwarRebirth.monster.TestTeams
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

object MatchWorld {
    data class Built(
        val redBase: BlockPos,
        val blueBase: BlockPos,
        val redSpawn: Match.Spawn,
        val blueSpawn: Match.Spawn,
    )

    fun build(server: MinecraftServer): Built? {
        val level = server.getLevel(MatchRules.DIMENSION) ?: return null
        val size = Match.mapSize
        val min = -size / 2
        val max = min + size - 1
        val border = level.worldBorder
        border.setCenter(min + (max - min) / 2.0, min + (max - min) / 2.0)
        border.setSize(size.toDouble())
        val chunkMin = Math.floorDiv(min, 16)
        val chunkMax = Math.floorDiv(max, 16)
        for (cx in chunkMin..chunkMax) {
            for (cz in chunkMin..chunkMax) level.getChunk(cx, cz)
        }
        for (x in min..max) {
            for (z in min..max) seal(level, x, z)
        }
        wall(level, min, max)
        val innerMin = min + 1
        val innerMax = max - 1
        val red = BlockPos(innerMin + MatchRules.BASE_INSET, 0, innerMin + MatchRules.BASE_INSET)
        val blue = BlockPos(innerMax - MatchRules.BASE_INSET, 0, innerMax - MatchRules.BASE_INSET)
        val redCore = platform(level, red.x, red.z)
        val blueCore = platform(level, blue.x, blue.z)
        val redFacing = facing(redCore, blueCore)
        val blueFacing = facing(blueCore, redCore)
        placeBase(level, redCore, TestTeams.RED, redFacing)
        placeBase(level, blueCore, TestTeams.BLUE, blueFacing)
        placeRearTree(level, redCore, blueCore)
        placeRearTree(level, blueCore, redCore)
        SurfaceOres.scatter(level, redCore, blueCore)
        level.dayTime = MatchRules.DAY_TIME
        level.setWeatherParameters(6000, 0, false, false)
        return Built(
            redCore,
            blueCore,
            spawn(level, redCore, blueCore),
            spawn(level, blueCore, redCore),
        )
    }

    private fun seal(level: ServerLevel, x: Int, z: Int) {
        val surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1
        for (y in level.dimensionType().minY() until surface) {
            val pos = BlockPos(x, y, z)
            val state = level.getBlockState(pos)
            if (state.`is`(Blocks.BEDROCK)) continue
            if (!buried(state)) continue
            val fill = if (y < 0) Blocks.DEEPSLATE else Blocks.STONE
            level.setBlock(pos, fill.defaultBlockState(), Block.UPDATE_NONE)
        }
    }

    private fun buried(state: net.minecraft.world.level.block.state.BlockState): Boolean {
        if (state.isAir) return true
        if (state.`is`(Blocks.LAVA) || state.fluidState.`is`(Fluids.LAVA)) return true
        if (state.`is`(Blocks.SPAWNER)) return true
        val path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.block).path
        return path.endsWith("_ore") || path == "ancient_debris" || path.contains("infested_")
    }

    private fun wall(level: ServerLevel, min: Int, max: Int) {
        val barrier = Blocks.BARRIER.defaultBlockState()
        for (x in min..max) {
            for (y in level.dimensionType().minY()..MatchRules.BARRIER_TOP) {
                level.setBlock(BlockPos(x, y, min), barrier, Block.UPDATE_NONE)
                level.setBlock(BlockPos(x, y, max), barrier, Block.UPDATE_NONE)
            }
        }
        for (z in min + 1 until max) {
            for (y in level.dimensionType().minY()..MatchRules.BARRIER_TOP) {
                level.setBlock(BlockPos(min, y, z), barrier, Block.UPDATE_NONE)
                level.setBlock(BlockPos(max, y, z), barrier, Block.UPDATE_NONE)
            }
        }
    }

    private fun platform(level: ServerLevel, x: Int, z: Int): BlockPos {
        val ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1
        val radius = MatchRules.PLATFORM_RADIUS
        for (dx in -radius..radius) {
            for (dz in -radius..radius) {
                val columnX = x + dx
                val columnZ = z + dz
                flatten(level, columnX, columnZ, ground)
            }
        }
        return BlockPos(x, ground + 1, z)
    }

    private fun flatten(level: ServerLevel, x: Int, z: Int, ground: Int) {
        val top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1
        if (top > ground) {
            for (y in ground + 1..top) level.setBlock(BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_NONE)
        } else if (top < ground) {
            for (y in top + 1 until ground) {
                level.setBlock(BlockPos(x, y, z), Blocks.DIRT.defaultBlockState(), Block.UPDATE_NONE)
            }
        }
        level.setBlock(BlockPos(x, ground, z), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_NONE)
        for (y in ground + 1..ground + 12) {
            val pos = BlockPos(x, y, z)
            val state = level.getBlockState(pos)
            if (state.isAir) continue
            if (state.`is`(BlockTags.LOGS) || state.`is`(BlockTags.LEAVES) || plant(state)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_NONE)
            }
        }
    }

    private fun plant(state: net.minecraft.world.level.block.state.BlockState): Boolean =
        state.`is`(BlockTags.FLOWERS) ||
            state.`is`(BlockTags.SAPLINGS) ||
            state.`is`(Blocks.SHORT_GRASS) ||
            state.`is`(Blocks.TALL_GRASS) ||
            state.`is`(Blocks.FERN) ||
            state.`is`(Blocks.LARGE_FERN) ||
            state.`is`(Blocks.BUSH)

    private fun placeRearTree(level: ServerLevel, core: BlockPos, other: BlockPos) {
        val stepX = direction(core.x - other.x)
        val stepZ = direction(core.z - other.z)
        if (stepX == 0 && stepZ == 0) return
        val distance = MatchRules.PLATFORM_RADIUS + 1
        val x = core.x + stepX * distance
        val z = core.z + stepZ * distance
        val ground = naturalGround(level, x, z)
        for (y in ground + 1..ground + 8) {
            val pos = BlockPos(x, y, z)
            val state = level.getBlockState(pos)
            if (state.isAir) continue
            if (state.`is`(BlockTags.LOGS) || state.`is`(BlockTags.LEAVES) || plant(state)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_NONE)
            }
        }
        val log = Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y)
        val leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true)
        val trunk = ground + 1
        for (dy in 0..3) level.setBlock(BlockPos(x, trunk + dy, z), log, Block.UPDATE_NONE)
        for (dx in -2..2) {
            for (dz in -2..2) {
                if (abs(dx) == 2 && abs(dz) == 2) continue
                for (dy in 2..3) {
                    if (dx == 0 && dz == 0) continue
                    putLeaf(level, BlockPos(x + dx, trunk + dy, z), leaves)
                }
            }
        }
        for (dx in -1..1) {
            for (dz in -1..1) putLeaf(level, BlockPos(x + dx, trunk + 4, z), leaves)
        }
    }

    private fun direction(delta: Int): Int = when {
        delta > 0 -> 1
        delta < 0 -> -1
        else -> 0
    }

    private fun naturalGround(level: ServerLevel, x: Int, z: Int): Int {
        var y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1
        var state = level.getBlockState(BlockPos(x, y, z))
        while (y > level.dimensionType().minY() && (state.`is`(BlockTags.LOGS) || state.`is`(BlockTags.LEAVES) || plant(state))) {
            y--
            state = level.getBlockState(BlockPos(x, y, z))
        }
        return y
    }

    private fun putLeaf(level: ServerLevel, pos: BlockPos, leaves: net.minecraft.world.level.block.state.BlockState) {
        val state = level.getBlockState(pos)
        if (!state.isAir && !plant(state) && !state.`is`(BlockTags.LEAVES)) return
        level.setBlock(pos, leaves, Block.UPDATE_NONE)
    }

    private fun placeBase(level: ServerLevel, pos: BlockPos, team: String, facing: Direction) {
        val state = ModSites.CORE.defaultBlockState().setValue(SiteCoreBlock.FORM, CoreForm.BEACON)
        level.setBlock(pos, state, Block.UPDATE_ALL)
        val core = level.getBlockEntity(pos) as? SiteCoreBlockEntity ?: return
        core.installBase(team, facing)
    }

    private fun facing(from: BlockPos, to: BlockPos): Direction {
        val dx = to.x - from.x
        val dz = to.z - from.z
        return if (abs(dx) >= abs(dz)) {
            if (dx >= 0) Direction.EAST else Direction.WEST
        } else {
            if (dz >= 0) Direction.SOUTH else Direction.NORTH
        }
    }

    private fun spawn(level: ServerLevel, from: BlockPos, to: BlockPos): Match.Spawn {
        val dx = (to.x - from.x).toDouble()
        val dz = (to.z - from.z).toDouble()
        val len = hypot(dx, dz).coerceAtLeast(1.0)
        val dirX = dx / len
        val dirZ = dz / len
        var bestX = from.x
        var bestZ = from.z
        var bestError = Double.MAX_VALUE
        for (ox in -12..12) {
            for (oz in -12..12) {
                if (ox == 0 && oz == 0) continue
                val toward = ox * dirX + oz * dirZ
                if (toward <= 0.0) continue
                val error = abs(hypot(ox.toDouble(), oz.toDouble()) - MatchRules.SPAWN_DISTANCE)
                if (error < bestError) {
                    bestError = error
                    bestX = from.x + ox
                    bestZ = from.z + oz
                }
            }
        }
        val ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bestX, bestZ) - 1
        for (y in ground + 1..ground + 3) {
            val pos = BlockPos(bestX, y, bestZ)
            val state = level.getBlockState(pos)
            if (state.`is`(BlockTags.LOGS) || state.`is`(BlockTags.LEAVES) || plant(state)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_NONE)
            }
        }
        val yaw = Math.toDegrees(atan2(-(to.x - bestX).toDouble(), (to.z - bestZ).toDouble())).toFloat()
        return Match.Spawn(bestX + 0.5, (ground + 1).toDouble(), bestZ + 0.5, yaw)
    }
}
