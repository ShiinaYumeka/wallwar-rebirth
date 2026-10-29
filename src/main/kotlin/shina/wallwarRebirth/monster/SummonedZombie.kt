package shina.wallwarRebirth.monster

import eu.pb4.polymer.core.api.entity.PolymerEntity
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import shina.wallwarRebirth.building.SiteCoreBlockEntity
import shina.wallwarRebirth.building.SiteCores
import shina.wallwarRebirth.item.ModComponents
import xyz.nucleoid.packettweaker.PacketContext
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.min

class SummonedZombie(type: EntityType<out SummonedZombie>, level: Level) : Zombie(type, level), PolymerEntity {
    var kindId: String = Monsters.ZOMBIE.id
    var summonedTeam: String = ""
    var chaseRadius: Double = MonsterRules.CHASE_RADIUS_START
    private var phase = Phase.PATH
    private var focusId: UUID? = null
    private var counterSourceId: UUID? = null
    private var corePos: net.minecraft.core.BlockPos? = null
    private var meleeCooldown = 0
    private var buildingCooldown = Monsters.ZOMBIE.buildingIntervalTicks
    private var ticksSinceExchange = 0
    private var radiusClock = 0
    private var coreTimer = MonsterRules.CORE_RETARGET_TICKS
    private var stuckTicks = 0
    private var stuckX = 0.0
    private var stuckZ = 0.0
    private var stuckReady = false

    init {
        applyStats(Monsters.ZOMBIE)
        customName = Component.literal(Monsters.ZOMBIE.displayName)
        isCustomNameVisible = true
        setPersistenceRequired()
    }

    override fun getPolymerEntityType(context: PacketContext): EntityType<*> = EntityType.ZOMBIE

    override fun registerGoals() {}

    override fun isSunSensitive(): Boolean = false

    override fun convertsInWater(): Boolean = false

    override fun removeWhenFarAway(distance: Double): Boolean = false

    override fun getBaseExperienceReward(level: ServerLevel): Int = 0

    override fun wantsToPickUp(level: ServerLevel, stack: ItemStack): Boolean = false

    override fun remove(reason: RemovalReason) {
        SummonedUnits.remove(this)
        super.remove(reason)
    }

    override fun hurtServer(level: ServerLevel, source: DamageSource, amount: Float): Boolean {
        val hurt = super.hurtServer(level, source, amount)
        if (hurt && amount > 0f) onDamaged(level, source)
        return hurt
    }

    override fun doHurtTarget(level: ServerLevel, target: Entity): Boolean {
        val hit = super.doHurtTarget(level, target)
        if (hit) ticksSinceExchange = 0
        return hit
    }

    override fun dropCustomDeathLoot(level: ServerLevel, source: DamageSource, recentlyHit: Boolean) {
        val stack = ItemStack(stats.dropItem, stats.dropCount)
        val killerTeam = killerTeam(source)
        if (killerTeam != null) stack.set(ModComponents.DROP_TEAM, killerTeam)
        spawnAtLocation(level, stack)
    }

    override fun customServerAiStep(level: ServerLevel) {
        super.customServerAiStep(level)
        if (!isAlive) return
        SummonedUnits.add(this)
        if (meleeCooldown > 0) meleeCooldown--
        ticksSinceExchange++
        when (phase) {
            Phase.COUNTER -> tickCounter(level)
            else -> tickSeek(level)
        }
    }

    override fun addAdditionalSaveData(output: net.minecraft.world.level.storage.ValueOutput) {
        super.addAdditionalSaveData(output)
        output.putString("Kind", kindId)
        output.putString("Team", summonedTeam)
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

    override fun readAdditionalSaveData(input: net.minecraft.world.level.storage.ValueInput) {
        super.readAdditionalSaveData(input)
        kindId = input.getStringOr("Kind", Monsters.ZOMBIE.id)
        summonedTeam = input.getStringOr("Team", "")
        phase = runCatching { Phase.valueOf(input.getStringOr("Phase", Phase.PATH.name)) }.getOrDefault(Phase.PATH)
        chaseRadius = input.getDoubleOr("ChaseRadius", MonsterRules.CHASE_RADIUS_START)
        meleeCooldown = input.getIntOr("MeleeCooldown", 0)
        buildingCooldown = input.getIntOr("BuildingCooldown", stats.buildingIntervalTicks)
        ticksSinceExchange = input.getIntOr("TicksSinceExchange", 0)
        radiusClock = input.getIntOr("RadiusClock", 0)
        coreTimer = input.getIntOr("CoreTimer", MonsterRules.CORE_RETARGET_TICKS)
        stuckTicks = input.getIntOr("StuckTicks", 0)
        stuckX = input.getDoubleOr("StuckX", x)
        stuckZ = input.getDoubleOr("StuckZ", z)
        stuckReady = input.getBooleanOr("StuckReady", false)
        corePos = if (input.getBooleanOr("HasCore", false)) {
            net.minecraft.core.BlockPos(
                input.getIntOr("CoreX", 0),
                input.getIntOr("CoreY", 0),
                input.getIntOr("CoreZ", 0),
            )
        } else {
            null
        }
        focusId = readId(input, "Focus")
        counterSourceId = readId(input, "CounterSource")
        customName = Component.literal(stats.displayName)
        isCustomNameVisible = true
    }

    private fun tickSeek(level: ServerLevel) {
        val core = refreshCore(level)
        if (core != null) {
            if (phase != Phase.PATH) {
                phase = Phase.PATH
                focusId = null
                counterSourceId = null
                setTarget(null)
                ticksSinceExchange = 0
                radiusClock = 0
                stuckReady = false
            }
            siege(core)
            return
        }
        val monster = preferredMonster(level)
        if (monster != null) {
            if (phase != Phase.CHASE || focusId != monster.uuid) enterChase(monster)
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
        recoverRadius()
        stuckReady = false
        navigation.stop()
    }

    private fun siege(core: SiteCoreBlockEntity) {
        recoverRadius()
        tickStuck(core)
        val center = Vec3.atCenterOf(core.blockPos)
        val inRange = distanceToCore(core.blockPos) <= stats.attackRange
        if (inRange) {
            navigation.stop()
            lookControl.setLookAt(center)
        } else if (tickCount % MonsterRules.REPATH_TICKS == 0) {
            navigation.moveTo(center.x, center.y, center.z, 1.0)
        }
        buildingCooldown--
        if (buildingCooldown <= 0) {
            buildingCooldown = stats.buildingIntervalTicks
            if (inRange) core.damageFromMonster(summonedTeam, stats.buildingDamage)
        }
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
        tryMelee(level, target)
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
        if (focus != null) tryMelee(level, focus)
        val source = living(level, counterSourceId)
        val sourceGone = source == null || !inReach(source)
        val still = enemiesInRadius(level)
        if (ticksSinceExchange >= MonsterRules.COUNTER_END_TICKS || (sourceGone && still.isEmpty())) {
            enterPath()
        }
    }

    private fun onDamaged(level: ServerLevel, source: DamageSource) {
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
        setTarget(chosen)
    }

    private fun enterChase(target: LivingEntity) {
        phase = Phase.CHASE
        focusId = target.uuid
        counterSourceId = null
        ticksSinceExchange = 0
        radiusClock = 0
        stuckReady = false
        setTarget(target)
    }

    private fun enterPath() {
        phase = Phase.PATH
        focusId = null
        counterSourceId = null
        ticksSinceExchange = 0
        radiusClock = 0
        stuckReady = false
        setTarget(null)
        navigation.stop()
    }

    private fun tryMelee(level: ServerLevel, target: LivingEntity) {
        setTarget(target)
        lookControl.setLookAt(target, 30f, 30f)
        if (distanceToEntity(target) <= stats.attackRange) {
            navigation.stop()
            if (meleeCooldown <= 0) {
                doHurtTarget(level, target)
                meleeCooldown = MonsterRules.MELEE_INTERVAL_TICKS
            }
        } else if (tickCount % MonsterRules.REPATH_TICKS == 0) {
            navigation.moveTo(target, 1.0)
        }
    }

    private fun tickStuck(core: SiteCoreBlockEntity) {
        if (!stuckReady) {
            stuckX = x
            stuckZ = z
            stuckReady = true
            stuckTicks = 0
            return
        }
        stuckTicks++
        if (stuckTicks < MonsterRules.STUCK_TICKS) return
        val moved = hypot(x - stuckX, z - stuckZ)
        stuckX = x
        stuckZ = z
        stuckTicks = 0
        if (moved < MonsterRules.STUCK_HORIZONTAL) {
            val center = Vec3.atCenterOf(core.blockPos)
            navigation.stop()
            navigation.moveTo(center.x, center.y, center.z, 1.0)
        }
    }

    private fun chooseCore(level: ServerLevel): SiteCoreBlockEntity? {
        val choices = ArrayList<CoreChoice>()
        for (core in SiteCores.all()) {
            if (core.level !== level || !isHostileCore(core)) continue
            choices.add(CoreChoice(core, distanceToCore(core.blockPos)))
        }
        return choices.minWithOrNull(
            compareBy<CoreChoice> { (it.distance * 1000.0).toLong() }
                .thenBy { it.core.health }
                .thenBy { it.core.blockPos.x }
                .thenBy { it.core.blockPos.z }
                .thenBy { it.core.blockPos.y },
        )?.core
    }

    private fun currentCore(level: ServerLevel): SiteCoreBlockEntity? {
        val pos = corePos ?: return null
        val core = SiteCores.all().firstOrNull { it.level === level && it.blockPos == pos } ?: return null
        return core.takeIf { isHostileCore(it) }
    }

    private fun enemiesInRadius(level: ServerLevel): List<LivingEntity> {
        val found = ArrayList<LivingEntity>()
        for (player in level.players()) {
            if (isHostilePlayer(player) && distanceToEntity(player) <= chaseRadius) found.add(player)
        }
        for (unit in SummonedUnits.all()) {
            if (unit === this || unit.level() !== level) continue
            if (summonedTeam != unit.summonedTeam && distanceToEntity(unit) <= chaseRadius) found.add(unit)
        }
        return found
    }

    private fun refreshCore(level: ServerLevel): SiteCoreBlockEntity? {
        coreTimer++
        var core = currentCore(level)
        if (core == null || coreTimer >= MonsterRules.CORE_RETARGET_TICKS) {
            coreTimer = 0
            core = chooseCore(level)
            corePos = core?.blockPos
        }
        return core
    }

    private fun preferredMonster(level: ServerLevel): SummonedZombie? {
        val current = living(level, focusId) as? SummonedZombie
        if (current != null && inReach(current)) return current
        return monstersInRadius(level).minWithOrNull(
            compareBy<SummonedZombie> { distanceToEntity(it) }.thenBy { it.id },
        )
    }

    private fun preferredPlayer(level: ServerLevel): ServerPlayer? {
        val current = living(level, focusId) as? ServerPlayer
        if (current != null && inReach(current)) return current
        return playersInRadius(level).minWithOrNull(
            compareBy<ServerPlayer> { distanceToEntity(it) }.thenBy { it.id },
        )
    }

    private fun monstersInRadius(level: ServerLevel): List<SummonedZombie> = SummonedUnits.all().filter { unit ->
        unit !== this && unit.level() === level && summonedTeam != unit.summonedTeam && distanceToEntity(unit) <= chaseRadius
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
            is SummonedZombie -> this !== entity && summonedTeam != entity.summonedTeam
            else -> false
        }
        return hostile && distanceToEntity(entity) <= chaseRadius
    }

    private fun isHostilePlayer(player: ServerPlayer): Boolean {
        if (!player.isAlive || player.isSpectator || player.isRemoved) return false
        return summonedTeam != (player.team?.name ?: "")
    }

    private fun isHostileCore(core: SiteCoreBlockEntity): Boolean {
        if (core.isRemoved || core.buildingId.isEmpty()) return false
        return summonedTeam != core.team
    }

    private fun distanceToEntity(entity: Entity): Double = position().distanceTo(entity.position())

    private fun distanceToCore(pos: net.minecraft.core.BlockPos): Double = position().distanceTo(Vec3.atCenterOf(pos))

    private fun killerTeam(source: DamageSource): String? = when (val killer = source.entity) {
        is SummonedZombie -> killer.summonedTeam
        is net.minecraft.world.entity.player.Player -> killer.team?.name ?: ""
        else -> null
    }

    private fun applyStats(definition: MonsterDefinition) {
        getAttribute(Attributes.MAX_HEALTH)?.baseValue = definition.health
        getAttribute(Attributes.ARMOR)?.baseValue = definition.armor
        getAttribute(Attributes.ATTACK_DAMAGE)?.baseValue = definition.playerAttack
        health = maxHealth
    }

    private val stats: MonsterDefinition
        get() = Monsters.byId(kindId) ?: Monsters.ZOMBIE

    private enum class Phase {
        PATH,
        CHASE,
        COUNTER,
    }

    private data class CoreChoice(val core: SiteCoreBlockEntity, val distance: Double)

    companion object {
        private fun readId(input: net.minecraft.world.level.storage.ValueInput, key: String): UUID? {
            val text = input.getStringOr(key, "")
            if (text.isEmpty()) return null
            return runCatching { UUID.fromString(text) }.getOrNull()
        }
    }
}
