package shina.wallwarRebirth.targeting

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import shina.wallwarRebirth.building.BuildingMining
import shina.wallwarRebirth.monster.SummonedUnit
import shina.wallwarRebirth.rules.MatchRules

object HoldTargeting {
    fun select(
        world: TargetWorld,
        level: ServerLevel,
        origin: Vec3,
        team: String,
        range: Double,
        self: LivingEntity,
    ): LivingEntity? {
        val wrecking = wreckingEnemies(world, level, origin, team, range, self)
        if (wrecking.isNotEmpty()) return nearest(wrecking, origin)
        val summons = world.summons(level).map { it.entity }.filter { candidate ->
            candidate !== self &&
                candidate.isAlive &&
                Targeting.hostile(team, (candidate as? SummonedUnit)?.summonedTeam ?: "") &&
                Targeting.distanceToBox(origin, candidate.boundingBox) <= range
        }
        if (summons.isNotEmpty()) return nearest(summons, origin)
        val players = world.players(level).map { it.entity }.filter { player ->
            player.isAlive &&
                Targeting.hostile(team, (player.team?.name ?: "")) &&
                Targeting.distanceToBox(origin, player.boundingBox) <= range
        }
        return nearest(players, origin)
    }

    private fun wreckingEnemies(
        world: TargetWorld,
        level: ServerLevel,
        origin: Vec3,
        team: String,
        range: Double,
        self: LivingEntity,
    ): List<LivingEntity> {
        val found = ArrayList<LivingEntity>()
        for (player in BuildingMining.minersOf(level, team)) {
            if (!player.isAlive || !Targeting.canTargetPlayer(player)) continue
            if (!Targeting.hostile(team, player.team?.name ?: "")) continue
            if (Targeting.distanceToBox(origin, player.boundingBox) > range) continue
            found.add(player)
        }
        for (actor in world.summons(level)) {
            val living = actor.entity
            if (living === self || !living.isAlive) continue
            if (!Targeting.hostile(team, actor.team)) continue
            if (Targeting.distanceToBox(origin, living.boundingBox) > range) continue
            val unit = living as? SummonedUnit ?: continue
            if (!unit.mind.siegingTeam(team)) continue
            found.add(living)
        }
        return found
    }

    private fun nearest(candidates: List<LivingEntity>, origin: Vec3): LivingEntity? =
        Targeting.nearest(
            candidates,
            distance = { Targeting.distanceToBox(origin, it.boundingBox) },
            health = { it.health },
            id = { it.id },
            tie = MatchRules.TOWER_TIE_DISTANCE,
        )
}
