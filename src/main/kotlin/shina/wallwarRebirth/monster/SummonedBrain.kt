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
import net.minecraft.world.phys.Vec3
import shina.wallwarRebirth.building.BuildingMining
import shina.wallwarRebirth.building.Relay
import shina.wallwarRebirth.rules.MatchRules
import shina.wallwarRebirth.world.SurfaceOres
import shina.wallwarRebirth.building.Buildings
import shina.wallwarRebirth.building.FarmlandWork
import shina.wallwarRebirth.building.SiteCoreBlockEntity
import shina.wallwarRebirth.targeting.CounterTargeting
import shina.wallwarRebirth.targeting.HoldTargeting
import shina.wallwarRebirth.targeting.Targeting
import shina.wallwarRebirth.targeting.SpiderTargeting
import shina.wallwarRebirth.targeting.SummonDefaultTargeting
import shina.wallwarRebirth.targeting.TargetWorlds
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
    private var minerCargo = 0

    fun carriedOre(): Int = minerCargo
    private var minerTicks = 0
    private val minerPile = ArrayList<Long>()
    private var depositPos: BlockPos? = null
    private var summonPos: BlockPos? = null
    private var homePos: BlockPos? = null
    private var returningHome = false
    private var patrolTicks = 0
    private var fuseLive = false
    private var fuseBuilding: SiteCoreBlockEntity? = null

    fun called(): Boolean = summonPos != null

    fun callDestination(): BlockPos? = summonPos

    fun finishCall() {
        summonPos = null
    }

    fun distanceToCenter(pos: BlockPos): Double = distanceToCore(pos)

    fun acceptCall(pos: BlockPos) {
        summonPos = pos.immutable()
        depositPos = null
        phase = Phase.PATH
        focusId = null
        counterSourceId = null
        ticksSinceExchange = 0
        mob.setTarget(null)
        mob.navigation.stop()
    }

    internal var ignoreDefensiveBuildings: Boolean = false

    fun bindHome(pos: BlockPos?) {
        homePos = pos?.immutable()
    }

    fun homePos(): BlockPos? = homePos

    fun homeSite(level: ServerLevel): SiteCoreBlockEntity? {
        val pos = homePos ?: return null
        val site = level.getBlockEntity(pos) as? SiteCoreBlockEntity ?: return null
        if (site.isRemoved || site.buildingId != Buildings.PILLAGER.id || site.health <= 0) return null
        return site
    }

    fun rebindHome(from: BlockPos, to: BlockPos) {
        if (homePos == from) homePos = to.immutable()
    }

    fun reassertIfHome(pos: BlockPos) {
        if (homePos != pos) return
        enterPath()
        returningHome = false
    }

    fun holdingActive(level: ServerLevel): Boolean {
        val site = homeSite(level) ?: return false
        return site.holding
    }

    fun siegingTeam(team: String): Boolean {
        if (team.isEmpty() || phase != Phase.PATH) return false
        val hit = nearestSiegeTarget() ?: return false
        return hit.core.team == team
    }

    internal fun tickCombat(level: ServerLevel) {
        if (!prepare(level)) return
        if (stats.buildingsOnly && !holdingActive(level) && (phase == Phase.COUNTER || phase == Phase.CHASE)) {
            enterPath()
        }
        if (holdingActive(level)) {
            tickHold(level)
            return
        }
        returningHome = false
        when (phase) {
            Phase.COUNTER -> tickCounter(level)
            else -> tickSeek(level)
        }
    }

    internal fun tickMinerWork(level: ServerLevel) {
        if (!prepare(level)) return
        if (phase == Phase.COUNTER) tickCounter(level) else tickMiner(level)
    }

    internal fun armCreeper() {
        (mob as? SummonedCreeper)?.arm(fuseLive, fuseBuilding)
    }

    private fun prepare(level: ServerLevel): Boolean {
        if (!mob.isAlive) return false
        fuseLive = false
        fuseBuilding = null
        SummonedUnits.add(unit)
        if (meleeCooldown > 0) meleeCooldown--
        ticksSinceExchange++
        return true
    }

    fun onDamaged(level: ServerLevel, source: DamageSource) {
        if (stats.buildingsOnly && !holdingActive(level)) return
        if (holdingActive(level)) {
            onHoldDamaged(level, source)
            return
        }
        val attacker = source.entity as? LivingEntity
        val chosen = CounterTargeting.select(
            TargetWorlds.world,
            level,
            mob.position(),
            unit.summonedTeam,
            chaseRadius,
            mob,
            attacker,
        )
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
        output.putInt("MinerCargo", minerCargo)
        output.putInt("MinerTicks", minerTicks)
        writePos(output, "Deposit", depositPos)
        writePos(output, "Summon", summonPos)
        writePos(output, "Home", homePos)
    }

    fun load(input: net.minecraft.world.level.storage.ValueInput) {
        unit.kindId = input.getStringOr("Kind", unit.kindId)
        if (unit.kindId != SummonedVex.VEX_ID && Monsters.byId(unit.kindId) == null) unit.kindId = Monsters.ZOMBIE.id
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
        minerCargo = input.getIntOr("MinerCargo", 0)
        minerTicks = input.getIntOr("MinerTicks", 0)
        depositPos = readPos(input, "Deposit")
        summonPos = readPos(input, "Summon")
        homePos = readPos(input, "Home")
        minerPile.clear()
        if (unit.kindId != SummonedVex.VEX_ID) SummonedGear.applyStats(mob, stats, fill = false)
    }

    private fun tickMiner(level: ServerLevel) {
        recoverRadius()
        mob.setTarget(null)
        if (summonPos != null) {
            tickSummon(level)
            return
        }
        if (minerCargo >= MatchRules.MINER_CARGO) {
            deliver(level)
            return
        }
        minerPile.removeAll { !SurfaceOres.isOre(level.getBlockState(BlockPos.of(it)).block) }
        if (minerPile.isEmpty()) {
            if (mob.tickCount % MatchRules.TICKS_PER_SECOND != 0) {
                mob.navigation.stop()
                return
            }
            minerPile.addAll(SurfaceOres.nearestPile(level, mob.position()).map { it.asLong() })
        }
        val target = closestOre()
        if (target == null) {
            mob.navigation.stop()
            return
        }
        val pos = BlockPos.of(target)
        if (distanceToCore(pos) <= stats.attackRange) {
            mob.navigation.stop()
            mob.lookControl.setLookAt(Vec3.atCenterOf(pos))
            minerTicks++
            if (minerTicks < MatchRules.MINER_MINE_TICKS) return
            level.destroyBlock(pos, false)
            minerPile.remove(target)
            minerCargo++
            minerTicks = 0
            mob.swing(InteractionHand.MAIN_HAND)
        } else {
            minerTicks = 0
            if (mob.tickCount % MonsterRules.REPATH_TICKS == 0) {
                val center = Vec3.atCenterOf(pos)
                mob.navigation.moveTo(center.x, center.y, center.z, 1.0)
            }
        }
    }

    private fun tickSummon(level: ServerLevel) {
        val pos = summonPos ?: return
        val site = liveSite(level, pos, Buildings.RELAY.id)
        if (site == null) {
            summonPos = null
            if (minerCargo >= MatchRules.MINER_CARGO) deliver(level)
            return
        }
        if (distanceToCore(site.blockPos) <= MatchRules.RELAY_INTERACT_RANGE) {
            mob.navigation.stop()
            if (minerCargo > 0 && !handIn(level)) return
            summonPos = null
            minerPile.clear()
            return
        }
        minerTicks = 0
        if (mob.tickCount % MonsterRules.REPATH_TICKS == 0) {
            val center = Vec3.atCenterOf(site.blockPos)
            mob.navigation.moveTo(center.x, center.y, center.z, 1.0)
        }
    }

    private fun deliver(level: ServerLevel) {
        val locked = depositPos?.let { liveSite(level, it, null) }
        val dest = locked ?: pickDeposit(level).also { depositPos = it?.blockPos?.immutable() }
        if (dest == null) {
            mob.navigation.stop()
            return
        }
        val reach = if (dest.buildingId == Buildings.RELAY.id) MatchRules.RELAY_INTERACT_RANGE else stats.attackRange
        if (distanceToCore(dest.blockPos) <= reach) {
            mob.navigation.stop()
            handIn(level)
            return
        }
        minerTicks = 0
        if (mob.tickCount % MonsterRules.REPATH_TICKS == 0) {
            val center = Vec3.atCenterOf(dest.blockPos)
            mob.navigation.moveTo(center.x, center.y, center.z, 1.0)
        }
    }

    private fun pickDeposit(level: ServerLevel): SiteCoreBlockEntity? {
        val base = Relay.teamBase(level, unit.summonedTeam) ?: return null
        val baseDist = mob.position().distanceTo(Vec3.atCenterOf(base.blockPos))
        val closer = SiteCores.all().filter {
            it.level === level &&
                !it.isRemoved &&
                it.health > 0 &&
                it.buildingId == Buildings.RELAY.id &&
                it.team == unit.summonedTeam &&
                baseDist - mob.position().distanceTo(Vec3.atCenterOf(it.blockPos)) > MatchRules.CANNON_TIE_DISTANCE
        }
        return closer.minWithOrNull(
            compareBy<SiteCoreBlockEntity> {
                Relay.bucket(mob.position().distanceTo(Vec3.atCenterOf(it.blockPos)))
            }
                .thenBy { it.blockPos.x }
                .thenBy { it.blockPos.z }
                .thenBy { it.blockPos.y },
        ) ?: base
    }

    private fun handIn(level: ServerLevel): Boolean {
        val base = Relay.teamBase(level, unit.summonedTeam) ?: return false
        repeat(minerCargo / MatchRules.MINER_ORES_PER_CYCLE) { base.produceCycle() }
        minerCargo = 0
        minerTicks = 0
        minerPile.clear()
        depositPos = null
        return true
    }

    private fun liveSite(level: ServerLevel, pos: BlockPos, requiredId: String?): SiteCoreBlockEntity? {
        val site = level.getBlockEntity(pos) as? SiteCoreBlockEntity ?: return null
        if (site.isRemoved || site.health <= 0 || site.team != unit.summonedTeam) return null
        if (requiredId != null && site.buildingId != requiredId) return null
        if (requiredId == null && site.buildingId != Buildings.BASE.id && site.buildingId != Buildings.RELAY.id) return null
        return site
    }

    private fun writePos(output: net.minecraft.world.level.storage.ValueOutput, name: String, pos: BlockPos?) {
        output.putBoolean("Has$name", pos != null)
        if (pos == null) return
        output.putInt("${name}X", pos.x)
        output.putInt("${name}Y", pos.y)
        output.putInt("${name}Z", pos.z)
    }

    private fun readPos(input: net.minecraft.world.level.storage.ValueInput, name: String): BlockPos? {
        if (!input.getBooleanOr("Has$name", false)) return null
        return BlockPos(input.getIntOr("${name}X", 0), input.getIntOr("${name}Y", 0), input.getIntOr("${name}Z", 0))
    }

    private fun closestOre(): Long? {
        var best: Long? = null
        var bestDist = Double.POSITIVE_INFINITY
        for (key in minerPile) {
            val dist = distanceToCore(BlockPos.of(key))
            if (dist < bestDist) {
                bestDist = dist
                best = key
            }
        }
        return best
    }

    private fun tickHold(level: ServerLevel) {
        val site = homeSite(level)
        if (site == null) {
            returningHome = false
            when (phase) {
                Phase.COUNTER -> tickCounter(level)
                else -> tickSeek(level)
            }
            return
        }
        val origin = Vec3.atCenterOf(site.blockPos)
        val range = MatchRules.PILLAGER_HOLD_RANGE
        val distance = Targeting.distanceToBox(origin, mob.boundingBox)
        if (distance > range) {
            returningHome = true
            phase = Phase.PATH
            focusId = null
            counterSourceId = null
            mob.setTarget(null)
            if (mob.tickCount % MonsterRules.REPATH_TICKS == 0) {
                mob.navigation.moveTo(origin.x, origin.y, origin.z, 1.0)
            }
            return
        }
        returningHome = false
        if (phase == Phase.COUNTER) {
            tickHoldCounter(level, origin, range)
            return
        }
        val target = HoldTargeting.select(TargetWorlds.world, level, origin, unit.summonedTeam, range, mob)
        if (target == null) {
            patrolHold(origin, range)
            return
        }
        enterChase(target)
        tryAttack(target)
        if (Targeting.distanceToBox(origin, target.boundingBox) > range || !target.isAlive) {
            enterPath()
            patrolHold(origin, range)
        }
    }

    private fun tickHoldCounter(level: ServerLevel, origin: Vec3, range: Double) {
        val focus = living(level, focusId)
        if (focus == null || !focus.isAlive || Targeting.distanceToBox(origin, focus.boundingBox) > range) {
            enterPath()
            return
        }
        if (focus is ServerPlayer && !Targeting.canTargetPlayer(focus)) {
            enterPath()
            return
        }
        tryAttack(focus)
        if (ticksSinceExchange >= MonsterRules.COUNTER_END_TICKS) enterPath()
    }

    private fun onHoldDamaged(level: ServerLevel, source: DamageSource) {
        val site = homeSite(level) ?: return
        val origin = Vec3.atCenterOf(site.blockPos)
        val range = MatchRules.PILLAGER_HOLD_RANGE
        val attacker = source.entity as? LivingEntity ?: return
        if (Targeting.distanceToBox(origin, attacker.boundingBox) > range) return
        val chosen = HoldTargeting.select(TargetWorlds.world, level, origin, unit.summonedTeam, range, mob) ?: attacker
        if (chosen is ServerPlayer && !Targeting.canTargetPlayer(chosen)) return
        if (Targeting.distanceToBox(origin, chosen.boundingBox) > range) return
        phase = Phase.COUNTER
        counterSourceId = attacker.uuid
        focusId = chosen.uuid
        ticksSinceExchange = 0
        stuckReady = false
        mob.setTarget(chosen)
    }

    private fun patrolHold(origin: Vec3, range: Double) {
        if (phase != Phase.PATH) enterPath()
        mob.setTarget(null)
        patrolTicks++
        if (patrolTicks < MonsterRules.REPATH_TICKS && mob.navigation.isInProgress) return
        patrolTicks = 0
        val random = mob.random
        val radius = (range * 0.6).coerceAtLeast(2.0)
        val angle = random.nextDouble() * Math.PI * 2
        val dist = random.nextDouble() * radius
        val x = origin.x + Math.cos(angle) * dist
        val z = origin.z + Math.sin(angle) * dist
        mob.navigation.moveTo(x, origin.y, z, 1.0)
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
        val monster = if (stats.buildingsOnly) null else preferredMonster(level)
        if (monster != null) {
            if (phase != Phase.CHASE || focusId != monster.living().uuid) enterChase(monster.living())
            tickChase(level)
            return
        }
        val player = if (stats.buildingsOnly) null else preferredPlayer(level)
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
        if (stats.explodes) {
            val hit = nearestSiegeTarget()
            if (hit != null) {
                mob.navigation.stop()
                mob.lookControl.setLookAt(Vec3.atCenterOf(hit.pos))
                fuseLive = true
                fuseBuilding = hit.core
            } else if (mob.tickCount % MonsterRules.REPATH_TICKS == 0) {
                mob.navigation.moveTo(center.x, center.y, center.z, 1.0)
            }
            return
        }
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
            hit.core.damageFromMonster(mob, unit.summonedTeam, stats.buildingDamage, hit.pos, name)
        }
    }

    internal var buildingSwing: (ServerLevel) -> Unit = { _ ->
        mob.swing(InteractionHand.MAIN_HAND)
    }

    private fun swingAtBuilding() {
        val world = mob.level() as? ServerLevel ?: return
        buildingSwing(world)
    }

    private fun nearestSiegeTarget(): SiegeHit? {
        val world = mob.level() as? ServerLevel ?: return null
        val range = stats.attackRange
        var best: SiegeHit? = null
        var bestDist = Double.POSITIVE_INFINITY
        fun consider(core: SiteCoreBlockEntity, pos: BlockPos) {
            if (FarmlandWork.isPart(core, pos)) return
            if (core.isRemoved || core.health <= 0 || core.team == unit.summonedTeam || skipsDefensive(core)) return
            val dist = mob.position().distanceTo(Vec3.atCenterOf(pos))
            if (dist <= range && dist < bestDist) {
                bestDist = dist
                best = SiegeHit(core, pos)
            }
        }
        for (core in SiteCores.all()) {
            if (core.level !== world) continue
            consider(core, core.blockPos)
            for (part in core.partPositions) {
                if (FarmlandWork.isPart(core, part)) continue
                consider(core, part)
            }
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
        if (stats.explodes && mob.distanceTo(target) <= stats.attackRange) fuseLive = true
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
        if (focus != null) {
            tryAttack(focus)
            if (stats.explodes && mob.distanceTo(focus) <= stats.attackRange) fuseLive = true
        }
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
        val origin = mob.position()
        val radius = if (withinRadius) chaseRadius else null
        val picked = if (ignoreDefensiveBuildings) {
            SpiderTargeting.chooseCore(TargetWorlds.world, level, origin, unit.summonedTeam, radius)
        } else {
            SummonDefaultTargeting.chooseCore(TargetWorlds.world, level, origin, unit.summonedTeam, radius, skipDefensive = false)
        } ?: return null
        return SiteCores.all().firstOrNull { it.level === level && it.blockPos == picked.pos && !it.isRemoved }
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
        return SummonDefaultTargeting.nearestSummon(
            TargetWorlds.world,
            level,
            mob.position(),
            unit.summonedTeam,
            chaseRadius,
            mob,
        ) as? SummonedUnit
    }

    private fun preferredPlayer(level: ServerLevel): ServerPlayer? {
        val current = living(level, focusId) as? ServerPlayer
        if (current != null && inReach(current)) return current
        return SummonDefaultTargeting.nearestPlayer(
            TargetWorlds.world,
            level,
            mob.position(),
            unit.summonedTeam,
            chaseRadius,
        ) as? ServerPlayer
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
        if (!player.isAlive || player.isRemoved || !Targeting.canTargetPlayer(player)) return false
        return shina.wallwarRebirth.contract.WallwarTeams.hostile(unit.summonedTeam, player.team?.name ?: "")
    }

    private fun isHostileCore(core: SiteCoreBlockEntity): Boolean {
        if (core.isRemoved || core.buildingId.isEmpty() || skipsDefensive(core)) return false
        return unit.summonedTeam != core.team
    }

    private fun skipsDefensive(core: SiteCoreBlockEntity): Boolean {
        if (!ignoreDefensiveBuildings) return false
        return Buildings.byId(core.buildingId)?.defensive == true
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
