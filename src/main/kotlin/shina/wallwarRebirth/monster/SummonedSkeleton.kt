package shina.wallwarRebirth.monster

import eu.pb4.polymer.core.api.entity.PolymerEntity
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.monster.skeleton.Skeleton
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import xyz.nucleoid.packettweaker.PacketContext

class SummonedSkeleton(type: EntityType<out SummonedSkeleton>, level: Level) :
    Skeleton(type, level),
    PolymerEntity,
    SummonedUnit {
    override var kindId: String = Monsters.SKELETON.id
    override var summonedTeam: String = ""
    override val mind: SummonedBrain = SummonedBrain(this, this)

    init {
        SummonedGear.applyStats(this, Monsters.SKELETON, fill = true)
        SummonedGear.apply(this, this)
        reassessWeaponGoal()
        setPersistenceRequired()
    }

    override fun getPolymerEntityType(context: PacketContext): EntityType<*> = EntityType.SKELETON

    override fun registerGoals() {
        goalSelector.addGoal(1, FloatGoal(this))
    }

    override fun performRangedAttack(target: LivingEntity, power: Float) {
        val world = level() as? ServerLevel ?: return
        mind.shoot(world, target)
    }

    override fun startFreezeConversion(time: Int) {}

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

    override fun dropCustomDeathLoot(level: ServerLevel, source: DamageSource, recentlyHit: Boolean) {
        mind.dropLoot(level, source)
    }

    override fun customServerAiStep(level: ServerLevel) {
        if (shina.wallwarRebirth.match.Match.frozen(level)) return
        super.customServerAiStep(level)
        mind.tick(level)
    }

    override fun addAdditionalSaveData(output: net.minecraft.world.level.storage.ValueOutput) {
        super.addAdditionalSaveData(output)
        mind.save(output)
    }

    override fun readAdditionalSaveData(input: net.minecraft.world.level.storage.ValueInput) {
        super.readAdditionalSaveData(input)
        mind.load(input)
        SummonedGear.apply(this, this)
        reassessWeaponGoal()
    }
}
