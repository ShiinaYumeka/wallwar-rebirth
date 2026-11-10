package shina.wallwarRebirth.monster

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import shina.wallwarRebirth.contract.SummonPort

object SummonPorts : SummonPort {
    override fun place(
        level: ServerLevel,
        monsterId: String,
        team: String,
        x: Double,
        y: Double,
        z: Double,
        yaw: Float,
        swift: Boolean,
    ): LivingEntity? {
        val definition = Monsters.byId(monsterId) ?: return null
        return Summoning.place(level, definition, team, x, y, z, yaw, swift)
    }

    override fun teamOf(entity: Entity): String? = when (entity) {
        is SummonedUnit -> entity.summonedTeam
        is Player -> entity.team?.name ?: ""
        else -> null
    }

    override fun isSummon(entity: Entity): Boolean = entity is SummonedUnit

    override fun callMiner(entity: LivingEntity, pos: BlockPos) {
        val unit = entity as? SummonedUnit ?: return
        unit.mind.acceptCall(pos)
    }

    override fun minerCalled(entity: LivingEntity): Boolean {
        val unit = entity as? SummonedUnit ?: return false
        return unit.mind.called()
    }
}
