package shina.wallwarRebirth.building

import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import shina.wallwarRebirth.match.Match
import shina.wallwarRebirth.monster.SummonedUnit
import shina.wallwarRebirth.rules.MatchRules
import shina.wallwarRebirth.targeting.Targeting
import shina.wallwarRebirth.targeting.TargetWorlds
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object QuakeWeapon {
    private data class Key(val id: UUID, val team: String)

    private val stacks = HashMap<Key, Int>()

    fun tick(site: SiteCoreBlockEntity, level: ServerLevel) {
        if (Match.phase != Match.Phase.FIGHT) return
        val index = (site.buildingLevel - 1).coerceIn(0, MatchRules.QUAKE_INTERVAL.lastIndex)
        val interval = MatchRules.QUAKE_INTERVAL[index]
        val origin = Vec3.atCenterOf(site.blockPos)
        val target = select(site, level, origin, MatchRules.QUAKE_RANGE)
        val relay = if (target == null && !site.diffuse.isEmpty) relayOf(site, level) else null
        val (nextAge, fire) = IntervalGate.step(site.actionAge, interval, target != null || relay != null)
        site.actionAge = nextAge
        if (!fire) return
        site.setChanged()
        if (target != null) {
            strike(site, level, target, link = null)
        } else if (relay != null) {
            strike(site, level, relay.target, link = relay.ally)
        }
    }

    fun clear(entity: LivingEntity) {
        val id = entity.uuid
        stacks.keys.filter { it.id == id }.forEach { stacks.remove(it) }
    }

    fun clearAll() {
        stacks.clear()
    }

    private data class Relay(val ally: SiteCoreBlockEntity, val target: LivingEntity)

    private fun relayOf(site: SiteCoreBlockEntity, level: ServerLevel): Relay? {
        var best: Relay? = null
        var bestOrigin = Vec3.ZERO
        for (ally in linkedTowers(site, level)) {
            val origin = Vec3.atCenterOf(ally.blockPos)
            for (candidate in eligibleAround(site, level, origin, MatchRules.QUAKE_RANGE)) {
                val current = best
                if (current == null || prefer(candidate, origin, current.target, bestOrigin)) {
                    best = Relay(ally, candidate)
                    bestOrigin = origin
                }
            }
        }
        return best
    }

    private fun prefer(candidate: LivingEntity, candidateOrigin: Vec3, best: LivingEntity, bestOrigin: Vec3): Boolean {
        val maxDelta = candidate.maxHealth.compareTo(best.maxHealth)
        if (maxDelta != 0) return maxDelta > 0
        val candidateDist = tieDistance(candidateOrigin, candidate)
        val bestDist = tieDistance(bestOrigin, best)
        if (candidateDist != bestDist) return candidateDist < bestDist
        val healthDelta = candidate.health.compareTo(best.health)
        if (healthDelta != 0) return healthDelta < 0
        return candidate.id < best.id
    }

    private fun tieDistance(origin: Vec3, entity: LivingEntity): Long =
        (Targeting.distanceToBox(origin, entity.boundingBox) / MatchRules.TOWER_TIE_DISTANCE).toLong()

    private fun linkedTowers(site: SiteCoreBlockEntity, level: ServerLevel): List<SiteCoreBlockEntity> {
        val reach = MatchRules.QUAKE_RANGE * MatchRules.QUAKE_RANGE
        return SiteCores.all().filter { core ->
            core !== site &&
                core.level === level &&
                !core.isRemoved &&
                core.health > 0 &&
                core.buildingId == Buildings.QUAKE.id &&
                core.team == site.team &&
                core.blockPos.distSqr(site.blockPos) <= reach
        }
    }

    private fun strike(site: SiteCoreBlockEntity, level: ServerLevel, preferred: LivingEntity, link: SiteCoreBlockEntity?) {
        val origin = Vec3.atCenterOf((link ?: site).blockPos)
        val range = MatchRules.QUAKE_RANGE
        val main = if (eligible(site, preferred) && Targeting.distanceToBox(origin, preferred.boundingBox) <= range) {
            preferred
        } else {
            select(site, level, origin, range)
        }
        if (main == null) return
        val splash = if (site.reinforce.isEmpty) MatchRules.QUAKE_SPLASH else MatchRules.QUAKE_SPLASH_REINFORCED
        val increment = if (site.reinforceTwo.isEmpty) MatchRules.QUAKE_STACK_INCREMENT else MatchRules.QUAKE_STACK_INCREMENT_II
        val cap = if (site.reinforceTwo.isEmpty) MatchRules.QUAKE_STACK_CAP else MatchRules.QUAKE_STACK_CAP_II
        val center = main.boundingBox.center
        val victims = eligibleAround(site, level, center, splash)
        val crystal = crystal(level, site)
        if (link != null) beam(level, crystal, crystal(level, link))
        val source = level.damageSources().generic()
        for (victim in victims) {
            val stacked = victim !is Player
            val current = if (stacked) stacks[Key(victim.uuid, site.team)] ?: 0 else 0
            val extra = if (stacked) min(current * increment, cap) else 0
            victim.hurtServer(level, source, (MatchRules.QUAKE_DAMAGE + extra).toFloat())
            if (stacked && current < MatchRules.QUAKE_STACK_MAX) {
                stacks[Key(victim.uuid, site.team)] = current + 1
            }
            if (link == null) beam(level, crystal, victim.boundingBox.center)
        }
        burst(level, center, splash)
    }

    private fun crystal(level: ServerLevel, site: SiteCoreBlockEntity): Vec3 {
        var found: net.minecraft.core.BlockPos? = null
        for (pos in site.partPositions) {
            if (pos.x != site.blockPos.x || pos.z != site.blockPos.z) continue
            val block = level.getBlockState(pos).block
            if (block != Blocks.BUDDING_AMETHYST && block != Blocks.AMETHYST_BLOCK) continue
            if (found == null || pos.y > found.y) found = pos
        }
        return Vec3.atCenterOf(found ?: site.blockPos.above(5))
    }

    private fun beam(level: ServerLevel, from: Vec3, to: Vec3) {
        val delta = to.subtract(from)
        val length = delta.length()
        if (length < 0.2) return
        val steps = max(1, ceil(length / 0.7).toInt())
        for (index in 0..steps) {
            val point = from.add(delta.scale(index.toDouble() / steps))
            level.sendParticles(PURPLE, true, true, point.x, point.y, point.z, 1, 0.02, 0.02, 0.02, 0.0)
        }
    }

    private fun burst(level: ServerLevel, center: Vec3, radius: Double) {
        val count = (36 + radius * 10).toInt()
        repeat(count) { index ->
            val angle = level.random.nextDouble() * Math.PI * 2.0
            val dist = radius * sqrt(level.random.nextDouble())
            val x = center.x + cos(angle) * dist
            val y = center.y + (level.random.nextDouble() - 0.35) * radius * 0.55
            val z = center.z + sin(angle) * dist
            val particle = if (index % 2 == 0) WHITE else BLUE
            level.sendParticles(particle, true, true, x, y, z, 1, 0.04, 0.04, 0.04, 0.0)
        }
        level.sendParticles(ParticleTypes.END_ROD, true, true, center.x, center.y, center.z, 14, radius * 0.35, 0.35, radius * 0.35, 0.02)
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, true, true, center.x, center.y, center.z, 18, radius * 0.4, 0.25, radius * 0.4, 0.01)
    }

    private fun select(site: SiteCoreBlockEntity, level: ServerLevel, origin: Vec3, range: Double): LivingEntity? {
        val candidates = eligibleAround(site, level, origin, range)
        if (candidates.isEmpty()) return null
        return candidates.minWithOrNull(
            compareByDescending<LivingEntity> { it.maxHealth }
                .thenBy { (Targeting.distanceToBox(origin, it.boundingBox) / MatchRules.TOWER_TIE_DISTANCE).toLong() }
                .thenBy { it.health }
                .thenBy { it.id },
        )
    }

    private fun eligibleAround(site: SiteCoreBlockEntity, level: ServerLevel, origin: Vec3, range: Double): List<LivingEntity> {
        val found = ArrayList<LivingEntity>()
        val world = TargetWorlds.world
        for (actor in world.players(level)) {
            val player = actor.entity as? Player ?: continue
            if (!eligiblePlayer(site, player)) continue
            if (Targeting.distanceToBox(origin, player.boundingBox) > range) continue
            found.add(player)
        }
        for (actor in world.summons(level)) {
            val living = actor.entity
            if (!living.isAlive) continue
            if (!Targeting.hostile(site.team, actor.team)) continue
            if (Targeting.distanceToBox(origin, living.boundingBox) > range) continue
            found.add(living)
        }
        return found
    }

    private fun eligible(site: SiteCoreBlockEntity, entity: LivingEntity): Boolean {
        if (!entity.isAlive) return false
        return when (entity) {
            is Player -> eligiblePlayer(site, entity)
            is SummonedUnit -> Targeting.hostile(site.team, entity.summonedTeam)
            else -> false
        }
    }

    private fun eligiblePlayer(site: SiteCoreBlockEntity, player: Player): Boolean {
        if (!player.isAlive || !Targeting.canTargetPlayer(player)) return false
        return Targeting.hostile(site.team, player.team?.name ?: "")
    }

    private val PURPLE = DustParticleOptions(0xC26BFF, 1.45f)
    private val WHITE = DustParticleOptions(0xF7F8FF, 1.25f)
    private val BLUE = DustParticleOptions(0x3C78FF, 1.25f)
}
