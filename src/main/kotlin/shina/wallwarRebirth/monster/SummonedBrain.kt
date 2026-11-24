package shina.wallwarRebirth.monster

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.InteractionHand
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.phys.Vec3
import shina.wallwarRebirth.building.BuildingMining
import shina.wallwarRebirth.building.BuildingStyles
import shina.wallwarRebirth.building.Relay
import shina.wallwarRebirth.match.Match
import shina.wallwarRebirth.rules.MatchRules
import shina.wallwarRebirth.world.SurfaceOres
import shina.wallwarRebirth.building.Buildings
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
    var chaseRadius: Double = stats.chaseRadiusStart
    private var phase = Phase.PATH
    private var focusId: UUID? = null
    private var counterSourceId: UUID? = null
    private var corePos: BlockPos? = null
    private var meleeCooldown = 0
    private var buildingCooldown = stats.buildingIntervalTicks
    private var ticksSinceExchange = 0
    private var radiusClock = 0
    private var coreTimer = MonsterRules.CORE_RETARGET_TICKS
    private var distantTimer = 0
    private var pathRetryTick = 0
    private var siegeScanTick = Int.MIN_VALUE
    private var siegeScanHit: SiegeHit? = null
    private var fangId: UUID? = null
    private var stuckTicks = 0
    private var stuckX = 0.0
    private var stuckZ = 0.0
    private var stuckReady = false
    private val unreachableCores = HashSet<BlockPos>()
    private val escape = SummonedEscape.Memory()
    private var siegeTargetPos: BlockPos? = null
    private var siegeFailTicks = 0
    private var siegeFailX = 0.0
    private var siegeFailZ = 0.0
    private var minerCargo = 0

    fun carriedOre(): Int = minerCargo
    private var minerTicks = 0
    private val minerPile = ArrayList<Long>()
    private val minerIgnored = HashSet<Long>()
    private var minerIdleTicks = 0
    private var minerIdleX = 0.0
    private var minerIdleZ = 0.0
    private var minerIdleReady = false
    private var minerIdleOre: Long? = null
    private var depositPos: BlockPos? = null
    private var depositNudge = 0
    private var depositWatchTicks = 0
    private var depositWatchX = 0.0
    private var depositWatchZ = 0.0
    private var depositWatchReady = false
    private var summonPos: BlockPos? = null
    private var homePos: BlockPos? = null
    private var commandMode = CommandMode.EXPEDITION
    private var followOwnerId: UUID? = null
    private var returningToOwner = false
    private var holdEpochSeen = 0
    private var returningHome = false
    private var patrolTicks = 0
    private var fuseLive = false
    private var fuseBuilding: SiteCoreBlockEntity? = null

    fun following(): Boolean = commandMode == CommandMode.FOLLOW

    fun follows(player: ServerPlayer): Boolean =
        commandMode == CommandMode.FOLLOW && followOwnerId == player.uuid

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
        val world = mob.level() as? ServerLevel
        holdEpochSeen = if (world != null) homeSite(world)?.holdEpoch ?: 0 else 0
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
        if (commandMode == CommandMode.FOLLOW) return
        enterPath()
        returningHome = false
    }

    fun holdingActive(level: ServerLevel): Boolean {
        if (commandMode == CommandMode.FOLLOW) return false
        val site = homeSite(level) ?: return false
        return site.holding && holdEpochSeen == site.holdEpoch
    }

    fun siegingTeam(team: String): Boolean {
        if (team.isEmpty() || phase != Phase.PATH) return false
        val hit = nearestSiegeTarget() ?: return false
        return hit.core.team == team
    }

    internal fun tickCombat(level: ServerLevel) {
        if (!prepare(level)) return
        refreshCommand(level)
        if (commandMode == CommandMode.FOLLOW) {
            tickFollow(level)
            return
        }
        if (stats.buildingsOnly && !holdingActive(level) && (phase == Phase.COUNTER || phase == Phase.CHASE)) {
            enterPath()
        }
        if (aboveHalfCalm() && (phase == Phase.COUNTER || phase == Phase.CHASE)) enterPath()
        if (holdingActive(level)) {
            tickHold(level)
            return
        }
        returningHome = false
        if (stats.fangs && kiteIfThreatened(level)) return
        when (phase) {
            Phase.COUNTER -> tickCounter(level)
            else -> tickSeek(level)
        }
    }

    internal fun tickMinerWork(level: ServerLevel) {
        if (!prepare(level)) return
        refreshCommand(level)
        if (commandMode == CommandMode.FOLLOW) {
            tickFollow(level)
            return
        }
        if (phase == Phase.COUNTER) tickCounter(level) else tickMiner(level)
    }

    internal fun tickVexCombat(level: ServerLevel) {
        if (!prepare(level)) return
        refreshCommand(level)
        if (commandMode == CommandMode.FOLLOW) {
            tickFollow(level)
            return
        }
        returningHome = false
        if (phase == Phase.COUNTER) {
            tickCounter(level)
            return
        }
        val ordered = ownerEvokerBuilding(level)
        if (ordered != null) {
            if (phase != Phase.PATH) enterPath()
            corePos = ordered.blockPos
            flySiege(ordered)
            return
        }
        tickSeek(level)
    }

    private fun ownerEvokerBuilding(level: ServerLevel): SiteCoreBlockEntity? {
        val owner = (mob as? net.minecraft.world.entity.monster.Vex)?.owner as? SummonedEvoker ?: return null
        if (!owner.isAlive || owner.isRemoved || owner.level() !== level) return null
        return owner.mind.currentCoreIntent(level)
    }

    private fun kiteIfThreatened(level: ServerLevel): Boolean {
        val threat = nearestFangThreat(level) ?: return false
        refreshCore(level)
        if (phase != Phase.CHASE || focusId != threat.uuid) enterChase(threat)
        else mob.setTarget(threat)
        fleeFrom(threat)
        (unit as? SummonedEvoker)?.offerFangTarget(threat)
        return true
    }

    private fun fleeFrom(threat: LivingEntity, anchor: Vec3? = null, leash: Double? = null) {
        val dx = mob.x - threat.x
        val dz = mob.z - threat.z
        val len = hypot(dx, dz).coerceAtLeast(0.01)
        val step = 6.0
        var x = mob.x + dx / len * step
        var z = mob.z + dz / len * step
        if (anchor != null && leash != null) {
            val awayX = x - anchor.x
            val awayZ = z - anchor.z
            val away = hypot(awayX, awayZ)
            val limit = (leash - 1.0).coerceAtLeast(1.0)
            if (away > limit) {
                x = anchor.x + awayX / away * limit
                z = anchor.z + awayZ / away * limit
            }
        }
        mob.navigation.stop()
        mob.moveControl.setWantedPosition(x, mob.y, z, 1.0)
        mob.lookControl.setLookAt(threat)
    }

    private fun nearestFangThreat(level: ServerLevel): LivingEntity? {
        if (!stats.fangs) return null
        val range = MatchRules.EVOKER_FANG_RANGE
        val kept = living(level, fangId)
        if (kept != null && fangEnemy(kept) && distanceToEntity(kept) <= range && !livingScanDue()) return kept
        if (!livingScanDue()) {
            fangId = null
            return null
        }
        var best: LivingEntity? = null
        var bestDist = range
        fun consider(entity: LivingEntity) {
            val dist = distanceToEntity(entity)
            if (dist > range) return
            val chosen = best
            if (chosen == null || dist < bestDist || (dist == bestDist && entity.id < chosen.id)) {
                best = entity
                bestDist = dist
            }
        }
        for (actor in TargetWorlds.world.players(level)) {
            val player = actor.entity as? ServerPlayer ?: continue
            if (isHostilePlayer(player)) consider(player)
        }
        for (actor in TargetWorlds.world.summons(level)) {
            val living = actor.entity
            if (living === mob || !living.isAlive) continue
            if (unit.summonedTeam != actor.team) consider(living)
        }
        fangId = best?.uuid
        return best
    }

    private fun fangEnemy(entity: LivingEntity): Boolean = when (entity) {
        is ServerPlayer -> isHostilePlayer(entity)
        is SummonedUnit -> unit !== entity && unit.summonedTeam != entity.summonedTeam
        else -> false
    }

    private fun livingScanDue(): Boolean {
        val spread = mob.id and Int.MAX_VALUE
        return (mob.tickCount + spread) % MonsterRules.REPATH_TICKS == 0
    }

    internal fun currentCoreIntent(level: ServerLevel): SiteCoreBlockEntity? {
        val pos = corePos ?: return null
        val core = SiteCores.find(level, pos) ?: return null
        return core.takeIf { it.health > 0 && isHostileCore(it) }
    }

    internal fun tickVillagerCommand(level: ServerLevel): Boolean {
        if (!mob.isAlive) return true
        refreshCommand(level)
        if (commandMode != CommandMode.FOLLOW) return false
        if (!prepare(level)) return true
        tickFollow(level)
        return true
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
        if (unit.kindId != SummonedVex.VEX_ID) tickEscape(level)
        return true
    }

    private fun tickEscape(level: ServerLevel) {
        if (nearestSiegeTarget() != null && !mob.navigation.isInProgress) {
            SummonedEscape.tick(mob, level, null, false, escape)
            return
        }
        val goal = travelGoal(level)
        val trying = goal != null && mob.position().distanceTo(goal) > stats.attackRange + 0.5
        SummonedEscape.tick(mob, level, goal, trying, escape)
    }

    private fun travelGoal(level: ServerLevel): Vec3? {
        val navPos = mob.navigation.targetPos
        if (mob.navigation.isInProgress && navPos != null) return Vec3.atCenterOf(navPos)
        val focus = mob.target
        if (focus != null && focus.isAlive) return focus.position()
        val core = currentCoreIntent(level)
        if (core != null) return Vec3.atCenterOf(core.blockPos)
        return null
    }

    fun onDamaged(level: ServerLevel, source: DamageSource) {
        if (stats.buildingsOnly && !holdingActive(level)) return
        if (aboveHalfCalm()) return
        if (source.`is`(DamageTypes.THORNS)) return
        if (commandMode == CommandMode.FOLLOW && suppressesFollowCounter(level)) return
        if (holdingActive(level)) {
            onHoldDamaged(level, source)
            return
        }
        val attacker = source.entity as? LivingEntity
        if (attacker != null && shouldKeepCurrentTarget(level, attacker)) return
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
        if (unit.summonedTeam.isNotEmpty()) {
            output.putString("Team", unit.summonedTeam)
        }
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
        output.putString("CommandMode", commandMode.name)
        output.putString("FollowOwner", followOwnerId?.toString() ?: "")
        output.putBoolean("ReturningToOwner", returningToOwner)
        output.putInt("HoldEpochSeen", holdEpochSeen)
    }

    fun load(input: net.minecraft.world.level.storage.ValueInput) {
        unit.kindId = input.getStringOr("Kind", unit.kindId)
        if (Monsters.byId(unit.kindId) == null) unit.kindId = Monsters.ZOMBIE.id
        val storedTeam = input.getStringOr("Team", "")
        unit.summonedTeam = if (storedTeam.isEmpty()) "" else storedTeam
        phase = runCatching { Phase.valueOf(input.getStringOr("Phase", Phase.PATH.name)) }.getOrDefault(Phase.PATH)
        chaseRadius = input.getDoubleOr("ChaseRadius", stats.chaseRadiusStart)
        if (unit.kindId == SummonedVex.VEX_ID) chaseRadius = stats.chaseRadiusMax
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
        commandMode = runCatching {
            CommandMode.valueOf(input.getStringOr("CommandMode", CommandMode.EXPEDITION.name))
        }.getOrDefault(CommandMode.EXPEDITION)
        followOwnerId = readId(input, "FollowOwner")
        returningToOwner = input.getBooleanOr("ReturningToOwner", false)
        holdEpochSeen = input.getIntOr("HoldEpochSeen", 0)
        minerPile.clear()
        SummonedGear.applyStats(mob, stats, fill = false)
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
        minerPile.removeAll { !SurfaceOres.isMineableOre(level, BlockPos.of(it)) }
        minerIgnored.removeAll { !SurfaceOres.isOre(level.getBlockState(BlockPos.of(it)).block) }
        if (minerPile.isEmpty()) {
            if (mob.tickCount % MatchRules.TICKS_PER_SECOND != 0) {
                mob.navigation.stop()
                return
            }
            minerPile.addAll(SurfaceOres.nearestPile(level, mob.position(), minerIgnored).map { it.asLong() })
        }
        val target = closestOre()
        if (target == null) {
            mob.navigation.stop()
            return
        }
        val pos = BlockPos.of(target)
        if (distanceToCore(pos) <= MatchRules.MINER_INTERACT_RANGE) {
            resetMinerIdle()
            mob.navigation.stop()
            mob.lookControl.setLookAt(Vec3.atCenterOf(pos))
            minerTicks++
            if (minerTicks < MatchRules.MINER_MINE_TICKS) return
            if (level.destroyBlock(pos, false)) minerCargo++
            minerPile.remove(target)
            minerIgnored.remove(target)
            minerTicks = 0
            mob.swing(InteractionHand.MAIN_HAND)
        } else {
            minerTicks = 0
            if (giveUpIdleOre(target)) {
                minerIgnored.add(target)
                minerPile.remove(target)
                resetMinerIdle()
                mob.navigation.stop()
                return
            }
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
        if (distanceToCore(site.blockPos) <= MatchRules.MINER_INTERACT_RANGE) {
            mob.navigation.stop()
            resetDepositNudge()
            if (minerCargo > 0 && !handIn(level)) return
            summonPos = null
            minerPile.clear()
            return
        }
        minerTicks = 0
        approachDeposit(site.blockPos)
    }

    private fun deliver(level: ServerLevel) {
        val locked = depositPos?.let { liveSite(level, it, null) }
        val dest = locked ?: pickDeposit(level).also { depositPos = it?.blockPos?.immutable() }
        if (dest == null) {
            mob.navigation.stop()
            resetDepositNudge()
            return
        }
        if (distanceToCore(dest.blockPos) <= MatchRules.MINER_INTERACT_RANGE) {
            mob.navigation.stop()
            resetDepositNudge()
            handIn(level)
            return
        }
        minerTicks = 0
        approachDeposit(dest.blockPos)
    }

    private fun approachDeposit(dest: BlockPos) {
        if (!depositWatchReady) {
            depositWatchX = mob.x
            depositWatchZ = mob.z
            depositWatchReady = true
            depositWatchTicks = 0
        }
        depositWatchTicks++
        if (depositWatchTicks >= MatchRules.ESCAPE_STUCK_TICKS) {
            val moved = hypot(mob.x - depositWatchX, mob.z - depositWatchZ)
            depositWatchX = mob.x
            depositWatchZ = mob.z
            depositWatchTicks = 0
            depositNudge = if (moved < MatchRules.ESCAPE_STUCK_DISTANCE) depositNudge + 1 else 0
        }
        if (mob.tickCount % MonsterRules.REPATH_TICKS != 0 && mob.navigation.isInProgress) return
        val center = Vec3.atCenterOf(dest)
        val goal = when {
            depositNudge == 0 -> center
            depositNudge % 2 == 1 -> closerDepositStep(center)
            else -> sideDepositStep(center)
        }
        mob.navigation.moveTo(goal.x, goal.y, goal.z, 1.0)
    }

    private fun closerDepositStep(center: Vec3): Vec3 {
        val dx = center.x - mob.x
        val dz = center.z - mob.z
        val len = hypot(dx, dz).coerceAtLeast(0.01)
        val step = 1.5.coerceAtMost(len)
        return Vec3(mob.x + dx / len * step, mob.y, mob.z + dz / len * step)
    }

    private fun sideDepositStep(center: Vec3): Vec3 {
        val dx = center.x - mob.x
        val dz = center.z - mob.z
        val len = hypot(dx, dz).coerceAtLeast(0.01)
        val side = if (depositNudge % 4 == 2) 2.5 else -2.5
        return Vec3(
            mob.x + dx / len + -dz / len * side,
            mob.y,
            mob.z + dz / len + dx / len * side,
        )
    }

    private fun resetDepositNudge() {
        depositNudge = 0
        depositWatchTicks = 0
        depositWatchReady = false
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
        minerIgnored.clear()
        resetMinerIdle()
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
            if (key in minerIgnored) continue
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
        val origin = Vec3.atCenterOf(site.holdOrigin())
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
        val kept = living(level, focusId)
        val keptInRange = kept != null && Targeting.distanceToBox(origin, kept.boundingBox) <= range
        if (kept != null && phase == Phase.CHASE && keptInRange && !livingScanDue()) {
            holdAttack(kept, origin, range)
            return
        }
        if (!livingScanDue()) {
            if (phase == Phase.CHASE) enterPath()
            patrolHold(origin, range)
            return
        }
        val target = HoldTargeting.select(TargetWorlds.world, level, origin, unit.summonedTeam, range, mob)
        if (target == null) {
            patrolHold(origin, range)
            return
        }
        if (phase != Phase.CHASE || focusId != target.uuid) enterChase(target)
        holdAttack(target, origin, range)
        if (Targeting.distanceToBox(origin, target.boundingBox) > range || !target.isAlive) {
            enterPath()
            patrolHold(origin, range)
        }
    }

    private fun holdAttack(target: LivingEntity, origin: Vec3, range: Double) {
        if (stats.fangs && distanceToEntity(target) <= MatchRules.EVOKER_FANG_RANGE) {
            fleeFrom(target, origin, range)
            (unit as? SummonedEvoker)?.offerFangTarget(target)
        } else {
            tryAttack(target)
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
        if (stats.fangs && distanceToEntity(focus) <= MatchRules.EVOKER_FANG_RANGE) {
            fleeFrom(focus, origin, range)
            (unit as? SummonedEvoker)?.offerFangTarget(focus)
        } else {
            tryAttack(focus)
        }
        if (ticksSinceExchange >= MonsterRules.COUNTER_END_TICKS) enterPath()
    }

    private fun onHoldDamaged(level: ServerLevel, source: DamageSource) {
        val site = homeSite(level) ?: return
        val origin = Vec3.atCenterOf(site.holdOrigin())
        val range = MatchRules.PILLAGER_HOLD_RANGE
        val attacker = source.entity as? LivingEntity ?: return
        if (Targeting.distanceToBox(origin, attacker.boundingBox) > range) return
        if (shouldKeepCurrentTarget(level, attacker)) return
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
        if (!skipsLiving() && keepOrScanLiving(level)) return
        if (phase != Phase.PATH) enterPath()
        if (distantTimer > 0 && distantTimer < MonsterRules.CORE_RETARGET_TICKS) {
            distantTimer++
            val held = currentCoreIntent(level)
            if (held != null) siege(held) else {
                recoverRadius()
                stuckReady = false
                mob.navigation.stop()
            }
            return
        }
        distantTimer = 0
        val distant = chooseCore(level, withinRadius = false)
        if (distant != null) {
            corePos = distant.blockPos
            distantTimer = 1
            siege(distant)
            return
        }
        distantTimer = 1
        recoverRadius()
        stuckReady = false
        mob.navigation.stop()
    }

    private fun siege(core: SiteCoreBlockEntity) {
        if (unit.kindId == SummonedVex.VEX_ID) {
            flySiege(core)
            return
        }
        recoverRadius()
        if (stats.fangs) {
            if (nearestSiegeTarget() == null) {
                tickStuck(core)
                if (giveUpUnreachable(core)) return
                val center = Vec3.atCenterOf(core.blockPos)
                repath(center.x, center.y, center.z)
                return
            }
            mob.navigation.stop()
            mob.lookControl.setLookAt(Vec3.atCenterOf(core.blockPos))
            (unit as? SummonedEvoker)?.trySummonVexes(mob.level() as? ServerLevel ?: return)
            return
        }
        tickStuck(core)
        if (giveUpUnreachable(core)) return
        val center = Vec3.atCenterOf(core.blockPos)
        val coreInRange = distanceToCore(core.blockPos) <= stats.attackRange
        if (stats.explodes) {
            val hit = nearestSiegeTarget()
            if (hit != null) {
                mob.navigation.stop()
                mob.lookControl.setLookAt(Vec3.atCenterOf(hit.pos))
                fuseLive = true
                fuseBuilding = hit.core
            } else {
                repath(center.x, center.y, center.z)
            }
            return
        }
        if (coreInRange) {
            mob.navigation.stop()
            mob.lookControl.setLookAt(center)
        } else {
            repath(center.x, center.y, center.z)
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

    private fun flySiege(core: SiteCoreBlockEntity) {
        recoverRadius()
        mob.navigation.stop()
        val aim = Vec3.atCenterOf(core.blockPos)
        mob.lookControl.setLookAt(aim)
        mob.moveControl.setWantedPosition(aim.x, aim.y, aim.z, 1.0)
        val hit = nearestSiegeTarget()
        if (hit == null || hit.core.blockPos != core.blockPos) return
        buildingCooldown--
        if (buildingCooldown > 0) return
        buildingCooldown = stats.buildingIntervalTicks
        mob.lookControl.setLookAt(Vec3.atCenterOf(hit.pos))
        swingAtBuilding()
        val name = Monsters.byId(unit.kindId)?.displayName ?: unit.kindId
        hit.core.damageFromMonster(mob, unit.summonedTeam, stats.buildingDamage, hit.pos, name)
    }

    private fun repath(x: Double, y: Double, z: Double) {
        val navigation = mob.navigation
        if (navigation.isInProgress) return
        if (mob.tickCount < pathRetryTick) return
        if (!navigation.moveTo(x, y, z, 1.0)) pathRetryTick = mob.tickCount + MonsterRules.REPATH_TICKS
    }

    private fun nearestSiegeTarget(): SiegeHit? {
        val now = mob.tickCount
        if (siegeScanTick != Int.MIN_VALUE && now - siegeScanTick < 4) return siegeScanHit
        siegeScanTick = now
        siegeScanHit = findSiegeTarget()
        return siegeScanHit
    }

    private fun findSiegeTarget(): SiegeHit? {
        val world = mob.level() as? ServerLevel ?: return null
        val range = stats.attackRange
        val rangeSq = range * range
        var best: SiegeHit? = null
        var bestDist = Double.POSITIVE_INFINITY
        fun consider(core: SiteCoreBlockEntity, pos: BlockPos) {
            if (core.isRemoved || core.health <= 0 || core.team == unit.summonedTeam || skipsDefensive(core)) return
            if (Buildings.neutral(core.buildingId)) return
            if (BuildingStyles.monsterIgnoresPart(core, pos)) return
            val dx = mob.x - (pos.x + 0.5)
            val dy = mob.y - (pos.y + 0.5)
            val dz = mob.z - (pos.z + 0.5)
            val distSq = dx * dx + dy * dy + dz * dz
            if (distSq <= rangeSq && distSq < bestDist) {
                bestDist = distSq
                best = SiegeHit(core, pos)
            }
        }
        val radius = kotlin.math.ceil(range).toInt() + 1
        val local = radius <= 4
        val cull = (range + 96.0) * (range + 96.0)
        for (core in SiteCores.all()) {
            if (core.level !== world) continue
            consider(core, core.blockPos)
            if (local) {
                val coreDx = mob.x - (core.blockPos.x + 0.5)
                val coreDz = mob.z - (core.blockPos.z + 0.5)
                if (coreDx * coreDx + coreDz * coreDz > cull) continue
            }
            for (part in core.partPositions) consider(core, part)
        }
        if (!local) return best
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
            val cap = stats.chaseRadiusMax
            chaseRadius = min(cap, chaseRadius + MonsterRules.RADIUS_RECOVER_PER_SECOND)
        }
    }

    private fun tickChase(level: ServerLevel, stick: Boolean = false) {
        val target = living(level, focusId)
        if (target == null || (!stick && !inReach(target))) {
            enterPath()
            return
        }
        tryAttack(target)
        if (stats.explodes && mob.distanceTo(target) <= stats.attackRange) fuseLive = true
        if (unit.kindId != SummonedVex.VEX_ID) {
            if (ticksSinceExchange >= MonsterRules.CHASE_LOSE_INTEREST_TICKS) {
                radiusClock++
                if (radiusClock >= MonsterRules.SECOND_TICKS) {
                    radiusClock = 0
                    chaseRadius = (chaseRadius - MonsterRules.RADIUS_SHRINK_PER_SECOND).coerceAtLeast(0.0)
                }
            } else {
                radiusClock = 0
            }
        }
        if (!stick && !inReach(target)) enterPath()
    }

    private fun tickCounter(level: ServerLevel) {
        var focus = living(level, focusId)
        val focusOk = focus != null && inReach(focus)
        val enemies = if (!focusOk || livingScanDue()) enemiesInRadius(level) else null
        if (!focusOk) {
            focus = enemies?.let { nearestEnemy(it) }
            focusId = focus?.uuid
        }
        if (focus != null) {
            tryAttack(focus)
            if (stats.explodes && mob.distanceTo(focus) <= stats.attackRange) fuseLive = true
        }
        val source = living(level, counterSourceId)
        val sourceGone = source == null || !inReach(source)
        val timedOut = unit.kindId != SummonedVex.VEX_ID && ticksSinceExchange >= MonsterRules.COUNTER_END_TICKS
        if (timedOut || (sourceGone && enemies != null && enemies.isEmpty())) {
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
            SpiderTargeting.chooseCore(TargetWorlds.world, level, origin, unit.summonedTeam, radius, ignore = unreachableCores)
        } else {
            SummonDefaultTargeting.chooseCore(TargetWorlds.world, level, origin, unit.summonedTeam, radius, skipDefensive = false, ignore = unreachableCores, preferCombatDefense = prefersCombatBuildings())
        } ?: return null
        return SiteCores.find(level, picked.pos)?.takeIf { !it.isRemoved }
    }

    private fun currentCore(level: ServerLevel): SiteCoreBlockEntity? {
        val pos = corePos ?: return null
        val core = SiteCores.find(level, pos) ?: return null
        return core.takeIf { isHostileCore(it) && distanceToCore(it.blockPos) <= chaseRadius }
    }

    private fun enemiesInRadius(level: ServerLevel): List<LivingEntity> {
        val found = ArrayList<LivingEntity>()
        for (actor in TargetWorlds.world.players(level)) {
            val player = actor.entity as? ServerPlayer ?: continue
            if (isHostilePlayer(player) && distanceToEntity(player) <= chaseRadius) found.add(player)
        }
        for (actor in TargetWorlds.world.summons(level)) {
            val living = actor.entity
            if (living === mob || !living.isAlive) continue
            if (unit.summonedTeam != actor.team && distanceToEntity(living) <= chaseRadius) found.add(living)
        }
        return found
    }

    private fun keepOrScanLiving(level: ServerLevel): Boolean {
        val kept = living(level, focusId)
        if (kept != null && phase == Phase.CHASE && inReach(kept) && (kept is SummonedUnit || !livingScanDue())) {
            tickChase(level)
            return true
        }
        if (!livingScanDue()) return false
        val monster = preferredMonster(level)
        if (monster != null) {
            if (phase != Phase.CHASE || focusId != monster.living().uuid) enterChase(monster.living())
            tickChase(level)
            return true
        }
        val player = preferredPlayer(level)
        if (player != null) {
            if (phase != Phase.CHASE || focusId != player.uuid) enterChase(player)
            tickChase(level)
            return true
        }
        return false
    }

    private fun refreshCore(level: ServerLevel): SiteCoreBlockEntity? {
        val current = currentCore(level)
        coreTimer++
        if (current != null && coreTimer < MonsterRules.CORE_RETARGET_TICKS) return current
        if (coreTimer < MonsterRules.CORE_RETARGET_TICKS) return null
        coreTimer = 0
        val near = chooseCore(level, withinRadius = true)
        if (near != null) {
            corePos = near.blockPos
            return near
        }
        if (currentCoreIntent(level) == null) corePos = null
        return null
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
        if (Buildings.neutral(core.buildingId)) return false
        if (core.blockPos in unreachableCores) return false
        return unit.summonedTeam != core.team
    }

    private fun giveUpUnreachable(core: SiteCoreBlockEntity): Boolean {
        if (siegeTargetPos != core.blockPos) {
            siegeTargetPos = core.blockPos.immutable()
            resetSiegeFail()
        }
        if (escape.acted) {
            resetSiegeFail()
            return false
        }
        val hit = nearestSiegeTarget()
        val canHit = hit != null && hit.core.blockPos == core.blockPos
        if (canHit || !closeToSiege(core)) {
            resetSiegeFail()
            return false
        }
        val moved = hypot(mob.x - siegeFailX, mob.z - siegeFailZ)
        if (moved >= MonsterRules.STUCK_HORIZONTAL) {
            resetSiegeFail()
            return false
        }
        siegeFailTicks++
        if (siegeFailTicks < MonsterRules.SIEGE_GIVE_UP_TICKS) return false
        val level = mob.level() as? ServerLevel
        if (level == null || !hasAlternateCore(level, core)) {
            resetSiegeFail()
            return false
        }
        unreachableCores.add(core.blockPos.immutable())
        siegeTargetPos = null
        siegeFailTicks = 0
        corePos = null
        enterPath()
        return true
    }

    private fun resetSiegeFail() {
        siegeFailTicks = 0
        siegeFailX = mob.x
        siegeFailZ = mob.z
    }

    private fun hasAlternateCore(level: ServerLevel, core: SiteCoreBlockEntity): Boolean {
        val blocked = HashSet(unreachableCores)
        blocked.add(core.blockPos)
        val origin = mob.position()
        val picked = if (ignoreDefensiveBuildings) {
            SpiderTargeting.chooseCore(TargetWorlds.world, level, origin, unit.summonedTeam, null, ignore = blocked)
        } else {
            SummonDefaultTargeting.chooseCore(
                TargetWorlds.world,
                level,
                origin,
                unit.summonedTeam,
                null,
                skipDefensive = false,
                ignore = blocked,
                preferCombatDefense = prefersCombatBuildings(),
            )
        }
        return picked != null
    }

    private fun closeToSiege(core: SiteCoreBlockEntity): Boolean {
        val range = MonsterRules.SIEGE_GIVE_UP_RANGE
        fun horizontal(pos: BlockPos): Double {
            val dx = mob.x - (pos.x + 0.5)
            val dz = mob.z - (pos.z + 0.5)
            return hypot(dx, dz)
        }
        if (horizontal(core.blockPos) <= range) return true
        if (BuildingStyles.monsterCoreOnly(core.buildingId)) return false
        return core.partPositions.any { horizontal(it) <= range }
    }

    private fun skipsDefensive(core: SiteCoreBlockEntity): Boolean {
        if (!ignoreDefensiveBuildings) return false
        return core.buildingId == Buildings.WALL.id
    }

    private fun distanceToEntity(entity: Entity): Double = mob.position().distanceTo(entity.position())

    private fun distanceToCore(pos: BlockPos): Double = mob.position().distanceTo(Vec3.atCenterOf(pos))

    private fun resetMinerIdle() {
        minerIdleTicks = 0
        minerIdleReady = false
        minerIdleOre = null
    }

    private fun giveUpIdleOre(target: Long): Boolean {
        if (minerIdleOre != target) {
            minerIdleOre = target
            minerIdleReady = false
            minerIdleTicks = 0
        }
        if (!minerIdleReady) {
            minerIdleX = mob.x
            minerIdleZ = mob.z
            minerIdleReady = true
            minerIdleTicks = 0
            return false
        }
        minerIdleTicks++
        if (minerIdleTicks < MonsterRules.SIEGE_GIVE_UP_TICKS) return false
        val moved = hypot(mob.x - minerIdleX, mob.z - minerIdleZ)
        minerIdleX = mob.x
        minerIdleZ = mob.z
        minerIdleTicks = 0
        return moved < MonsterRules.STUCK_HORIZONTAL
    }


    private fun attackReach(): Double = stats.bowRange ?: stats.attackRange

    private fun currentTargetDistance(level: ServerLevel): Double? {
        if (phase == Phase.CHASE || phase == Phase.COUNTER) {
            val focus = living(level, focusId)
            if (focus != null && focus.isAlive) return distanceToEntity(focus)
        }
        val core = currentCoreIntent(level)
        if (core != null) return distanceToCore(core.blockPos)
        return null
    }

    private fun shouldKeepCurrentTarget(level: ServerLevel, attacker: LivingEntity): Boolean {
        val current = currentTargetDistance(level) ?: return false
        val attackerDistance = distanceToEntity(attacker)
        return current < attackerDistance && attackerDistance > attackReach()
    }

    private fun aboveHalfCalm(): Boolean = stats.calmAboveHalf && mob.health > mob.maxHealth * 0.5f

    private fun skipsLiving(): Boolean = stats.buildingsOnly || aboveHalfCalm()

    private fun prefersCombatBuildings(): Boolean =
        stats.fangs || unit.kindId == SummonedVex.VEX_ID

    private val stats: MonsterDefinition
        get() = Monsters.byId(unit.kindId) ?: Monsters.ZOMBIE

    private enum class Phase {
        PATH,
        CHASE,
        COUNTER,
    }

    private data class SiegeHit(val core: SiteCoreBlockEntity, val pos: BlockPos)

    fun applyCommand(player: ServerPlayer, follow: Boolean) {
        if (follow) {
            commandMode = CommandMode.FOLLOW
            followOwnerId = player.uuid
            returningToOwner = false
        } else {
            becomeExpedition(leaveHold = true)
        }
        enterPath()
    }

    fun recallHold(site: SiteCoreBlockEntity) {
        if (homePos != site.blockPos) return
        if (commandMode == CommandMode.FOLLOW) return
        holdEpochSeen = site.holdEpoch
        enterPath()
        returningHome = false
    }

    private fun becomeExpedition(leaveHold: Boolean) {
        commandMode = CommandMode.EXPEDITION
        followOwnerId = null
        returningToOwner = false
        if (leaveHold) {
            val world = mob.level() as? ServerLevel
            val site = world?.let { homeSite(it) }
            holdEpochSeen = if (site != null) site.holdEpoch - 1 else -1
        }
    }

    private fun refreshCommand(level: ServerLevel) {
        if (commandMode != CommandMode.FOLLOW) return
        if (ownerAiming()) return
        if (followOwner(level) == null) becomeExpedition(leaveHold = true)
    }

    private fun ownerAiming(): Boolean {
        val id = followOwnerId ?: return false
        return Targeting.isAiming(id)
    }

    private fun followOwner(level: ServerLevel): ServerPlayer? {
        if (commandMode != CommandMode.FOLLOW) return null
        val id = followOwnerId ?: return null
        val player = level.server.playerList.getPlayer(id) ?: return null
        if (!player.isAlive || player.isSpectator) return null
        if (player.level() !== level) return null
        if (!Match.isFight()) return null
        val member = Match.member(id) ?: return null
        if (member.team.isEmpty() || member.team != unit.summonedTeam) return null
        return player
    }

    private fun suppressesFollowCounter(level: ServerLevel): Boolean {
        if (returningToOwner) return true
        val owner = followOwner(level) ?: return true
        return distanceToEntity(owner) > MatchRules.FOLLOW_LEASH
    }

    private fun tickFollow(level: ServerLevel) {
        if (ownerAiming()) return
        val owner = followOwner(level)
        if (owner == null) {
            becomeExpedition(leaveHold = true)
            if (stats.buildingsOnly && !holdingActive(level) && (phase == Phase.COUNTER || phase == Phase.CHASE)) {
                enterPath()
            }
            if (aboveHalfCalm() && (phase == Phase.COUNTER || phase == Phase.CHASE)) enterPath()
            if (holdingActive(level)) {
                tickHold(level)
                return
            }
            returningHome = false
            when (phase) {
                Phase.COUNTER -> tickCounter(level)
                else -> tickSeek(level)
            }
            return
        }
        val distance = distanceToEntity(owner)
        if (distance > MatchRules.FOLLOW_LEASH) returningToOwner = true
        else if (distance <= MatchRules.FOLLOW_RESUME) returningToOwner = false
        if (returningToOwner) {
            if (phase == Phase.COUNTER) enterPath()
            mob.setTarget(null)
            if (mob.tickCount % MonsterRules.REPATH_TICKS == 0 || !mob.navigation.isInProgress) {
                mob.navigation.moveTo(owner.x, owner.y, owner.z, 1.0)
            }
            return
        }
        if (phase == Phase.COUNTER) {
            tickCounter(level)
            return
        }
        tickFollowSeek(level, owner)
    }

    private fun tickFollowSeek(level: ServerLevel, owner: ServerPlayer) {
        if (!skipsLiving()) {
            val kept = living(level, focusId)
            if (kept != null && phase == Phase.CHASE && inReach(kept) && !livingScanDue()) {
                tickChase(level)
                return
            }
            if (livingScanDue()) {
                val player = preferredPlayer(level)
                if (player != null) {
                    if (phase != Phase.CHASE || focusId != player.uuid) enterChase(player)
                    tickChase(level)
                    return
                }
                val monster = preferredMonster(level)
                if (monster != null) {
                    if (phase != Phase.CHASE || focusId != monster.living().uuid) enterChase(monster.living())
                    tickChase(level)
                    return
                }
            }
        }
        val core = refreshCore(level)
        if (core != null) {
            siege(core)
            return
        }
        if (phase != Phase.PATH) enterPath()
        if (mob.tickCount % MonsterRules.REPATH_TICKS == 0 || !mob.navigation.isInProgress) {
            mob.navigation.moveTo(owner.x, owner.y, owner.z, 1.0)
        }
    }

    private enum class CommandMode {
        EXPEDITION,
        FOLLOW,
    }

    companion object {
        private fun readId(input: net.minecraft.world.level.storage.ValueInput, key: String): UUID? {
            val text = input.getStringOr(key, "")
            if (text.isEmpty()) return null
            return runCatching { UUID.fromString(text) }.getOrNull()
        }
    }
}
