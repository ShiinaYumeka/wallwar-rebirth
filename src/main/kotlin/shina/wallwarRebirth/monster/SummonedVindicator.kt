package shina.wallwarRebirth.monster

import eu.pb4.polymer.core.api.entity.PolymerEntity
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import shina.wallwarRebirth.match.Match
import xyz.nucleoid.packettweaker.PacketContext

class SummonedVindicator(type: EntityType<out SummonedVindicator>, level: Level) :
    net.minecraft.world.entity.monster.illager.Vindicator(type, level),
    PolymerEntity,
    SummonedUnit {
    override var kindId: String = Monsters.VINDICATOR.id
    override var summonedTeam: String = ""
    override val mind: SummonedBrain = SummonedBrain(this, this)

    init {
        SummonedGear.applyStats(this, Monsters.VINDICATOR, fill = true)
        SummonedGear.apply(this, this)
        setPersistenceRequired()
    }

    override fun getPolymerEntityType(context: PacketContext): EntityType<*> = EntityType.VINDICATOR

    override fun registerGoals() {
        super.registerGoals()
        targetSelector.removeAllGoals { true }
    }

    override fun canJoinRaid(): Boolean = false


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
        
        shina.wallwarRebirth.monster.ai.VindicatorAi.tick(this, level)
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
