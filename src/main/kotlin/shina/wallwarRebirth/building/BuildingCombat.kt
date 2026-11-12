package shina.wallwarRebirth.building

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.BlockTags
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import shina.wallwarRebirth.monster.ModMonsters
import shina.wallwarRebirth.rules.MatchRules
import shina.wallwarRebirth.targeting.CannonTargeting
import shina.wallwarRebirth.targeting.TargetWorlds
import shina.wallwarRebirth.targeting.TeslaTargeting
import shina.wallwarRebirth.targeting.TowerTargeting
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object BuildingCombat {
    fun tick(site: SiteCoreBlockEntity, level: ServerLevel) {
        val before = site.actionAge
        val beforeEgg = site.eggReleaseAge
        when (site.buildingId) {
            Buildings.BASE.id -> tickBase(site)
            Buildings.WALL.id -> tickWall(site)
            Buildings.TOWER.id -> tickTower(site, level)
            Buildings.TESLA.id -> tickTesla(site, level)
            Buildings.CANNON.id -> tickCannon(site, level)
            Buildings.BARRACKS.id, Buildings.PILLAGER.id -> BarracksWork.tick(site, level)
            Buildings.FARMLAND.id -> FarmlandWork.tick(site, level)
        }
        if (site.actionAge != before && (site.actionAge % 20 == 0 || site.actionAge == 30)) site.setChanged()
        if (site.eggReleaseAge != beforeEgg && site.eggReleaseAge == 0) site.setChanged()
    }

    private fun tickBase(site: SiteCoreBlockEntity) {
        if (site.reinforceTwo.isEmpty || site.health <= 0) {
            site.healAge = 0
            return
        }
        site.healAge++
        if (site.healAge < MatchRules.BASE_REGEN_TICKS) return
        site.healAge = 0
        val definition = Buildings.byId(site.buildingId) ?: return
        val maxHealth = definition.maxHealth(site.buildingLevel)
        if (site.health >= maxHealth) return
        site.health = minOf(maxHealth, site.health + MatchRules.BASE_REGEN_AMOUNT)
        site.setChanged()
    }

    private fun tickWall(site: SiteCoreBlockEntity) {
        if (!site.moduleActive(ModuleSlot.PRIMARY) || site.health <= 0) return
        if (site.age % MatchRules.WALL_HEAL_TICKS != 0) return
        if (site.lastHurtAge >= site.age - MatchRules.TICKS_PER_SECOND) return
        val definition = Buildings.byId(site.buildingId) ?: return
        val maxHealth = definition.maxHealth(site.buildingLevel)
        if (site.health >= maxHealth) return
        site.health = minOf(maxHealth, site.health + MatchRules.WALL_HEAL_AMOUNT)
        site.setChanged()
    }

    private fun tickTower(site: SiteCoreBlockEntity, level: ServerLevel) {
        val index = (site.buildingLevel - 1).coerceIn(0, MatchRules.TOWER_RANGE.lastIndex)
        val range = MatchRules.TOWER_RANGE[index]
        val target = nearestTarget(site, level, range)
        TowerCrossbows.aim(site, level, target)
        val baseInterval = MatchRules.TOWER_INTERVAL[index]
        val interval = if (site.reinforceTwo.isEmpty) baseInterval else baseInterval * 4 / 5
        val (nextAge, fire) = IntervalGate.step(site.actionAge, interval, target != null)
        site.actionAge = nextAge
        if (!fire || target == null) return
        site.setChanged()
        shoot(site, level, target, MatchRules.TOWER_SPEED[index])
    }

    private fun tickTesla(site: SiteCoreBlockEntity, level: ServerLevel) {
        val index = (site.buildingLevel - 1).coerceIn(0, MatchRules.TESLA_RANGE.lastIndex)
        val target = teslaTarget(site, level, MatchRules.TESLA_RANGE[index])
        val interval = MatchRules.TESLA_INTERVAL
        val (nextAge, fire) = IntervalGate.step(site.actionAge, interval, target != null)
        site.actionAge = nextAge
        if (!fire || target == null) return
        site.setChanged()
        target.hurtServer(level, level.damageSources().lightningBolt(), MatchRules.TESLA_DAMAGE.toFloat())
        target.addEffect(MobEffectInstance(MobEffects.WEAKNESS, MatchRules.TESLA_WEAKNESS_TICKS, 0, false, true))
        target.addEffect(MobEffectInstance(MobEffects.SLOWNESS, MatchRules.TESLA_SLOWNESS_TICKS, 0, false, true))
        target.addEffect(MobEffectInstance(MobEffects.BLINDNESS, MatchRules.TESLA_BLINDNESS_TICKS, 0, false, true))
    }

    private fun teslaTarget(site: SiteCoreBlockEntity, level: ServerLevel, range: Double): ServerPlayer? =
        TeslaTargeting.select(
            TargetWorlds.world,
            level,
            Vec3.atCenterOf(site.blockPos),
            site.team,
            range,
            MatchRules.TESLA_TIE_DISTANCE,
        )

    private fun tickCannon(site: SiteCoreBlockEntity, level: ServerLevel) {
        val index = (site.buildingLevel - 1).coerceIn(0, MatchRules.CANNON_CENTER_DAMAGE.lastIndex)
        val interval = if (site.reinforceTwo.isEmpty) MatchRules.CANNON_INTERVAL else MatchRules.CANNON_INTERVAL * 4 / 5
        val wide = !site.reinforce.isEmpty
        if (site.gunner != null && !site.diffuse.isEmpty) {
            val rider = CannonCrew.rider(site, level)
            if (rider == null) {
                if (CannonCrew.holding(site, level)) {
                    if (site.actionAge < interval) site.actionAge++
                    return
                }
            } else {
                if (CannonCrew.tickWaiting(site, level, rider)) return
                val aimed = cannonTarget(site, level)
                val (waitingAge, waitingFire) = IntervalGate.step(site.actionAge, interval, aimed != null)
                site.actionAge = waitingAge
                if (!waitingFire || aimed == null) return
                site.setChanged()
                CannonShell.launchManned(level, site, rider, MatchRules.CANNON_CENTER_DAMAGE[index], wide)
                return
            }
        }
        val target = cannonTarget(site, level)
        val (nextAge, fire) = IntervalGate.step(site.actionAge, interval, target != null)
        site.actionAge = nextAge
        if (!fire || target == null) return
        site.setChanged()
        CannonShell.launch(level, site, target, MatchRules.CANNON_CENTER_DAMAGE[index], wide)
    }

    private fun cannonTarget(site: SiteCoreBlockEntity, level: ServerLevel): Vec3? =
        CannonTargeting.select(
            TargetWorlds.world,
            level,
            Vec3.atCenterOf(site.blockPos),
            site.team,
            site.blockPos,
            MatchRules.CANNON_RANGE,
            MatchRules.CANNON_TIE_DISTANCE,
        )?.point

    private fun coreBox(level: ServerLevel, pos: BlockPos): AABB {
        val shape = level.getBlockState(pos).getCollisionShape(level, pos)
        if (shape.isEmpty) return AABB(pos)
        return shape.bounds().move(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
    }

    private fun nearestTarget(site: SiteCoreBlockEntity, level: ServerLevel, range: Double): LivingEntity? =
        TowerTargeting.select(
            TargetWorlds.world,
            level,
            Vec3.atCenterOf(site.blockPos),
            site.team,
            range,
            MatchRules.TOWER_TIE_DISTANCE,
        )

    private fun <T : LivingEntity> pick(origin: Vec3, entities: List<T>, tie: Double = MatchRules.TOWER_TIE_DISTANCE): T? =
        entities.minWithOrNull(
            compareBy<T> { (distanceToBox(origin, it.boundingBox) / tie).toLong() }
                .thenBy { it.health }
                .thenBy { it.id },
        )

    private fun shoot(site: SiteCoreBlockEntity, level: ServerLevel, target: LivingEntity, speed: Float) {
        val origin = Vec3.atCenterOf(site.blockPos)
        val aim = target.eyePosition.subtract(origin)
        if (aim.lengthSqr() < 1.0e-6) return
        val direction = aim.normalize()
        val muzzle = muzzle(site, level, origin, direction, aim.length()) ?: return
        val pierce = !site.reinforce.isEmpty
        val spread = !site.diffuse.isEmpty
        fire(site, level, muzzle, direction, speed, pierce)
        if (!spread) return
        fire(site, level, muzzle, yawTurn(direction, -MatchRules.TOWER_SPREAD_DEGREES), speed, pierce)
        fire(site, level, muzzle, yawTurn(direction, MatchRules.TOWER_SPREAD_DEGREES), speed, pierce)
    }

    private fun fire(
        site: SiteCoreBlockEntity,
        level: ServerLevel,
        muzzle: Vec3,
        direction: Vec3,
        speed: Float,
        pierce: Boolean,
    ) {
        val arrow = TowerArrow(ModMonsters.TOWER_ARROW, level)
        arrow.setBaseDamage(MatchRules.TOWER_ARROW_BASE_DAMAGE)
        arrow.towerTeam = site.team
        if (pierce) arrow.setPierceHits(MatchRules.TOWER_PIERCE_HITS)
        val yaw = Math.toDegrees(atan2(-direction.x, direction.z)).toFloat()
        arrow.snapTo(muzzle.x, muzzle.y, muzzle.z, yaw, 0f)
        arrow.shoot(direction.x, direction.y, direction.z, speed, 0f)
        level.addFreshEntity(arrow)
    }

    private fun yawTurn(direction: Vec3, degrees: Double): Vec3 {
        val horizontal = sqrt(direction.x * direction.x + direction.z * direction.z)
        val pitch = atan2(direction.y, horizontal)
        val yaw = atan2(-direction.x, direction.z) + Math.toRadians(degrees)
        val cosPitch = cos(pitch)
        return Vec3(-sin(yaw) * cosPitch, sin(pitch), cos(yaw) * cosPitch)
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
            if (state.`is`(BlockTags.PLANKS)) {
                level.destroyBlock(pos, true)
                return null
            }
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
