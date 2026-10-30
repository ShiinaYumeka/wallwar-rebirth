package shina.wallwarRebirth.building

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import shina.wallwarRebirth.monster.ModMonsters
import shina.wallwarRebirth.monster.SummonedZombie
import kotlin.math.atan2
import kotlin.math.sqrt

object BuildingCombat {
    fun tick(site: SiteCoreBlockEntity, level: ServerLevel) {
        val before = site.actionAge
        when (site.buildingId) {
            Buildings.TOWER.id -> tickTower(site, level)
            Buildings.BARRACKS.id -> tickBarracks(site, level)
        }
        if (site.actionAge != before && (site.actionAge % 20 == 0 || site.actionAge == 15)) site.setChanged()
    }

    private fun tickTower(site: SiteCoreBlockEntity, level: ServerLevel) {
        val interval = if (site.buildingLevel >= 3) 15 else 20
        if (site.actionAge < interval) site.actionAge++
        if (site.actionAge < interval) return
        val range = when (site.buildingLevel) {
            1 -> 12.0
            2 -> 16.0
            else -> 20.0
        }
        val target = nearestTarget(site, level, range) ?: return
        site.actionAge = 0
        site.setChanged()
        val speed = when (site.buildingLevel) {
            1 -> 2f
            2 -> 3f
            else -> 4f
        }
        shoot(site, level, target, speed)
    }

    private fun tickBarracks(site: SiteCoreBlockEntity, level: ServerLevel) {
        val interval = when (site.buildingLevel) {
            1 -> 600
            2 -> 400
            else -> 300
        }
        if (site.actionAge < interval) site.actionAge++
        if (site.actionAge < interval) return
        site.actionAge = 0
        site.setChanged()
        spawn(site, level)
    }

    private fun nearestTarget(site: SiteCoreBlockEntity, level: ServerLevel, range: Double): LivingEntity? {
        val origin = Vec3.atCenterOf(site.blockPos)
        val box = AABB.ofSize(origin, range * 2.0, range * 2.0, range * 2.0)
        val monsters = level.getEntitiesOfClass(SummonedZombie::class.java, box)
            .filter { it.isAlive && hostile(site.team, it.summonedTeam) && distanceToBox(origin, it.boundingBox) <= range + 0.001 }
        pick(origin, monsters)?.let { return it }
        val players = level.players()
            .filter { it.isAlive && !it.isSpectator && hostile(site.team, it.team?.name ?: "") && distanceToBox(origin, it.boundingBox) <= range + 0.001 }
        return pick(origin, players)
    }

    private fun <T : LivingEntity> pick(origin: Vec3, entities: List<T>): T? =
        entities.minWithOrNull(
            compareBy<T> { (distanceToBox(origin, it.boundingBox) * 1000.0).toLong() }
                .thenBy { it.health }
                .thenBy { it.uuid.mostSignificantBits }
                .thenBy { it.uuid.leastSignificantBits },
        )

    private fun shoot(site: SiteCoreBlockEntity, level: ServerLevel, target: LivingEntity, speed: Float) {
        val origin = Vec3.atCenterOf(site.blockPos)
        val aim = target.eyePosition.subtract(origin)
        if (aim.lengthSqr() < 1.0e-6) return
        val direction = aim.normalize()
        val muzzle = muzzle(site, level, origin, direction, aim.length()) ?: return
        val arrow = TowerArrow(ModMonsters.TOWER_ARROW, level)
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

    private fun spawn(site: SiteCoreBlockEntity, level: ServerLevel) {
        val step = spawnStep(site, level)
        val feet = site.blockPos.offset(step)
        val head = feet.above()
        if (!canOccupy(level, feet) || !canOccupy(level, head)) return
        val zombie = SummonedZombie(ModMonsters.ZOMBIE, level)
        zombie.summonedTeam = site.team
        val yaw = Math.toDegrees(atan2(-step.x.toDouble(), step.z.toDouble())).toFloat()
        zombie.snapTo(feet.x + 0.5, feet.y.toDouble(), feet.z + 0.5, yaw, 0f)
        level.addFreshEntity(zombie)
    }

    private fun spawnStep(site: SiteCoreBlockEntity, level: ServerLevel): BlockPos {
        val enemy = nearestCore(site, level)
        if (enemy == null || (enemy.blockPos.x == site.blockPos.x && enemy.blockPos.z == site.blockPos.z)) {
            val facing = site.facingDirection()
            return BlockPos(facing.stepX, 0, facing.stepZ)
        }
        val dx = enemy.blockPos.x.compareTo(site.blockPos.x)
        val dz = enemy.blockPos.z.compareTo(site.blockPos.z)
        return BlockPos(dx, 0, dz)
    }

    private fun nearestCore(site: SiteCoreBlockEntity, level: ServerLevel): SiteCoreBlockEntity? =
        SiteCores.all()
            .filter {
                it.level === level &&
                    !it.isRemoved &&
                    it.health > 0 &&
                    it.buildingId.isNotEmpty() &&
                    it.blockPos != site.blockPos &&
                    hostile(site.team, it.team)
            }
            .minWithOrNull(
                compareBy<SiteCoreBlockEntity> { dist2(site.blockPos, it.blockPos) }
                    .thenBy { it.health }
                    .thenBy { it.blockPos.x }
                    .thenBy { it.blockPos.z }
                    .thenBy { it.blockPos.y },
            )

    private fun canOccupy(level: ServerLevel, pos: BlockPos): Boolean {
        if (level.isOutsideBuildHeight(pos)) return false
        val state = level.getBlockState(pos)
        if (state.isAir) return true
        val player = level.players().firstOrNull() ?: return false
        val hit = BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)
        val context = BlockPlaceContext(player, InteractionHand.MAIN_HAND, ItemStack.EMPTY, hit)
        return state.canBeReplaced(context)
    }

    private fun hostile(buildingTeam: String, other: String): Boolean = buildingTeam != other

    private fun distanceToBox(origin: Vec3, box: AABB): Double {
        val dx = (origin.x.coerceIn(box.minX, box.maxX) - origin.x).let { if (origin.x in box.minX..box.maxX) 0.0 else it }
        val dy = if (origin.y in box.minY..box.maxY) 0.0 else origin.y.coerceIn(box.minY, box.maxY) - origin.y
        val dz = if (origin.z in box.minZ..box.maxZ) 0.0 else origin.z.coerceIn(box.minZ, box.maxZ) - origin.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun dist2(origin: BlockPos, pos: BlockPos): Long {
        val dx = (origin.x - pos.x).toLong()
        val dy = (origin.y - pos.y).toLong()
        val dz = (origin.z - pos.z).toLong()
        return dx * dx + dy * dy + dz * dz
    }
}
