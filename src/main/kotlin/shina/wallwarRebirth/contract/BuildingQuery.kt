package shina.wallwarRebirth.contract

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB

data class CoreView(
    val pos: BlockPos,
    val team: String,
    val health: Int,
    val buildingId: String,
    val defensive: Boolean,
    val base: Boolean,
    val box: AABB,
)

data class StrikePoint(
    val corePos: BlockPos,
    val struck: BlockPos,
    val team: String,
    val health: Int,
    val buildingId: String,
    val defensive: Boolean,
)

interface BuildingQuery {
    fun cores(level: ServerLevel): List<CoreView>
    fun strikePoints(level: ServerLevel, origin: BlockPos, radius: Int): List<StrikePoint>
    fun damage(
        level: ServerLevel,
        corePos: BlockPos,
        amount: Int,
        struck: BlockPos,
        attacker: LivingEntity,
        attackerTeam: String,
        attackerName: String,
    )
    fun produceCycle(level: ServerLevel, corePos: BlockPos)
}
