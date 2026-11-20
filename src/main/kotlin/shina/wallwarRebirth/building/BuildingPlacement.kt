package shina.wallwarRebirth.building

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.CropBlock
import net.minecraft.world.level.block.FarmBlock
import net.minecraft.world.phys.AABB
import shina.wallwarRebirth.monster.MonsterDefinition
import shina.wallwarRebirth.monster.Monsters
import shina.wallwarRebirth.monster.Summoning
import kotlin.math.abs
import kotlin.math.max

object BuildingPlacement {
    fun spawnAfterPlace(level: ServerLevel, site: SiteCoreBlockEntity) {
        if (site.buildingId == Buildings.RELAY.id) {
            spawn(level, site, Monsters.MINER)
        }
    }

    private fun spawn(
        level: ServerLevel,
        site: SiteCoreBlockEntity,
        definition: MonsterDefinition,
    ) {
        val feet = findStandable(level, site) ?: return
        val yaw = site.facingDirection().toYRot()
        Summoning.place(
            level,
            definition,
            site.team,
            feet.x + 0.5,
            feet.y.toDouble(),
            feet.z + 0.5,
            yaw,
        )
    }

    fun settle(level: ServerLevel, site: SiteCoreBlockEntity, lift: Int) {
        val players = level.players().filter { it.isAlive && !it.isSpectator }
        if (lift > 0) {
            for (player in players) {
                if (!inside(player, site)) continue
                player.teleportTo(level, player.x, player.y + lift, player.z, emptySet(), player.yRot, player.xRot, false)
            }
        }
        val taken = HashSet<BlockPos>()
        for (player in players) {
            if (!inside(player, site)) continue
            val stand = findStandable(level, site, taken) ?: continue
            taken.add(stand)
            player.teleportTo(level, stand.x + 0.5, stand.y.toDouble(), stand.z + 0.5, emptySet(), player.yRot, player.xRot, false)
        }
    }

    private fun inside(player: ServerPlayer, site: SiteCoreBlockEntity): Boolean {
        val box = player.boundingBox.inflate(-0.05, -0.05, -0.05)
        if (box.intersects(AABB(site.blockPos))) return true
        return site.partPositions.any { box.intersects(AABB(it)) }
    }

    private fun findStandable(level: ServerLevel, site: SiteCoreBlockEntity, excluded: Set<BlockPos> = emptySet()): BlockPos? {
        val origin = site.blockPos
        val spots = ArrayList<BlockPos>()
        for (dy in intArrayOf(0, -1, 1)) {
            for (dx in -SEARCH..SEARCH) {
                for (dz in -SEARCH..SEARCH) {
                    if (dx == 0 && dy == 0 && dz == 0) continue
                    spots.add(origin.offset(dx, dy, dz))
                }
            }
        }
        spots.sortWith(
            compareBy<BlockPos> { abs(it.y - origin.y) }
                .thenBy { max(abs(it.x - origin.x), abs(it.z - origin.z)) }
                .thenBy { dist2(origin, it) }
                .thenBy { it.x }
                .thenBy { it.z },
        )
        return spots.firstOrNull { it !in excluded && usable(level, site, it) }
    }

    private fun usable(level: ServerLevel, site: SiteCoreBlockEntity, pos: BlockPos): Boolean {
        if (insideBuilding(site, pos)) return false
        if (!Summoning.canOccupy(level, pos, null) || !Summoning.canOccupy(level, pos.above(), null)) return false
        val state = level.getBlockState(pos)
        if (!state.fluidState.isEmpty) return false
        if (state.block is CropBlock || state.block is FarmBlock) return false
        val belowPos = pos.below()
        val below = level.getBlockState(belowPos)
        if (!below.fluidState.isEmpty) return false
        if (below.getCollisionShape(level, belowPos).isEmpty) return false
        return true
    }

    private fun insideBuilding(site: SiteCoreBlockEntity, pos: BlockPos): Boolean {
        if (pos == site.blockPos || pos.above() == site.blockPos) return true
        if (site.encloses(pos) || site.encloses(pos.above())) return true
        val definition = Buildings.byId(site.buildingId) ?: return false
        val cells = BuildingFootprints.cells(definition, site.blockPos, site.facingDirection())
        return pos in cells
    }

    private fun dist2(origin: BlockPos, pos: BlockPos): Long {
        val dx = (origin.x - pos.x).toLong()
        val dy = (origin.y - pos.y).toLong()
        val dz = (origin.z - pos.z).toLong()
        return dx * dx + dy * dy + dz * dz
    }

    private const val SEARCH = 6
}
