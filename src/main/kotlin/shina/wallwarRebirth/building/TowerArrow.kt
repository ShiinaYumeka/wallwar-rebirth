package shina.wallwarRebirth.building

import eu.pb4.polymer.core.api.entity.PolymerEntity
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.arrow.Arrow
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import shina.wallwarRebirth.monster.SummonedUnit
import xyz.nucleoid.packettweaker.PacketContext

class TowerArrow(type: EntityType<out TowerArrow>, level: Level) : Arrow(type, level), PolymerEntity {
    var towerTeam: String = ""

    init {
        pickup = Pickup.DISALLOWED
        isNoGravity = false
    }

    override fun getPolymerEntityType(context: PacketContext): EntityType<*> = EntityType.ARROW

    override fun canHitEntity(target: Entity): Boolean {
        if (!super.canHitEntity(target)) return false
        val other = when (target) {
            is Player -> {
                if (target.isSpectator) return false
                target.team?.name ?: ""
            }
            is SummonedUnit -> target.summonedTeam
            else -> return false
        }
        return other != towerTeam
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
