package shina.wallwarRebirth.match

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtOps
import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.storage.LevelResource
import shina.wallwarRebirth.profession.Classes
import shina.wallwarRebirth.rules.MatchRules
import shina.wallwarRebirth.shop.match.TeamWarehouses
import shina.wallwarRebirth.world.MatchWorld
import java.util.UUID
import kotlin.math.hypot

object MatchPersistence {
    internal fun file(current: MinecraftServer) =
        current.getWorldPath(LevelResource.ROOT).resolve("data").resolve("wallwar-match.nbt")

    internal fun save(current: MinecraftServer) {
        val tag = CompoundTag()
        tag.putString("Phase", Match.phase.name)
        tag.putBoolean("WorldReady", Match.worldReady)
        tag.putInt("MapSize", Match.mapSize)
        tag.putLong("TerrainSeed", Match.terrainSeed)
        tag.putLong("RedBase", Match.redBase.asLong())
        tag.putLong("BlueBase", Match.blueBase.asLong())
        writeSpawn(tag, "RedSpawn", Match.redSpawn)
        writeSpawn(tag, "BlueSpawn", Match.blueSpawn)
        val list = ListTag()
        val ops = current.registryAccess().createSerializationContext(NbtOps.INSTANCE)
        for (member in Match.members.values) {
            val entry = CompoundTag()
            entry.putString("Id", member.id.toString())
            entry.putString("Team", member.team)
            entry.putString("ClassId", member.classId)
            entry.putBoolean("ClassLocked", member.classLocked)
            entry.putLong("MinerUntil", member.minerUntil)
            entry.putBoolean("Ready", member.ready)
            entry.putBoolean("Started", member.started)
            entry.putBoolean("IronGiven", member.ironGiven)
            entry.putInt("RespawnAt", member.respawnAt)
            entry.putBoolean("PendingReset", member.pendingReset)
            entry.putInt("HeldLevel", member.heldLevel)
            entry.putFloat("HeldProgress", member.heldProgress)
            val items = ListTag()
            for (stack in member.heldItems) {
                val saved = ItemStack.CODEC.encodeStart(ops, stack).resultOrPartial { }.orElse(null)
                if (saved is CompoundTag) items.add(saved)
            }
            entry.put("HeldItems", items)
            list.add(entry)
        }
        tag.put("Members", list)
        TeamWarehouses.write(tag, ops)
        val path = file(current)
        java.nio.file.Files.createDirectories(path.parent)
        NbtIo.writeCompressed(tag, path)
    }

    internal fun load(current: MinecraftServer) {
        val path = file(current)
        if (!java.nio.file.Files.exists(path)) return
        val tag = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap())
        Match.phase = runCatching { Match.Phase.valueOf(tag.getStringOr("Phase", Match.Phase.LOBBY.name)) }.getOrDefault(Match.Phase.LOBBY)
        Match.worldReady = tag.getBooleanOr("WorldReady", false)
        val loadedSize = tag.getIntOr("MapSize", MatchRules.MAP_SIZE)
        Match.mapSize = if (loadedSize >= MatchRules.minimumMapSize() && (loadedSize - MatchRules.MAP_SIZE) % MatchRules.MAP_SIZE_STEP == 0) {
            loadedSize
        } else {
            MatchRules.MAP_SIZE
        }
        Match.terrainSeed = tag.getLongOr("TerrainSeed", 0L)
        if (Match.terrainSeed != 0L) MatchWorld.reseed(current, Match.terrainSeed)
        Match.redBase = BlockPos.of(tag.getLongOr("RedBase", 0L))
        Match.blueBase = BlockPos.of(tag.getLongOr("BlueBase", 0L))
        Match.redSpawn = readSpawn(tag, "RedSpawn")
        Match.blueSpawn = readSpawn(tag, "BlueSpawn")
        Match.members.clear()
        val ops = current.registryAccess().createSerializationContext(NbtOps.INSTANCE)
        TeamWarehouses.read(tag, ops)
        val list = tag.getListOrEmpty("Members")
        for (index in 0 until list.size) {
            val entry = list.getCompoundOrEmpty(index)
            val id = runCatching { UUID.fromString(entry.getStringOr("Id", "")) }.getOrNull() ?: continue
            val member = Match.Member(id)
            member.team = entry.getStringOr("Team", "")
            member.classId = Classes.MINER
            member.classLocked = true
            member.minerUntil = entry.getLongOr("MinerUntil", 0L)
            member.ready = entry.getBooleanOr("Ready", false)
            member.started = entry.getBooleanOr("Started", false)
            member.ironGiven = entry.getBooleanOr("IronGiven", false)
            member.respawnAt = entry.getIntOr("RespawnAt", 0)
            member.pendingReset = entry.getBooleanOr("PendingReset", false)
            member.heldLevel = entry.getIntOr("HeldLevel", 0)
            member.heldProgress = entry.getFloatOr("HeldProgress", 0f)
            val items = entry.getListOrEmpty("HeldItems")
            val stacks = ArrayList<ItemStack>()
            for (itemIndex in 0 until items.size) {
                val saved = items.getCompoundOrEmpty(itemIndex)
                val stack = ItemStack.CODEC.parse(ops, saved).resultOrPartial { }.orElse(ItemStack.EMPTY)
                if (stack != null) {
                    stacks.add(stack)
                }
            }
            member.heldItems = stacks
            Match.members[id] = member
        }
    }

    internal fun writeSpawn(tag: CompoundTag, key: String, spawn: Match.Spawn) {
        tag.putDouble(key + "X", spawn.x)
        tag.putDouble(key + "Y", spawn.y)
        tag.putDouble(key + "Z", spawn.z)
        tag.putFloat(key + "Yaw", spawn.yaw)
    }

    internal fun readSpawn(tag: CompoundTag, key: String): Match.Spawn = Match.Spawn(
        tag.getDoubleOr(key + "X", 0.5),
        tag.getDoubleOr(key + "Y", 80.0),
        tag.getDoubleOr(key + "Z", 0.5),
        tag.getFloatOr(key + "Yaw", 0f),
    )

    internal fun horizontal(a: BlockPos, b: BlockPos): Double {
        val dx = (a.x - b.x).toDouble()
        val dz = (a.z - b.z).toDouble()
        return hypot(dx, dz)
    }
}
