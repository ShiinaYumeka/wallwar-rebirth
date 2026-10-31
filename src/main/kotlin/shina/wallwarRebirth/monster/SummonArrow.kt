package shina.wallwarRebirth.monster

import eu.pb4.polymer.core.api.entity.PolymerEntity
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.arrow.Arrow
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.phys.EntityHitResult
import xyz.nucleoid.packettweaker.PacketContext

class SummonArrow(type: EntityType<out SummonArrow>, level: Level) : Arrow(type, level), PolymerEntity {
    var attackDamage: Float = 4f
    var ownerTeam: String = ""

    init {
        pickup = Pickup.DISALLOWED
    }

    override fun getPolymerEntityType(context: PacketContext): EntityType<*> = EntityType.ARROW

    override fun canHitEntity(target: Entity): Boolean {
        if (!super.canHitEntity(target)) return false
        if (target is Player && target.isSpectator) return false
        val ownerEntity = getOwner()
        if (ownerEntity != null && ownerEntity.isAlliedTo(target)) return false
        val other = when (target) {
            is Player -> target.team?.name ?: ""
            is SummonedUnit -> target.summonedTeam
            else -> return false
        }
        return other != ownerTeam
    }

    override fun onHitEntity(result: EntityHitResult) {
        val target = result.entity as? LivingEntity
        val world = level() as? ServerLevel
        if (target != null && world != null) {
            val attacker = getOwner() as? LivingEntity
            val source = if (attacker != null) {
                damageSources().mobAttack(attacker)
            } else {
                damageSources().arrow(this, getOwner())
            }
            target.hurtServer(world, source, attackDamage)
            (attacker as? SummonedUnit)?.mind?.noteExchange()
        }
        discard()
    }

    override fun addAdditionalSaveData(output: ValueOutput) {
        super.addAdditionalSaveData(output)
        output.putFloat("AttackDamage", attackDamage)
        output.putString("OwnerTeam", ownerTeam)
    }

    override fun readAdditionalSaveData(input: ValueInput) {
        super.readAdditionalSaveData(input)
        attackDamage = input.getFloatOr("AttackDamage", 4f)
        ownerTeam = input.getStringOr("OwnerTeam", "")
        pickup = Pickup.DISALLOWED
    }
}
