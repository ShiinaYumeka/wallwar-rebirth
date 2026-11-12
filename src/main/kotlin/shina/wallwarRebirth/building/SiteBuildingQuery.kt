package shina.wallwarRebirth.building

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import shina.wallwarRebirth.contract.BuildingQuery
import shina.wallwarRebirth.contract.CoreView
import shina.wallwarRebirth.contract.StrikePoint
import kotlin.math.ceil

object SiteBuildingQuery : BuildingQuery {
    override fun cores(level: ServerLevel): List<CoreView> =
        SiteCores.all().mapNotNull { core ->
            if (core.level !== level || core.isRemoved) return@mapNotNull null
            val definition = Buildings.byId(core.buildingId)
            CoreView(
                pos = core.blockPos,
                team = core.team,
                health = core.health,
                buildingId = core.buildingId,
                defensive = definition?.defensive == true,
                base = core.buildingId == Buildings.BASE.id,
                box = box(level, core.blockPos),
            )
        }

    override fun strikePoints(level: ServerLevel, origin: BlockPos, radius: Int): List<StrikePoint> {
        val points = ArrayList<StrikePoint>()
        fun add(core: SiteCoreBlockEntity, pos: BlockPos) {
            if (FarmlandWork.isPart(core, pos)) return
            if (core.isRemoved || core.health <= 0 || core.buildingId.isEmpty()) return
            points.add(
                StrikePoint(
                    core.blockPos,
                    pos,
                    core.team,
                    core.health,
                    core.buildingId,
                    Buildings.byId(core.buildingId)?.defensive == true,
                ),
            )
        }
        for (core in SiteCores.all()) {
            if (core.level !== level) continue
            add(core, core.blockPos)
            for (part in core.partPositions) add(core, part)
        }
        val reach = ceil(radius.toDouble()).toInt() + 1
        for (x in -reach..reach) {
            for (y in -reach..reach) {
                for (z in -reach..reach) {
                    val pos = origin.offset(x, y, z)
                    val state = level.getBlockState(pos)
                    if (state.block !is SiteStructureBlock && state.block !is SitePartBlock) continue
                    val owner = BuildingMining.resolve(level, pos, state) ?: continue
                    add(owner, pos)
                }
            }
        }
        return points
    }

    override fun damage(
        level: ServerLevel,
        corePos: BlockPos,
        amount: Int,
        struck: BlockPos,
        attacker: LivingEntity,
        attackerTeam: String,
        attackerName: String,
    ) {
        val core = level.getBlockEntity(corePos) as? SiteCoreBlockEntity ?: return
        core.damageFromMonster(attacker, attackerTeam, amount, struck, attackerName)
    }

    override fun produceCycle(level: ServerLevel, corePos: BlockPos) {
        val core = level.getBlockEntity(corePos) as? SiteCoreBlockEntity ?: return
        core.produceCycle()
    }

    private fun box(level: ServerLevel, pos: BlockPos): AABB {
        val shape = level.getBlockState(pos).getCollisionShape(level, pos)
        if (shape.isEmpty) return AABB(pos)
        return shape.bounds().move(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
    }
}
