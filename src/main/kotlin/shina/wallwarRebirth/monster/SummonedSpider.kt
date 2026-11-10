package shina.wallwarRebirth.monster

import eu.pb4.polymer.core.api.entity.PolymerEntity
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal
import net.minecraft.world.entity.monster.spider.Spider
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import shina.wallwarRebirth.match.Match
import xyz.nucleoid.packettweaker.PacketContext

class SummonedSpider(type: EntityType<out SummonedSpider>, level: Level) : Spider(type, level), PolymerEntity, SummonedUnit {
    override var kindId: String = Monsters.SPIDER.id
    override var summonedTeam: String = ""
    override val mind: SummonedBrain = SummonedBrain(this, this)

    init {
        SummonedGear.applyStats(this, Monsters.SPIDER, fill = true)
        SummonedGear.apply(this, this)
        setPersistenceRequired()
    }

    override fun getPolymerEntityType(context: PacketContext): EntityType<*> = EntityType.SPIDER

    override fun registerGoals() {
        goalSelector.addGoal(1, FloatGoal(this))
        goalSelector.addGoal(3, LeapAtTargetGoal(this, 0.4f))
        goalSelector.addGoal(4, MeleeAttackGoal(this, 1.0, true))
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
    }
}
