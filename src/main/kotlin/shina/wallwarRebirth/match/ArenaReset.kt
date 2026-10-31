package shina.wallwarRebirth.match

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap
import net.minecraft.server.level.ChunkHolder
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.chunk.storage.EntityStorage
import net.minecraft.world.level.chunk.storage.IOWorker
import net.minecraft.world.level.chunk.storage.SectionStorage
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage
import net.minecraft.world.level.dimension.DimensionType
import net.minecraft.world.level.storage.LevelResource
import org.slf4j.LoggerFactory
import shina.wallwarRebirth.building.SiteCores
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CompletableFuture
import java.util.function.BooleanSupplier

object ArenaReset {
    private val logger = LoggerFactory.getLogger("wallwar-rebirth")
    private val chunks = Collections.newSetFromMap(IdentityHashMap<ChunkAccess, Boolean>())

    @JvmStatic
    fun condemned(chunk: ChunkAccess): Boolean = chunks.contains(chunk)

    fun holding(): Boolean = chunks.isNotEmpty()

    fun forget(level: ServerLevel) {
        if (chunks.isEmpty()) return
        val pending = level.chunkSource.chunkMap.pendingUnloads
        val source = level.chunkSource
        chunks.removeIf { chunk ->
            val pos = chunk.pos
            if (pending.containsKey(pos.toLong())) return@removeIf false
            source.getChunkNow(pos.x, pos.z) !== chunk
        }
    }

    fun wipe(level: ServerLevel): Boolean {
        return try {
            forget(level)
            for (core in SiteCores.all().filter { it.level === level }.toList()) core.destroy()
            discardCreatures(level)
            val chunkMap = level.chunkSource.chunkMap
            chunkMap.synchronize(true).join()
            val entities = entityRegions(level)
            val poi = poiRegions(level)
            entities.synchronize(true).join()
            poi.synchronize(true).join()
            val loaded = unload(chunkMap)
            val positions = LinkedHashSet<ChunkPos>()
            positions.addAll(loaded)
            val folder = DimensionType.getStorageFolder(level.dimension(), level.server.getWorldPath(LevelResource.ROOT))
            positions.addAll(chunkPositions(folder.resolve("region")))
            positions.addAll(chunkPositions(folder.resolve("entities")))
            positions.addAll(chunkPositions(folder.resolve("poi")))
            erase(chunkMap, positions)
            erase(entities, positions)
            erase(poi, positions)
            clearPoi(level)
            chunkMap.chunkTypeCache.clear()
            level.chunkSource.clearCache()
            forget(level)
            logger.info("竞技场已清空 {} 个区块", positions.size)
            true
        } catch (error: Exception) {
            logger.error("重置竞技场失败", error)
            false
        }
    }

    private fun discardCreatures(level: ServerLevel) {
        val present = ArrayList<Entity>()
        level.allEntities.forEach { present.add(it) }
        for (entity in present) {
            if (entity is Player || entity.isRemoved) continue
            entity.discard()
        }
    }

    private fun unload(chunkMap: net.minecraft.server.level.ChunkMap): Set<ChunkPos> {
        val loaded = LinkedHashSet<ChunkPos>()
        val keep = BooleanSupplier { true }
        repeat(8) {
            val holders = holders(chunkMap.updatingChunkMap)
            if (holders.isEmpty()) return@repeat
            for ((pos, chunk) in holders) {
                loaded.add(pos)
                if (chunk == null) continue
                chunks.add(chunk)
                chunk.tryMarkSaved()
            }
            for ((pos, _) in holders) chunkMap.toDrop.add(pos.toLong())
            chunkMap.processUnloads(keep)
            chunkMap.promoteChunkMap()
        }
        return loaded
    }

    private fun holders(map: Long2ObjectLinkedOpenHashMap<*>): List<Pair<ChunkPos, ChunkAccess?>> {
        val result = ArrayList<Pair<ChunkPos, ChunkAccess?>>(map.size)
        for (entry in map.long2ObjectEntrySet()) {
            val holder = entry.value as? ChunkHolder ?: continue
            result.add(ChunkPos(entry.longKey) to holder.latestChunk)
        }
        return result
    }

    private fun entityRegions(level: ServerLevel): SimpleRegionStorage {
        return (level.entityManager.permanentStorage as EntityStorage).simpleRegionStorage
    }

    private fun poiRegions(level: ServerLevel): SimpleRegionStorage {
        return (level.poiManager as SectionStorage<*, *>).simpleRegionStorage
    }

    private fun clearPoi(level: ServerLevel) {
        val poi = level.poiManager
        val sections = poi as SectionStorage<*, *>
        sections.storage.clear()
        sections.dirtyChunks.clear()
        sections.pendingLoads.clear()
        sections.loadedChunks.clear()
        poi.loadedChunks.clear()
    }

    private fun erase(storage: SimpleRegionStorage, positions: Collection<ChunkPos>) {
        if (positions.isEmpty()) return
        val futures = positions.map { pos ->
            storage.write(pos, IOWorker.STORE_EMPTY).exceptionally { error ->
                logger.warn("删除区块 {} 失败: {}", pos, error.toString())
                null
            }
        }
        CompletableFuture.allOf(*futures.toTypedArray()).join()
        storage.synchronize(true).join()
    }

    private fun chunkPositions(directory: Path): Set<ChunkPos> {
        if (!Files.isDirectory(directory)) return emptySet()
        val found = HashSet<ChunkPos>()
        Files.newDirectoryStream(directory).use { stream ->
            for (file in stream) {
                val name = file.fileName.toString()
                if (!name.startsWith("r.") || !name.endsWith(".mca")) continue
                val parts = name.removeSuffix(".mca").split('.')
                if (parts.size != 3) continue
                val regionX = parts[1].toIntOrNull() ?: continue
                val regionZ = parts[2].toIntOrNull() ?: continue
                val header = readHeader(file)
                if (header == null) {
                    for (localX in 0..31) {
                        for (localZ in 0..31) found.add(ChunkPos(regionX * 32 + localX, regionZ * 32 + localZ))
                    }
                    continue
                }
                for (index in 0 until 1024) {
                    val offset = index * 4
                    val sector = ((header[offset].toInt() and 0xFF) shl 16) or
                        ((header[offset + 1].toInt() and 0xFF) shl 8) or
                        (header[offset + 2].toInt() and 0xFF)
                    if (sector == 0) continue
                    found.add(ChunkPos(regionX * 32 + (index and 31), regionZ * 32 + (index shr 5)))
                }
            }
        }
        return found
    }

    private fun readHeader(file: Path): ByteArray? {
        return try {
            Files.newInputStream(file).use { input ->
                val header = ByteArray(4096)
                var read = 0
                while (read < header.size) {
                    val count = input.read(header, read, header.size - read)
                    if (count < 0) return null
                    read += count
                }
                header
            }
        } catch (_: Exception) {
            null
        }
    }
}
