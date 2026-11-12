package shina.wallwarRebirth.world
import shina.wallwarRebirth.match.Match
import shina.wallwarRebirth.rules.MatchRules

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.TicketType
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.DoublePlantBlock
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf
import net.minecraft.world.level.block.LeavesBlock
import net.minecraft.world.level.block.RotatedPillarBlock
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator
import net.minecraft.world.level.levelgen.RandomState
import net.minecraft.world.level.material.Fluids
import net.minecraft.tags.BlockTags
import shina.wallwarRebirth.building.CoreForm
import shina.wallwarRebirth.building.ModSites
import shina.wallwarRebirth.building.SiteCoreBlock
import shina.wallwarRebirth.building.SiteCoreBlockEntity
import shina.wallwarRebirth.mixin.ChunkMapAccess
import shina.wallwarRebirth.mixin.ServerChunkCacheAccess
import shina.wallwarRebirth.contract.WallwarTeams
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

object MatchWorld {
    private const val TICKET_RADIUS = 0
    private var loading: List<ChunkPos> = emptyList()

    data class Built(
        val redBase: BlockPos,
        val blueBase: BlockPos,
        val redSpawn: Match.Spawn,
        val blueSpawn: Match.Spawn,
    )

    fun prime(server: MinecraftServer): Boolean {
        val level = server.getLevel(MatchRules.DIMENSION) ?: return false
        val positions = chunkPositions()
        val source = level.chunkSource
        for (pos in positions) source.addTicketWithRadius(TicketType.FORCED, pos, TICKET_RADIUS)
        (source as ServerChunkCacheAccess).wallwarRunDistanceManagerUpdates()
        loading = positions
        return true
    }

    fun loaded(server: MinecraftServer): Boolean {
        if (loading.isEmpty()) return false
        val level = server.getLevel(MatchRules.DIMENSION) ?: return false
        val source = level.chunkSource
        (source as ServerChunkCacheAccess).wallwarRunDistanceManagerUpdates()
        return loading.all { source.getChunkNow(it.x, it.z) != null }
    }

    fun release(server: MinecraftServer) {
        val level = server.getLevel(MatchRules.DIMENSION) ?: return
        val positions = loading.ifEmpty { chunkPositions() }
        val source = level.chunkSource
        for (pos in positions) source.removeTicketWithRadius(TicketType.FORCED, pos, TICKET_RADIUS)
        (source as ServerChunkCacheAccess).wallwarRunDistanceManagerUpdates()
        loading = emptyList()
    }

    fun reseed(server: MinecraftServer, seed: Long) {
        val level = server.getLevel(MatchRules.DIMENSION) ?: return
        val generator = level.chunkSource.generator as? NoiseBasedChunkGenerator ?: return
        val noises = level.registryAccess().lookupOrThrow(Registries.NOISE)
        val state = RandomState.create(generator.generatorSettings().value(), noises, seed)
        (level.chunkSource.chunkMap as ChunkMapAccess).wallwarSetRandomState(state)
    }

    fun build(server: MinecraftServer): Built? {
        if (!loaded(server)) return null
        val level = server.getLevel(MatchRules.DIMENSION) ?: return null
        val size = Match.mapSize
        val min = -size / 2
        val max = min + size - 1
        ease(level, min, max)
        val border = level.worldBorder
        border.setCenter(min + (max - min) / 2.0, min + (max - min) / 2.0)
        border.size = size.toDouble()
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
        placeBase(level, redCore, WallwarTeams.RED, redFacing)
        placeBase(level, blueCore, WallwarTeams.BLUE, blueFacing)
        placeRearTree(level, redCore, blueCore)
        placeRearTree(level, blueCore, redCore)
        SurfaceOres.scatter(level, redCore, blueCore)
        shortenGrass(level, min, max)
        level.dayTime = MatchRules.DAY_TIME
        level.setWeatherParameters(6000, 0, false, false)
        return Built(
            redCore,
            blueCore,
            spawn(level, redCore, blueCore),
            spawn(level, blueCore, redCore),
        )
    }

    private fun chunkPositions(): List<ChunkPos> {
        val size = Match.mapSize
        val min = -size / 2
        val max = min + size - 1
        val chunkMin = Math.floorDiv(min, 16)
        val chunkMax = Math.floorDiv(max, 16)
        val positions = ArrayList<ChunkPos>((chunkMax - chunkMin + 1) * (chunkMax - chunkMin + 1))
        for (cx in chunkMin..chunkMax) {
            for (cz in chunkMin..chunkMax) positions.add(ChunkPos(cx, cz))
        }
        return positions
    }

    private fun shortenGrass(level: ServerLevel, min: Int, max: Int) {
        val minY = level.minY
        val maxY = minY + level.height - 1
        for (x in min..max) {
            for (z in min..max) {
                val surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z)
                val top = (surface + 8).coerceAtMost(maxY)
                val bottom = (surface - 1).coerceAtLeast(minY)
                for (y in top downTo bottom) {
                    val pos = BlockPos(x, y, z)
                    val state = level.getBlockState(pos)
                    if (!state.`is`(Blocks.TALL_GRASS)) continue
                    if (state.hasProperty(DoublePlantBlock.HALF) &&
                        state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)
                    } else {
                        level.setBlock(pos, Blocks.SHORT_GRASS.defaultBlockState(), Block.UPDATE_ALL)
                    }
                }
            }
        }
    }

    private fun ease(level: ServerLevel, min: Int, max: Int) {
        val span = max - min + 1
        val height = IntArray(span * span)
        fun index(x: Int, z: Int) = (x - min) * span + (z - min)
        for (x in min..max) {
            for (z in min..max) height[index(x, z)] = naturalGround(level, x, z)
        }
        var changed = true
        while (changed) {
            changed = false
            for (x in min..max) {
                for (z in min..max) {
                    val at = index(x, z)
                    var cap = height[at]
                    if (x > min) cap = min(cap, height[index(x - 1, z)] + 1)
                    if (x < max) cap = min(cap, height[index(x + 1, z)] + 1)
                    if (z > min) cap = min(cap, height[index(x, z - 1)] + 1)
                    if (z < max) cap = min(cap, height[index(x, z + 1)] + 1)
                    if (cap < height[at]) {
                        height[at] = cap
                        changed = true
                    }
                }
            }
        }
        for (x in min..max) {
            for (z in min..max) cutColumn(level, x, z, height[index(x, z)])
        }
    }

    private fun cutColumn(level: ServerLevel, x: Int, z: Int, surface: Int) {
        val old = naturalGround(level, x, z)
        if (surface >= old) return
        val top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)
        for (y in surface + 1..top) {
            level.setBlock(BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_NONE)
        }
        val minY = level.dimensionType().minY()
        fun place(y: Int, block: Block) {
            if (y < minY) return
            level.setBlock(BlockPos(x, y, z), block.defaultBlockState(), Block.UPDATE_NONE)
        }
        place(surface, Blocks.GRASS_BLOCK)
        place(surface - 1, Blocks.DIRT)
        for (dy in 2..4) place(surface - dy, Blocks.STONE)
        for (dy in 5..8) place(surface - dy, Blocks.BEDROCK)
        var y = surface - 9
        while (y >= minY) {
            val pos = BlockPos(x, y, z)
            if (level.getBlockState(pos).isAir) break
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_NONE)
            y--
        }
    }

    private fun seal(level: ServerLevel, x: Int, z: Int) {
        val surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1
        for (y in level.dimensionType().minY() until surface) {
            val pos = BlockPos(x, y, z)
            val state = level.getBlockState(pos)
            if (state.`is`(Blocks.BEDROCK) || state.isAir) continue
            if (!buried(state)) continue
            val fill = if (y < 0) Blocks.DEEPSLATE else Blocks.STONE
            level.setBlock(pos, fill.defaultBlockState(), Block.UPDATE_NONE)
        }
    }

    private fun buried(state: net.minecraft.world.level.block.state.BlockState): Boolean {
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
                    putLeaf(level, BlockPos(x + dx, trunk + dy, z + dz), leaves)
                }
            }
        }
        for (dx in -1..1) {
            for (dz in -1..1) putLeaf(level, BlockPos(x + dx, trunk + 4, z + dz), leaves)
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
