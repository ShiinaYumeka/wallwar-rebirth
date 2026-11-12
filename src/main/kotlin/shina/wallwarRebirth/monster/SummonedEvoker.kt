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

class SummonedEvoker(type: EntityType<out SummonedEvoker>, level: Level) :
    net.minecraft.world.entity.monster.illager.Evoker(type, level),
    PolymerEntity,
    SummonedUnit {
    override var kindId: String = Monsters.EVOKER.id
    override var summonedTeam: String = ""
    override val mind: SummonedBrain = SummonedBrain(this, this)

    init {
        SummonedGear.applyStats(this, Monsters.EVOKER, fill = true)
        SummonedGear.apply(this, this)
        setPersistenceRequired()
    }

    override fun getPolymerEntityType(context: PacketContext): EntityType<*> = EntityType.EVOKER

    override fun registerGoals() {
        super.registerGoals()
        targetSelector.removeAllGoals { true }
    }

    override fun canJoinRaid(): Boolean = false

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
