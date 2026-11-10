package shina.wallwarRebirth.contract

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity

interface SummonPort {
    fun place(
        level: ServerLevel,
        monsterId: String,
        team: String,
        x: Double,
        y: Double,
        z: Double,
        yaw: Float,
        swift: Boolean = false,
    ): LivingEntity?

    fun teamOf(entity: Entity): String?
    fun isSummon(entity: Entity): Boolean
    fun callMiner(entity: LivingEntity, pos: BlockPos)
    fun minerCalled(entity: LivingEntity): Boolean
}
