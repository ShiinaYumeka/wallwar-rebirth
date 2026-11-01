package shina.wallwarRebirth.monster

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.InteractionHand
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.animal.golem.IronGolem
import net.minecraft.world.phys.Vec3
import shina.wallwarRebirth.building.BuildingMining
import shina.wallwarRebirth.building.Buildings
import shina.wallwarRebirth.building.SiteCoreBlockEntity
import shina.wallwarRebirth.building.SiteCores
import shina.wallwarRebirth.building.SitePartBlock
import shina.wallwarRebirth.building.SiteStructureBlock
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt

class SummonedBrain(private val mob: Mob, private val unit: SummonedUnit) {
    var chaseRadius: Double = MonsterRules.CHASE_RADIUS_START
    private var phase = Phase.PATH
    private var focusId: UUID? = null
    private var counterSourceId: UUID? = null
    private var corePos: BlockPos? = null
    private var meleeCooldown = 0
    private var buildingCooldown = stats.buildingIntervalTicks
    private var ticksSinceExchange = 0
    private var radiusClock = 0
    private var coreTimer = MonsterRules.CORE_RETARGET_TICKS
    private var stuckTicks = 0
    private var stuckX = 0.0
    private var stuckZ = 0.0
    private var stuckReady = false

    fun tick(level: ServerLevel) {
        if (!mob.isAlive) return
        SummonedUnits.add(unit)
        if (meleeCooldown > 0) meleeCooldown--
        ticksSinceExchange++
        when (phase) {
            Phase.COUNTER -> tickCounter(level)
            else -> tickSeek(level)
        }
    }

    fun onDamaged(level: ServerLevel, source: DamageSource) {
        val enemies = enemiesInRadius(level)
        if (enemies.isEmpty()) return
        val attacker = source.entity as? LivingEntity
        val chosen = if (attacker != null && enemies.any { it === attacker }) attacker else nearestEnemy(enemies)
        if (chosen == null) return
        phase = Phase.COUNTER
        counterSourceId = attacker?.uuid
        focusId = chosen.uuid
        ticksSinceExchange = 0
        stuckReady = false
        mob.setTarget(chosen)
    }

    fun noteExchange() {
        ticksSinceExchange = 0
    }

    fun save(output: net.minecraft.world.level.storage.ValueOutput) {
        output.putString("Kind", unit.kindId)
        output.putString("Team", unit.summonedTeam)
        output.putString("Phase", phase.name)
        output.putDouble("ChaseRadius", chaseRadius)
        output.putInt("MeleeCooldown", meleeCooldown)
        output.putInt("BuildingCooldown", buildingCooldown)
        output.putInt("TicksSinceExchange", ticksSinceExchange)
        output.putInt("RadiusClock", radiusClock)
        output.putInt("CoreTimer", coreTimer)
        output.putInt("StuckTicks", stuckTicks)
        output.putDouble("StuckX", stuckX)
        output.putDouble("StuckZ", stuckZ)
        output.putBoolean("StuckReady", stuckReady)
        val pos = corePos
        output.putBoolean("HasCore", pos != null)
        if (pos != null) {
            output.putInt("CoreX", pos.x)
            output.putInt("CoreY", pos.y)
            output.putInt("CoreZ", pos.z)
        }
        output.putString("Focus", focusId?.toString() ?: "")
        output.putString("CounterSource", counterSourceId?.toString() ?: "")
    }

    fun load(input: net.minecraft.world.level.storage.ValueInput) {
        unit.kindId = input.getStringOr("Kind", unit.kindId)
        if (Monsters.byId(unit.kindId) == null) unit.kindId = Monsters.ZOMBIE.id
        unit.summonedTeam = input.getStringOr("Team", "")
        phase = runCatching { Phase.valueOf(input.getStringOr("Phase", Phase.PATH.name)) }.getOrDefault(Phase.PATH)
        chaseRadius = input.getDoubleOr("ChaseRadius", MonsterRules.CHASE_RADIUS_START)
        meleeCooldown = input.getIntOr("MeleeCooldown", 0)
        buildingCooldown = input.getIntOr("BuildingCooldown", stats.buildingIntervalTicks)
        ticksSinceExchange = input.getIntOr("TicksSinceExchange", 0)
        radiusClock = input.getIntOr("RadiusClock", 0)
        coreTimer = input.getIntOr("CoreTimer", MonsterRules.CORE_RETARGET_TICKS)
        stuckTicks = input.getIntOr("StuckTicks", 0)
        stuckX = input.getDoubleOr("StuckX", mob.x)
        stuckZ = input.getDoubleOr("StuckZ", mob.z)
        stuckReady = input.getBooleanOr("StuckReady", false)
        corePos = if (input.getBooleanOr("HasCore", false)) {
            BlockPos(
                input.getIntOr("CoreX", 0),
                input.getIntOr("CoreY", 0),
                input.getIntOr("CoreZ", 0),
            )
        } else {
            null
        }
        focusId = readId(input, "Focus")
        counterSourceId = readId(input, "CounterSource")
        SummonedGear.applyStats(mob, stats, fill = false)
    }

    private fun tickSeek(level: ServerLevel) {
        val core = refreshCore(level)
        if (core != null) {
            if (phase != Phase.PATH) {
                phase = Phase.PATH
                focusId = null
                counterSourceId = null
                mob.setTarget(null)
                ticksSinceExchange = 0
                radiusClock = 0
                stuckReady = false
            }
            siege(core)
            return
        }
        val monster = preferredMonster(level)
        if (monster != null) {
            if (phase != Phase.CHASE || focusId != monster.living().uuid) enterChase(monster.living())
            tickChase(level)
            return
        }
        val player = preferredPlayer(level)
        if (player != null) {
            if (phase != Phase.CHASE || focusId != player.uuid) enterChase(player)
            tickChase(level)
            return
        }
        if (phase != Phase.PATH) {
            enterPath()
            return
        }
        val distant = chooseCore(level, withinRadius = false)
        if (distant != null) {
            siege(distant)
            return
        }
        recoverRadius()
        stuckReady = false
        mob.navigation.stop()
    }

    private fun siege(core: SiteCoreBlockEntity) {
        recoverRadius()
        tickStuck(core)
        val center = Vec3.atCenterOf(core.blockPos)
        val coreInRange = distanceToCore(core.blockPos) <= stats.attackRange
        if (coreInRange) {
            mob.navigation.stop()
            mob.lookControl.setLookAt(center)
        } else if (mob.tickCount % MonsterRules.REPATH_TICKS == 0) {
            mob.navigation.moveTo(center.x, center.y, center.z, 1.0)
        }
        buildingCooldown--
        if (buildingCooldown <= 0) {
            val hit = nearestSiegeTarget() ?: return
            buildingCooldown = stats.buildingIntervalTicks
            mob.lookControl.setLookAt(Vec3.atCenterOf(hit.pos))
            swingAtBuilding()
            val name = Monsters.byId(unit.kindId)?.displayName ?: unit.kindId
            hit.core.damageFromMonster(unit.summonedTeam, stats.buildingDamage, hit.pos, name)
        }
    }

    private fun swingAtBuilding() {
        val world = mob.level() as? ServerLevel ?: return
        if (mob is IronGolem) {
            world.broadcastEntityEvent(mob, 4.toByte())
        } else {
            mob.swing(InteractionHand.MAIN_HAND)
        }
    }

    private fun nearestSiegeTarget(): SiegeHit? {
        val world = mob.level() as? ServerLevel ?: return null
        val range = stats.attackRange
        var best: SiegeHit? = null
        var bestDist = Double.POSITIVE_INFINITY
        fun consider(core: SiteCoreBlockEntity, pos: BlockPos) {
            if (core.isRemoved || core.health <= 0 || core.team == unit.summonedTeam) return
            val dist = mob.position().distanceTo(Vec3.atCenterOf(pos))
            if (dist <= range && dist < bestDist) {
                bestDist = dist
                best = SiegeHit(core, pos)
            }
        }
        for (core in SiteCores.all()) {
            if (core.level !== world) continue
            consider(core, core.blockPos)
            for (part in core.partPositions) consider(core, part)
        }
        val radius = kotlin.math.ceil(range).toInt() + 1
        val origin = mob.blockPosition()
        for (x in -radius..radius) {
            for (y in -radius..radius) {
                for (z in -radius..radius) {
                    val pos = origin.offset(x, y, z)
                    val state = world.getBlockState(pos)
                    if (state.block !is SiteStructureBlock && state.block !is SitePartBlock) continue
                    val owner = BuildingMining.resolve(world, pos, state) ?: continue
                    consider(owner, pos)
                }
            }
        }
        return best
    }

    private fun recoverRadius() {
        radiusClock++
        if (radiusClock >= MonsterRules.SECOND_TICKS) {
            radiusClock = 0
            chaseRadius = min(MonsterRules.CHASE_RADIUS_MAX, chaseRadius + MonsterRules.RADIUS_RECOVER_PER_SECOND)
        }
    }

    private fun tickChase(level: ServerLevel) {
        val target = living(level, focusId)
        if (target == null || !inReach(target)) {
            enterPath()
            return
        }
        tryAttack(target)
        if (ticksSinceExchange >= MonsterRules.CHASE_LOSE_INTEREST_TICKS) {
            radiusClock++
            if (radiusClock >= MonsterRules.SECOND_TICKS) {
                radiusClock = 0
                chaseRadius = (chaseRadius - MonsterRules.RADIUS_SHRINK_PER_SECOND).coerceAtLeast(0.0)
            }
        } else {
            radiusClock = 0
        }
        if (!inReach(target)) enterPath()
    }

    private fun tickCounter(level: ServerLevel) {
        val enemies = enemiesInRadius(level)
        var focus = living(level, focusId)
        if (focus == null || !inReach(focus)) {
            focus = nearestEnemy(enemies)
            focusId = focus?.uuid
        }
        if (focus != null) tryAttack(focus)
        val source = living(level, counterSourceId)
        val sourceGone = source == null || !inReach(source)
        val still = enemiesInRadius(level)
        if (ticksSinceExchange >= MonsterRules.COUNTER_END_TICKS || (sourceGone && still.isEmpty())) {
            enterPath()
        }
    }

    private fun enterChase(target: LivingEntity) {
        phase = Phase.CHASE
        focusId = target.uuid
        counterSourceId = null
        ticksSinceExchange = 0
        radiusClock = 0
        stuckReady = false
        mob.setTarget(target)
    }

    private fun enterPath() {
        phase = Phase.PATH
        focusId = null
        counterSourceId = null
        ticksSinceExchange = 0
        radiusClock = 0
        stuckReady = false
        mob.setTarget(null)
        mob.navigation.stop()
    }

    private fun tryAttack(target: LivingEntity) {
        mob.setTarget(target)
    }

    fun shoot(level: ServerLevel, target: LivingEntity) {
        val arrow = SummonArrow(ModMonsters.SUMMON_ARROW, level)
        arrow.attackDamage = stats.playerAttack.toFloat()
        arrow.ownerTeam = unit.summonedTeam
        arrow.owner = mob
        val start = mob.eyePosition
        arrow.snapTo(start.x, start.y, start.z, mob.yRot, mob.xRot)
        val dx = target.x - mob.x
        val dz = target.z - mob.z
        val horizontal = sqrt(dx * dx + dz * dz)
        val dy = target.getY(0.333) - start.y
        val spread = (14 - level.difficulty.id * 4).coerceAtLeast(0).toFloat()
        arrow.shoot(dx, dy + horizontal * 0.2, dz, MonsterRules.SKELETON_BOW_SPEED, spread)
        level.addFreshEntity(arrow)
        val pitch = 1f / (mob.random.nextFloat() * 0.4f + 0.8f)
        mob.playSound(SoundEvents.SKELETON_SHOOT, 1f, pitch)
    }

    private fun tickStuck(core: SiteCoreBlockEntity) {
        if (!stuckReady) {
            stuckX = mob.x
            stuckZ = mob.z
            stuckReady = true
            stuckTicks = 0
            return
        }
        stuckTicks++
        if (stuckTicks < MonsterRules.STUCK_TICKS) return
        val moved = hypot(mob.x - stuckX, mob.z - stuckZ)
        stuckX = mob.x
        stuckZ = mob.z
        stuckTicks = 0
        if (moved < MonsterRules.STUCK_HORIZONTAL) {
            val center = Vec3.atCenterOf(core.blockPos)
            mob.navigation.stop()
            mob.navigation.moveTo(center.x, center.y, center.z, 1.0)
        }
    }

    private fun chooseCore(level: ServerLevel, withinRadius: Boolean): SiteCoreBlockEntity? {
        val choices = ArrayList<CoreChoice>()
        for (core in SiteCores.all()) {
            if (core.level !== level || !isHostileCore(core)) continue
            val distance = distanceToCore(core.blockPos)
            if (withinRadius && distance > chaseRadius) continue
            choices.add(CoreChoice(core, distance))
        }
        return choices.minWithOrNull(
            compareBy<CoreChoice> { (it.distance * 1000.0).toLong() }
                .thenBy { it.core.health }
                .thenBy { if (it.core.buildingId == Buildings.BASE.id) 0 else 1 }
                .thenBy { it.core.blockPos.x }
                .thenBy { it.core.blockPos.z }
                .thenBy { it.core.blockPos.y },
        )?.core
    }

    private fun currentCore(level: ServerLevel): SiteCoreBlockEntity? {
        val pos = corePos ?: return null
        val core = SiteCores.all().firstOrNull { it.level === level && it.blockPos == pos } ?: return null
        return core.takeIf { isHostileCore(it) && distanceToCore(it.blockPos) <= chaseRadius }
    }

    private fun enemiesInRadius(level: ServerLevel): List<LivingEntity> {
        val found = ArrayList<LivingEntity>()
        for (player in level.players()) {
            if (isHostilePlayer(player) && distanceToEntity(player) <= chaseRadius) found.add(player)
        }
        for (other in SummonedUnits.all()) {
            if (other === unit || other.living().level() !== level) continue
            if (unit.summonedTeam != other.summonedTeam && distanceToEntity(other.living()) <= chaseRadius) {
                found.add(other.living())
            }
        }
        return found
    }

    private fun refreshCore(level: ServerLevel): SiteCoreBlockEntity? {
        coreTimer++
        var core = currentCore(level)
        if (core == null || coreTimer >= MonsterRules.CORE_RETARGET_TICKS) {
            coreTimer = 0
            core = chooseCore(level, withinRadius = true)
            corePos = core?.blockPos
        }
        return core
    }

    private fun preferredMonster(level: ServerLevel): SummonedUnit? {
        val current = living(level, focusId) as? SummonedUnit
        if (current != null && current !== unit && inReach(current.living())) return current
        return monstersInRadius(level).minWithOrNull(
            compareBy<SummonedUnit> { distanceToEntity(it.living()) }.thenBy { it.living().id },
        )
    }

    private fun preferredPlayer(level: ServerLevel): ServerPlayer? {
        val current = living(level, focusId) as? ServerPlayer
        if (current != null && inReach(current)) return current
        return playersInRadius(level).minWithOrNull(
            compareBy<ServerPlayer> { distanceToEntity(it) }.thenBy { it.id },
        )
    }

    private fun monstersInRadius(level: ServerLevel): List<SummonedUnit> = SummonedUnits.all().filter { other ->
        other !== unit &&
            other.living().level() === level &&
            unit.summonedTeam != other.summonedTeam &&
            distanceToEntity(other.living()) <= chaseRadius
    }

    private fun playersInRadius(level: ServerLevel): List<ServerPlayer> = level.players().filter { player ->
        isHostilePlayer(player) && distanceToEntity(player) <= chaseRadius
    }

    private fun nearestEnemy(enemies: List<LivingEntity>): LivingEntity? = enemies.minWithOrNull(
        compareBy<LivingEntity> { if (it is ServerPlayer) 1 else 0 }
            .thenBy { distanceToEntity(it) }
            .thenBy { it.id },
    )

    private fun living(level: ServerLevel, id: UUID?): LivingEntity? {
        val entity = id?.let { level.getEntity(it) } as? LivingEntity ?: return null
        if (!entity.isAlive || entity.isRemoved) return null
        return entity
    }

    private fun inReach(entity: LivingEntity): Boolean {
        val hostile = when (entity) {
            is ServerPlayer -> isHostilePlayer(entity)
            is SummonedUnit -> unit !== entity && unit.summonedTeam != entity.summonedTeam
            else -> false
        }
        return hostile && distanceToEntity(entity) <= chaseRadius
    }

    private fun isHostilePlayer(player: ServerPlayer): Boolean {
        if (!player.isAlive || player.isSpectator || player.isCreative || player.isRemoved) return false
        return unit.summonedTeam != (player.team?.name ?: "")
    }

    private fun isHostileCore(core: SiteCoreBlockEntity): Boolean {
        if (core.isRemoved || core.buildingId.isEmpty()) return false
        return unit.summonedTeam != core.team
    }

    private fun distanceToEntity(entity: Entity): Double = mob.position().distanceTo(entity.position())

    private fun distanceToCore(pos: BlockPos): Double = mob.position().distanceTo(Vec3.atCenterOf(pos))

    private val stats: MonsterDefinition
        get() = Monsters.byId(unit.kindId) ?: Monsters.ZOMBIE

    private enum class Phase {
        PATH,
        CHASE,
        COUNTER,
    }

    private data class CoreChoice(val core: SiteCoreBlockEntity, val distance: Double)

    private data class SiegeHit(val core: SiteCoreBlockEntity, val pos: BlockPos)

    companion object {
        private fun readId(input: net.minecraft.world.level.storage.ValueInput, key: String): UUID? {
            val text = input.getStringOr(key, "")
            if (text.isEmpty()) return null
            return runCatching { UUID.fromString(text) }.getOrNull()
        }
    }
}
