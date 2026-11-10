package shina.wallwarRebirth.bootstrap

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import shina.wallwarRebirth.building.Buildings
import shina.wallwarRebirth.building.SiteCores
import shina.wallwarRebirth.monster.SummonedUnit
import shina.wallwarRebirth.monster.SummonedUnits
import shina.wallwarRebirth.targeting.ActorTarget
import shina.wallwarRebirth.targeting.CoreTarget
import shina.wallwarRebirth.targeting.TargetWorld

object LiveTargetWorld : TargetWorld {
    override fun players(level: ServerLevel): List<ActorTarget> =
        level.players().map { player -> ActorTarget(player, player.team?.name ?: "") }

    override fun summons(level: ServerLevel): List<ActorTarget> =
        SummonedUnits.all().mapNotNull { unit ->
            val living = unit.living()
            if (living.level() !== level) return@mapNotNull null
            ActorTarget(living, unit.summonedTeam)
        }

    override fun cores(level: ServerLevel): List<CoreTarget> =
        SiteCores.all().mapNotNull { core ->
            if (core.level !== level || core.isRemoved) return@mapNotNull null
            val definition = Buildings.byId(core.buildingId)
            CoreTarget(
                entityId = core.blockPos.hashCode(),
                pos = core.blockPos,
                team = core.team,
                health = core.health,
                buildingId = core.buildingId,
                defensive = definition?.defensive == true,
                base = core.buildingId == Buildings.BASE.id,
                box = box(level, core.blockPos),
            )
        }

    private fun box(level: ServerLevel, pos: BlockPos): AABB {
        val shape = level.getBlockState(pos).getCollisionShape(level, pos)
        if (shape.isEmpty) return AABB(pos)
        return shape.bounds().move(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
    }
}

fun LivingEntity.asPlayerOrNull(): ServerPlayer? = this as? ServerPlayer
