package shina.wallwarRebirth.building

import eu.pb4.polymer.core.api.entity.PolymerEntity
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BlockTags
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.arrow.AbstractArrow
import net.minecraft.world.entity.projectile.arrow.Arrow
import shina.wallwarRebirth.mixin.AbstractArrowAccess
import net.minecraft.world.level.Level
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import shina.wallwarRebirth.monster.SummonedUnit
import shina.wallwarRebirth.targeting.Targeting
import xyz.nucleoid.packettweaker.PacketContext

class TowerArrow(type: EntityType<out TowerArrow>, level: Level) : Arrow(type, level), PolymerEntity {
    var towerTeam: String = ""

    init {
        pickup = Pickup.DISALLOWED
        isNoGravity = false
    }

    override fun getPolymerEntityType(context: PacketContext): EntityType<*> = EntityType.ARROW

    override fun getTypeName(): Component = EntityType.ARROW.description

    fun setPierceHits(hits: Int) {
        val base: AbstractArrow = this
        (base as AbstractArrowAccess).wallwarSetPierceLevel((hits - 1).toByte())
    }

    override fun canHitEntity(target: Entity): Boolean {
        if (!super.canHitEntity(target)) return false
        if (target is Player && !Targeting.canTargetPlayer(target)) return false
        val other = when (target) {
            is Player -> target.team?.name ?: ""
            is SummonedUnit -> target.summonedTeam
            else -> return false
        }
        return other != towerTeam
    }

    override fun onHitBlock(result: BlockHitResult) {
        val server = level() as? ServerLevel
        if (server != null) {
            val pos = result.blockPos
            val state = server.getBlockState(pos)
            if (state.`is`(BlockTags.PLANKS)) {
                server.destroyBlock(pos, true, this)
                discard()
                return
            }
        }
        super.onHitBlock(result)
    }

    override fun addAdditionalSaveData(output: ValueOutput) {
        super.addAdditionalSaveData(output)
        output.putString("TowerTeam", towerTeam)
    }

    override fun readAdditionalSaveData(input: ValueInput) {
        super.readAdditionalSaveData(input)
        towerTeam = input.getStringOr("TowerTeam", "")
        pickup = Pickup.DISALLOWED
        isNoGravity = false
    }
}
