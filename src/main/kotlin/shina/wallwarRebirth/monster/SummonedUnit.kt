package shina.wallwarRebirth.monster

import net.minecraft.world.entity.LivingEntity

interface SummonedUnit {
    var summonedTeam: String
    var kindId: String
    val mind: SummonedBrain

    fun living(): LivingEntity = this as LivingEntity
}
