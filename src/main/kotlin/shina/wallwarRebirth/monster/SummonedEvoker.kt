package shina.wallwarRebirth.monster

import eu.pb4.polymer.core.api.entity.PolymerEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.Mth
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.projectile.EvokerFangs
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import shina.wallwarRebirth.match.Match
import shina.wallwarRebirth.rules.MatchRules
import xyz.nucleoid.packettweaker.PacketContext
import java.util.*
import kotlin.math.*

class SummonedEvoker(type: EntityType<out SummonedEvoker>, level: Level) :
    net.minecraft.world.entity.monster.illager.Evoker(type, level),
    PolymerEntity,
    SummonedUnit {
    override var kindId: String = Monsters.EVOKER.id
    override var summonedTeam: String = ""
    override val mind: SummonedBrain = SummonedBrain(this, this)
    private var vexCooldown = 0
    private var fangCooldown = 0
    private var fangWindup = 0
    private var fangTargetId: UUID? = null

    init {
        SummonedGear.applyStats(this, Monsters.EVOKER, fill = true)
        SummonedGear.apply(this, this)
        setPersistenceRequired()
    }

    override fun getPolymerEntityType(context: PacketContext): EntityType<*> = EntityType.EVOKER

    override fun registerGoals() {
        super.registerGoals()
        targetSelector.removeAllGoals { true }
        goalSelector.removeAllGoals { goal ->
            val name = goal.javaClass.simpleName
            name == "EvokerAttackSpellGoal" || name == "EvokerSummonSpellGoal"
        }
    }

    override fun canJoinRaid(): Boolean = false

    fun offerFangTarget(target: LivingEntity) {
        if (!target.isAlive || target.isRemoved) return
        if (fangWindup > 0) {
            fangTargetId = target.uuid
            return
        }
        if (fangCooldown > 0) return
        fangTargetId = target.uuid
        fangWindup = MatchRules.EVOKER_FANG_CAST_TICKS
        setIsCastingSpell(IllagerSpell.FANGS)
        playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 1f, 1f)
    }

    fun tickFangCast(level: ServerLevel) {
        if (fangCooldown > 0) fangCooldown--
        if (fangWindup <= 0) return
        val target = fangTargetId?.let { level.getEntity(it) } as? LivingEntity
        if (target == null || !target.isAlive || target.isRemoved || !mind.canKeepFocus(target)) {
            fangWindup = 0
            fangTargetId = null
            setIsCastingSpell(IllagerSpell.NONE)
            return
        }
        fangWindup--
        if (fangWindup > 0) return
        releaseFangs(level, target)
        fangTargetId = null
        fangCooldown = MatchRules.EVOKER_FANG_INTERVAL_TICKS
        setIsCastingSpell(IllagerSpell.NONE)
    }

    private fun releaseFangs(level: ServerLevel, target: LivingEntity) {
        val minY = min(target.y, y)
        val maxY = max(target.y, y) + 1.0
        val angle = atan2(target.z - z, target.x - x).toFloat()
        val distance = distanceTo(target)
        when {
            distance < MatchRules.EVOKER_FANG_MELEE -> castFangCircle(level, angle, minY, maxY)
            distance < MatchRules.EVOKER_FANG_LINE -> castFangLine(level, angle, minY, maxY)
            else -> spawnFang(level, target.x, target.z, minY, maxY, angle, 0)
        }
    }

    private fun castFangCircle(level: ServerLevel, angle: Float, minY: Double, maxY: Double) {
        for (index in 0 until 5) {
            val fang = angle + index * Math.PI.toFloat() * 0.4f
            spawnFang(level, x + cos(fang.toDouble()) * 1.5, z + sin(fang.toDouble()) * 1.5, minY, maxY, fang, 0)
        }
        for (index in 0 until 8) {
            val fang = angle + index * Math.PI.toFloat() * 2f / 8f + 1.2566371f
            spawnFang(level, x + cos(fang.toDouble()) * 2.5, z + sin(fang.toDouble()) * 2.5, minY, maxY, fang, 3)
        }
    }

    private fun castFangLine(level: ServerLevel, angle: Float, minY: Double, maxY: Double) {
        val radians = angle.toDouble()
        for (index in 0 until 16) {
            val reach = 1.25 * (index + 1)
            spawnFang(
                level,
                x + cos(radians) * reach,
                z + sin(radians) * reach,
                minY,
                maxY,
                angle,
                index,
            )
        }
    }

    private fun spawnFang(
        level: ServerLevel,
        fangX: Double,
        fangZ: Double,
        minY: Double,
        maxY: Double,
        yRot: Float,
        warmup: Int,
    ) {
        var pos = BlockPos.containing(fangX, maxY, fangZ)
        var placed = false
        var yOffset = 0.0
        while (true) {
            val below = pos.below()
            val ground = level.getBlockState(below)
            if (ground.isFaceSturdy(level, below, Direction.UP)) {
                if (!level.isEmptyBlock(pos)) {
                    val shape = level.getBlockState(pos).getCollisionShape(level, pos)
                    if (!shape.isEmpty) yOffset = shape.max(Direction.Axis.Y)
                }
                placed = true
                break
            }
            pos = pos.below()
            if (pos.y < Mth.floor(minY) - 1) break
        }
        if (!placed) return
        level.addFreshEntity(EvokerFangs(level, fangX, pos.y + yOffset, fangZ, yRot, warmup, this))
    }

    fun trySummonVexes(level: ServerLevel) {
        if (vexCooldown > 0) {
            vexCooldown--
            return
        }
        val need = MatchRules.EVOKER_VEX_COUNT - ownedVexes(level).size
        if (need <= 0) return
        repeat(need) { spawnOwnedVex(level) }
        vexCooldown = MatchRules.EVOKER_VEX_COOLDOWN_TICKS
    }

    private fun ownedVexes(level: ServerLevel): List<SummonedVex> =
        SummonedUnits.all().filterIsInstance<SummonedVex>().filter { vex ->
            vex.isAlive && !vex.isRemoved && vex.level() === level && vex.owner === this
        }

    private fun spawnOwnedVex(level: ServerLevel) {
        val summoned = SummonedVex(ModMonsters.VEX, level)
        val ox = (random.nextDouble() * 2.0 - 1.0) * 1.5
        val oz = (random.nextDouble() * 2.0 - 1.0) * 1.5
        summoned.snapTo(x + ox, y + 1.0, z + oz, yRot, xRot)
        summoned.inheritFrom(this, mind.homePos() ?: blockPosition())
        summoned.setLimitedLife(20 * (30 + random.nextInt(90)))
        level.addFreshEntity(summoned)
    }

    private fun adoptVexes(level: ServerLevel) {
        val box = boundingBox.inflate(32.0)
        for (vex in level.getEntitiesOfClass(net.minecraft.world.entity.monster.Vex::class.java, box)) {
            if (vex is SummonedVex || vex.owner !== this || !vex.isAlive) continue
            val summoned = SummonedVex(ModMonsters.VEX, level)
            summoned.snapTo(vex.x, vex.y, vex.z, vex.yRot, vex.xRot)
            summoned.inheritFrom(this, mind.homePos() ?: blockPosition())
            summoned.setLimitedLife(20 * (30 + random.nextInt(90)))
            vex.discard()
            level.addFreshEntity(summoned)
        }
    }

    override fun removeWhenFarAway(distance: Double): Boolean = false

    override fun getBaseExperienceReward(level: ServerLevel): Int = 0

    override fun wantsToPickUp(level: ServerLevel, stack: ItemStack): Boolean = false

    override fun remove(reason: RemovalReason) {
        SummonedUnits.remove(this)
        super.remove(reason)
    }

    override fun hurtServer(level: ServerLevel, source: DamageSource, amount: Float): Boolean {
        val hurt = super.hurtServer(level, source, amount)
        if (hurt && amount > 0f) mind.onDamaged(level, source)
        return hurt
    }

    override fun doHurtTarget(level: ServerLevel, target: Entity): Boolean {
        val hit = super.doHurtTarget(level, target)
        if (hit) mind.noteExchange()
        return hit
    }

    override fun dropCustomDeathLoot(level: ServerLevel, source: DamageSource, recentlyHit: Boolean) {}

    override fun customServerAiStep(level: ServerLevel) {
        if (Match.frozen(level)) return
        super.customServerAiStep(level)
        tickFangCast(level)
        adoptVexes(level)
        shina.wallwarRebirth.monster.ai.EvokerAi.tick(this, level)
    }

    override fun addAdditionalSaveData(output: net.minecraft.world.level.storage.ValueOutput) {
        super.addAdditionalSaveData(output)
        mind.save(output)
    }

    override fun readAdditionalSaveData(input: net.minecraft.world.level.storage.ValueInput) {
        super.readAdditionalSaveData(input)
        mind.load(input)
        SummonedGear.apply(this, this)
        
    }
}
