package shina.wallwarRebirth.monster

import eu.pb4.polymer.core.api.entity.PolymerEntity
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal
import net.minecraft.world.entity.ai.goal.MoveTowardsTargetGoal
import net.minecraft.world.entity.animal.golem.IronGolem
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import shina.wallwarRebirth.rules.MatchRules
import xyz.nucleoid.packettweaker.PacketContext

class SummonedGolem(type: EntityType<out SummonedGolem>, level: Level) :
    IronGolem(type, level),
    PolymerEntity,
    SummonedUnit {
    override var kindId: String = Monsters.GOLEM.id
    override var summonedTeam: String = ""
    override val mind: SummonedBrain = SummonedBrain(this, this)

    init {
        setPlayerCreated(true)
        SummonedGear.applyStats(this, Monsters.GOLEM, fill = true)
        SummonedGear.apply(this, this)
        setPersistenceRequired()
    }

    override fun getPolymerEntityType(context: PacketContext): EntityType<*> = EntityType.IRON_GOLEM

    override fun registerGoals() {
        goalSelector.addGoal(1, MeleeAttackGoal(this, 1.0, true))
        goalSelector.addGoal(2, MoveTowardsTargetGoal(this, 0.9, 32.0f))
    }

    override fun removeWhenFarAway(distance: Double): Boolean = false

    override fun getBaseExperienceReward(level: ServerLevel): Int = 0

    override fun wantsToPickUp(level: ServerLevel, stack: ItemStack): Boolean = false

    override fun mobInteract(player: Player, hand: InteractionHand): InteractionResult {
        val stack = player.getItemInHand(hand)
        if (!stack.`is`(Items.IRON_INGOT)) {
            return InteractionResult.PASS
        }
        val before = health
        heal(MatchRules.GOLEM_INGOT_REPAIR)
        if (health == before) {
            return InteractionResult.PASS
        }
        val pitch = 1.0f + (random.nextFloat() - random.nextFloat()) * 0.2f
        playSound(SoundEvents.IRON_GOLEM_REPAIR, 1.0f, pitch)
        stack.consume(1, player)
        return InteractionResult.SUCCESS
    }

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
        if (shina.wallwarRebirth.match.Match.frozen(level)) return
        super.customServerAiStep(level)
        shina.wallwarRebirth.monster.ai.GolemAi.tick(this, level)
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
