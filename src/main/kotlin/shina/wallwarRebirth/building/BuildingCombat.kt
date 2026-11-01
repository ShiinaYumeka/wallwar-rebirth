package shina.wallwarRebirth.building

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import shina.wallwarRebirth.match.MatchRules
import shina.wallwarRebirth.monster.ModMonsters
import shina.wallwarRebirth.monster.SummonedUnit
import kotlin.math.atan2
import kotlin.math.sqrt

object BuildingCombat {
    fun tick(site: SiteCoreBlockEntity, level: ServerLevel) {
        val before = site.actionAge
        val beforeEgg = site.eggReleaseAge
        when (site.buildingId) {
            Buildings.TOWER.id -> tickTower(site, level)
            Buildings.BARRACKS.id -> BarracksWork.tick(site, level)
        }
        if (site.actionAge != before && (site.actionAge % 20 == 0 || site.actionAge == 30)) site.setChanged()
        if (site.eggReleaseAge != beforeEgg && site.eggReleaseAge == 0) site.setChanged()
    }

    private fun tickTower(site: SiteCoreBlockEntity, level: ServerLevel) {
        val index = (site.buildingLevel - 1).coerceIn(0, MatchRules.TOWER_RANGE.lastIndex)
        val range = MatchRules.TOWER_RANGE[index]
        val target = nearestTarget(site, level, range)
        TowerCrossbows.aim(site, level, target)
        val interval = MatchRules.TOWER_INTERVAL[index]
        if (site.actionAge < interval) site.actionAge++
        if (site.actionAge < interval || target == null) return
        site.actionAge = 0
        site.setChanged()
        shoot(site, level, target, MatchRules.TOWER_SPEED[index])
    }

    private fun nearestTarget(site: SiteCoreBlockEntity, level: ServerLevel, range: Double): LivingEntity? {
        val origin = Vec3.atCenterOf(site.blockPos)
        val box = AABB.ofSize(origin, range * 2.0, range * 2.0, range * 2.0)
        val monsters = level.getEntitiesOfClass(LivingEntity::class.java, box)
            .filter {
                it is SummonedUnit &&
                    it.isAlive &&
                    hostile(site.team, it.summonedTeam) &&
                    distanceToBox(origin, it.boundingBox) <= range + MatchRules.TOWER_TIE_DISTANCE
            }
        pick(origin, monsters)?.let { return it }
        val players = level.players()
            .filter { it.isAlive && !it.isSpectator && hostile(site.team, it.team?.name ?: "") && distanceToBox(origin, it.boundingBox) <= range + MatchRules.TOWER_TIE_DISTANCE }
        return pick(origin, players)
    }

    private fun <T : LivingEntity> pick(origin: Vec3, entities: List<T>): T? =
        entities.minWithOrNull(
            compareBy<T> { (distanceToBox(origin, it.boundingBox) / MatchRules.TOWER_TIE_DISTANCE).toLong() }
                .thenBy { it.health }
                .thenBy { it.id },
        )

    private fun shoot(site: SiteCoreBlockEntity, level: ServerLevel, target: LivingEntity, speed: Float) {
        val origin = Vec3.atCenterOf(site.blockPos)
        val aim = target.eyePosition.subtract(origin)
        if (aim.lengthSqr() < 1.0e-6) return
        val direction = aim.normalize()
        val muzzle = muzzle(site, level, origin, direction, aim.length()) ?: return
        val arrow = TowerArrow(ModMonsters.TOWER_ARROW, level)
        arrow.setBaseDamage(MatchRules.TOWER_ARROW_BASE_DAMAGE)
        arrow.towerTeam = site.team
        val yaw = Math.toDegrees(atan2(-direction.x, direction.z)).toFloat()
        arrow.snapTo(muzzle.x, muzzle.y, muzzle.z, yaw, 0f)
        arrow.shoot(direction.x, direction.y, direction.z, speed, 0f)
        level.addFreshEntity(arrow)
    }

    private fun muzzle(
        site: SiteCoreBlockEntity,
        level: ServerLevel,
        origin: Vec3,
        direction: Vec3,
        distance: Double,
    ): Vec3? {
        var traveled = 0.0
        var cursor = origin
        while (traveled < distance) {
            cursor = cursor.add(direction.scale(0.25))
            traveled += 0.25
            val pos = BlockPos.containing(cursor)
            if (owns(site, level, pos)) continue
            val state = level.getBlockState(pos)
            if (collisionEmpty(level, pos, state)) return cursor
            return null
        }
        return cursor
    }

    private fun owns(site: SiteCoreBlockEntity, level: ServerLevel, pos: BlockPos): Boolean {
        if (pos == site.blockPos) return true
        if (site.partPositions.any { it == pos }) return true
        val state = level.getBlockState(pos)
        if (state.block is SiteStructureBlock) {
            val part = level.getBlockEntity(pos) as? SiteStructureBlockEntity
            return part?.corePos == site.blockPos
        }
        return false
    }

    private fun collisionEmpty(level: ServerLevel, pos: BlockPos, state: BlockState): Boolean =
        state.getCollisionShape(level, pos).isEmpty

    private fun hostile(buildingTeam: String, other: String): Boolean = buildingTeam != other

    private fun distanceToBox(origin: Vec3, box: AABB): Double {
        val dx = (origin.x.coerceIn(box.minX, box.maxX) - origin.x).let { if (origin.x in box.minX..box.maxX) 0.0 else it }
        val dy = if (origin.y in box.minY..box.maxY) 0.0 else origin.y.coerceIn(box.minY, box.maxY) - origin.y
        val dz = if (origin.z in box.minZ..box.maxZ) 0.0 else origin.z.coerceIn(box.minZ, box.maxZ) - origin.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}
