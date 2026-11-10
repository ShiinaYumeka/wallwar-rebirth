package shina.wallwarRebirth.monster

import eu.pb4.polymer.core.api.entity.PolymerEntity
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.protocol.game.ClientboundExplodePacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.random.WeightedList
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal
import net.minecraft.world.entity.monster.Creeper
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.ServerExplosion
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import shina.wallwarRebirth.building.SiteCoreBlockEntity
import shina.wallwarRebirth.match.Match
import shina.wallwarRebirth.match.MatchRules
import xyz.nucleoid.packettweaker.PacketContext
import java.util.Optional
import kotlin.math.sqrt

class SummonedCreeper(type: EntityType<out SummonedCreeper>, level: Level) : Creeper(type, level), PolymerEntity, SummonedUnit {
    override var kindId: String = Monsters.CREEPER.id
    override var summonedTeam: String = ""
    override val mind: SummonedBrain = SummonedBrain(this, this)
    var siegeTarget: SiteCoreBlockEntity? = null

    init {
        SummonedGear.applyStats(this, Monsters.CREEPER, fill = true)
        SummonedGear.apply(this, this)
        setPersistenceRequired()
    }

    fun arm(live: Boolean, building: SiteCoreBlockEntity?) {
        setSwellDir(if (live) 1 else -1)
        siegeTarget = if (live) building else null
    }

    fun burst() {
        val level = level() as? ServerLevel ?: return
        val center = position()
        val building = siegeTarget
        val knocks = HashMap<ServerPlayer, Vec3>()
        val reach = MatchRules.CREEPER_FALLOFF
        val area = AABB.ofSize(center, (reach + 2.0) * 2.0, (reach + 2.0) * 2.0, (reach + 2.0) * 2.0)
        for (entity in level.getEntities(this, area)) {
            val living = entity as? LivingEntity ?: continue
            if (living === this || !living.isAlive) continue
            val other = teamOf(living) ?: continue
            if (other.isEmpty() || other == summonedTeam) continue
            val distance = sqrt(living.distanceToSqr(center))
            val norm = distance / reach
            if (norm > 1.0) continue
            val seen = ServerExplosion.getSeenPercent(center, living).toDouble()
            val exposure = (1.0 - norm) * seen
            if (exposure <= 0.0) continue
            val raw = MatchRules.CREEPER_ATTACK * (exposure * exposure + exposure) / 2.0
            if (raw > 0.0) living.hurtServer(level, level.damageSources().explosion(this, null), raw.toFloat())
            val eye = living.eyePosition.subtract(center)
            if (eye.lengthSqr() <= 1.0e-8) continue
            val resistance = living.getAttributeValue(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE)
            val push = eye.normalize().scale(exposure * (1.0 - resistance))
            living.push(push)
            if (living is ServerPlayer) knocks[living] = push
        }
        if (building != null && !building.isRemoved && building.health > 0 && building.level === level) {
            building.damageFromMonster(
                this,
                summonedTeam,
                MatchRules.CREEPER_BUILDING_DAMAGE,
                building.blockPos,
                Monsters.CREEPER.displayName,
            )
        }
        val particle = ParticleTypes.EXPLOSION_EMITTER
        val sound = SoundEvents.GENERIC_EXPLODE
        val debris = WeightedList.of<net.minecraft.core.particles.ExplosionParticleInfo>()
        for (player in level.players()) {
            if (player.distanceToSqr(center) > 64.0 * 64.0) continue
            player.connection.send(
                ClientboundExplodePacket(
                    center,
                    MatchRules.CREEPER_POWER.toFloat(),
                    0,
                    Optional.ofNullable(knocks[player]),
                    particle,
                    sound,
                    debris,
                ),
            )
        }
        discard()
    }

    override fun getPolymerEntityType(context: PacketContext): EntityType<*> = EntityType.CREEPER

    override fun registerGoals() {
        goalSelector.addGoal(1, FloatGoal(this))
        goalSelector.addGoal(2, MeleeAttackGoal(this, 1.0, false))
    }

    override fun thunderHit(level: ServerLevel, bolt: net.minecraft.world.entity.LightningBolt) {}

    override fun mobInteract(player: Player, hand: InteractionHand): InteractionResult = InteractionResult.PASS

    override fun killedEntity(level: ServerLevel, entity: LivingEntity, source: DamageSource): Boolean = false

    override fun doHurtTarget(level: ServerLevel, target: Entity): Boolean = false

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

    override fun dropCustomDeathLoot(level: ServerLevel, source: DamageSource, recentlyHit: Boolean) {}

    override fun customServerAiStep(level: ServerLevel) {
        if (Match.frozen(level)) {
            setSwellDir(-1)
            return
        }
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

    private fun teamOf(entity: LivingEntity): String? = when (entity) {
        is Player -> if (entity.isSpectator) null else entity.team?.name ?: ""
        is SummonedUnit -> entity.summonedTeam
        else -> null
    }
}
