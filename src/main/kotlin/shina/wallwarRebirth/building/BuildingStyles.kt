package shina.wallwarRebirth.building

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Vec3i
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtUtils
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.entity.StructureBlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
import java.nio.file.Path
import kotlin.math.max
import kotlin.math.min

object BuildingStyles {
    private data class Piece(val x: Int, val y: Int, val z: Int, val state: BlockState)
    private data class Loaded(val pieces: List<Piece>, val axis: String)

    fun save(level: ServerLevel, structurePos: BlockPos, buildingId: String, rank: Int): String {
        val definition = Buildings.byId(buildingId) ?: return "没有这种建筑"
        if (rank !in 1..definition.maxLevel) return "等级要在 1 到 ${definition.maxLevel} 之间"
        val blockEntity = level.getBlockEntity(structurePos) as? StructureBlockEntity ?: return "没有看向结构方块"
        val corner = structurePos.offset(blockEntity.structurePos)
        val far = corner.offset(blockEntity.structureSize).offset(-1, -1, -1)
        val min = BlockPos(min(corner.x, far.x), min(corner.y, far.y), min(corner.z, far.z))
        val max = BlockPos(max(corner.x, far.x), max(corner.y, far.y), max(corner.z, far.z))
        val size = Vec3i(max.x - min.x + 1, max.y - min.y + 1, max.z - min.z + 1)
        if (size.x < 1 || size.y < 1 || size.z < 1) return "结构方块没有尺寸"
        val beacons = ArrayList<BlockPos>()
        for (x in min.x..max.x) {
            for (y in min.y..max.y) {
                for (z in min.z..max.z) {
                    val pos = BlockPos(x, y, z)
                    if (level.getBlockState(pos).`is`(Blocks.BEACON)) beacons.add(pos)
                }
            }
        }
        if (beacons.isEmpty()) return "区域里没有信标"
        if (beacons.size > 1) return "区域里有多个信标"
        val beacon = beacons[0]
        val offset = BlockPos(beacon.x - min.x, beacon.y - min.y, beacon.z - min.z)
        val template = StructureTemplate()
        template.fillFromWorld(level, min, size, false, listOf(Blocks.BEACON))
        val tag = CompoundTag()
        template.save(tag)
        val lookup = level.registryAccess().lookupOrThrow(Registries.BLOCK)
        val pieces = piecesOf(tag, lookup, offset)
        val axis = when (definition.footprint) {
            Footprint.SQUARE_7, Footprint.SQUARE_1 -> {
                if (pieces.any { !BuildingFootprints.inside(definition, it.x, it.z) }) return "样式超出占地"
                "x"
            }
            Footprint.WALL_9 -> wallAxis(pieces) ?: return "样式超出占地"
        }
        val folder = folder(buildingId)
        java.nio.file.Files.createDirectories(folder)
        NbtIo.writeCompressed(tag, folder.resolve("level_$rank.nbt"))
        folder.resolve("level_$rank.json").toFile().writeText(
            """{"x":${offset.x},"y":${offset.y},"z":${offset.z},"axis":"$axis"}""",
        )
        return "已保存${definition.displayName} $rank 级样式"
    }

    fun rejection(
        level: ServerLevel,
        origin: BlockPos,
        definition: BuildingDefinition,
        rank: Int,
        facing: Direction,
    ): String? {
        val loaded = load(level, definition, rank) ?: return null
        val rotation = rotation(definition, loaded.axis, facing)
        val placed = loaded.pieces.map { rotate(it, rotation) }
        if (placed.any { !BuildingFootprints.inside(definition, it.x, it.z) }) return "样式超出占地"
        if (placed.any { piece ->
            val pos = origin.offset(piece.x, piece.y, piece.z)
            blocked(level, pos, origin) && (piece.y != COVER_BELOW || !coverable(level, pos))
        }) return BLOCKED
        return null
    }

    fun apply(
        level: ServerLevel,
        origin: BlockPos,
        definition: BuildingDefinition,
        rank: Int,
        facing: Direction,
        previousParts: List<BlockPos>,
        remember: (BlockPos, BlockState) -> Unit = { _, _ -> },
    ): List<BlockPos> {
        clearOwned(level, origin, previousParts)
        setCoreForm(level, origin, definition)
        val loaded = load(level, definition, rank) ?: return emptyList()
        val rotation = rotation(definition, loaded.axis, facing)
        val placed = ArrayList<BlockPos>()
        for (piece in loaded.pieces) {
            val relative = rotate(piece, rotation)
            if (!BuildingFootprints.inside(definition, relative.x, relative.z)) continue
            val pos = origin.offset(relative.x, relative.y, relative.z)
            if (pos == origin || level.isOutsideBuildHeight(pos)) continue
            if (pos.y == origin.y - 1) {
                val current = level.getBlockState(pos)
                if (!current.isAir &&
                    current.block !is SiteCoreBlock &&
                    current.block !is SiteStructureBlock &&
                    current.block !is SitePartBlock
                ) {
                    remember(pos, current)
                }
            }
            val state = ModSites.STRUCTURE.defaultBlockState()
            level.setBlock(pos, state, net.minecraft.world.level.block.Block.UPDATE_ALL)
            val entity = level.getBlockEntity(pos) as? SiteStructureBlockEntity ?: continue
            entity.appearance = piece.state
            entity.corePos = origin
            entity.setChanged()
            level.chunkSource.blockChanged(pos)
            placed.add(pos)
        }
        return placed
    }

    private fun wallAxis(pieces: List<Piece>): String? {
        val alongX = pieces.all { it.z == 0 && it.x in -4..4 }
        val alongZ = pieces.all { it.x == 0 && it.z in -4..4 }
        return when {
            alongX -> "x"
            alongZ -> "z"
            else -> null
        }
    }

    private fun rotation(definition: BuildingDefinition, axis: String, facing: Direction): Rotation {
        if (definition.footprint != Footprint.WALL_9) return Rotation.NONE
        val target = if (facing.axis == Direction.Axis.Z) "x" else "z"
        return when {
            axis == target -> Rotation.NONE
            axis == "x" -> Rotation.CLOCKWISE_90
            else -> Rotation.COUNTERCLOCKWISE_90
        }
    }

    private fun rotate(piece: Piece, rotation: Rotation): Piece {
        val (x, z) = when (rotation) {
            Rotation.CLOCKWISE_90 -> -piece.z to piece.x
            Rotation.CLOCKWISE_180 -> -piece.x to -piece.z
            Rotation.COUNTERCLOCKWISE_90 -> piece.z to -piece.x
            else -> piece.x to piece.z
        }
        val state = if (rotation == Rotation.NONE) piece.state else piece.state.rotate(rotation)
        return piece.copy(x = x, z = z, state = state)
    }

    private fun coverable(level: ServerLevel, pos: BlockPos): Boolean {
        val state = level.getBlockState(pos)
        if (state.block is SiteCoreBlock || state.block is SiteStructureBlock || state.block is SitePartBlock) return false
        return state.block != Blocks.BARRIER && state.block != Blocks.BEDROCK
    }

    private fun blocked(level: ServerLevel, pos: BlockPos, origin: BlockPos): Boolean {
        if (pos == origin) return false
        val state = level.getBlockState(pos)
        if (state.isAir) return false
        if (state.block is SiteStructureBlock) {
            val owner = (level.getBlockEntity(pos) as? SiteStructureBlockEntity)?.corePos
            return owner != origin
        }
        if (state.block is SitePartBlock) return false
        if (state.`is`(net.minecraft.tags.BlockTags.LEAVES)) return false
        if (state.`is`(net.minecraft.tags.BlockTags.FLOWERS)) return false
        if (state.`is`(net.minecraft.tags.BlockTags.SAPLINGS)) return false
        return state.block != Blocks.SHORT_GRASS &&
            state.block != Blocks.TALL_GRASS &&
            state.block != Blocks.FERN &&
            state.block != Blocks.LARGE_FERN
    }

    private fun clearOwned(level: ServerLevel, origin: BlockPos, previous: List<BlockPos>) {
        for (pos in previous) {
            val state = level.getBlockState(pos)
            if (state.block is SiteStructureBlock || state.block is SitePartBlock) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL)
            }
        }
        for (dx in -2..2) {
            for (dz in -2..2) {
                for (dy in -1..1) {
                    if (dx == 0 && dy == 0 && dz == 0) continue
                    val pos = origin.offset(dx, dy, dz)
                    if (!level.isOutsideBuildHeight(pos) && level.getBlockState(pos).block is SitePartBlock) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL)
                    }
                }
            }
        }
    }

    private fun setCoreForm(level: ServerLevel, origin: BlockPos, definition: BuildingDefinition) {
        val state = level.getBlockState(origin)
        if (state.block !is SiteCoreBlock) return
        val form = when (definition.id) {
            Buildings.REACTOR.id -> CoreForm.BLAST_FURNACE
            Buildings.WALL.id -> CoreForm.COPPER_LANTERN
            Buildings.TOWER.id -> CoreForm.FLETCHING_TABLE
            Buildings.BARRACKS.id -> CoreForm.CRAFTER
            else -> CoreForm.BEACON
        }
        if (state.getValue(SiteCoreBlock.FORM) == form) return
        level.setBlock(origin, state.setValue(SiteCoreBlock.FORM, form), net.minecraft.world.level.block.Block.UPDATE_ALL)
    }

    private fun load(level: ServerLevel, definition: BuildingDefinition, rank: Int): Loaded? {
        val template = level.structureManager.get(definition.structure(rank)).orElse(null) ?: return null
        val saved = CompoundTag()
        template.save(saved)
        val tag = saved
        val meta = readMeta(definition.id, rank)
        val lookup = level.registryAccess().lookupOrThrow(Registries.BLOCK)
        return Loaded(piecesOf(tag, lookup, BlockPos(meta.x, meta.y, meta.z)), meta.axis)
    }

    private fun piecesOf(
        tag: CompoundTag,
        lookup: net.minecraft.core.HolderGetter<net.minecraft.world.level.block.Block>,
        offset: BlockPos,
    ): List<Piece> {
        val palette = tag.getListOrEmpty("palette")
        val blocks = tag.getListOrEmpty("blocks")
        val pieces = ArrayList<Piece>()
        for (index in 0 until blocks.size) {
            val block = blocks.getCompoundOrEmpty(index)
            val pos = coordinates(block) ?: continue
            val state = NbtUtils.readBlockState(lookup, palette.getCompoundOrEmpty(block.getIntOr("state", 0)))
            if (state.isAir) continue
            val x = pos[0] - offset.x
            val y = pos[1] - offset.y
            val z = pos[2] - offset.z
            if (x == 0 && y == 0 && z == 0) continue
            pieces.add(Piece(x, y, z, state))
        }
        return pieces
    }

    private fun coordinates(block: CompoundTag): IntArray? {
        val array = block.getIntArray("pos").orElse(null)
        if (array != null && array.size >= 3) return array
        val list = block.getListOrEmpty("pos")
        if (list.size < 3) return null
        return intArrayOf(list.getIntOr(0, 0), list.getIntOr(1, 0), list.getIntOr(2, 0))
    }

    private data class Meta(val x: Int, val y: Int, val z: Int, val axis: String)

    private fun readMeta(buildingId: String, rank: Int): Meta {
        val text = BuildingStyles::class.java.getResourceAsStream("/data/wallwar-rebirth/building_style/$buildingId/level_$rank.json")
            ?.bufferedReader()?.use { it.readText() }
            ?: return Meta(0, 0, 0, "x")
        fun number(key: String): Int =
            Regex(""""$key"\s*:\s*(-?\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val axis = Regex(""""axis"\s*:\s*"([^"]*)"""").find(text)?.groupValues?.get(1) ?: "x"
        return Meta(number("x"), number("y"), number("z"), axis)
    }

    private fun folder(buildingId: String): Path =
        FabricLoader.getInstance().gameDir.resolve("structures").resolve("building").resolve(buildingId)

    const val BLOCKED = "这个位置有方块挡着"
    private const val COVER_BELOW = -1
}
