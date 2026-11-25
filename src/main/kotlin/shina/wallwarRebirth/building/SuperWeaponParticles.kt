package shina.wallwarRebirth.building

import net.minecraft.core.particles.ColorParticleOption
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import shina.wallwarRebirth.rules.MatchRules
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

internal object SuperWeaponParticles {
    private val white = DustParticleOptions(0xF5F8FF, 1.8f)
    private val purple = DustParticleOptions(0xBA54FF, 2.0f)
    private val palePurple = DustParticleOptions(0xA88AC4, 1.25f)
    private val gold = DustParticleOptions(0xFFC35C, 1.2f)
    private val orange = DustParticleOptions(0xFF7E2A, 2.0f)
    private val cyan = DustParticleOptions(0x46E8EE, 1.6f)
    private val flash = ColorParticleOption.create(ParticleTypes.FLASH, 1f, 1f, 1f)

    private const val PUBLIC_RANGE = 96.0
    private const val SHOCKWAVE_TICKS = 8
    private const val ARRIVAL_TICKS = 12

    private class Animation(
        val level: ServerLevel,
        val start: Long,
        val duration: Int,
        val draw: (Int) -> Unit,
    )

    private val animations = ArrayList<Animation>()

    fun tick(server: MinecraftServer) {
        animations.removeIf { effect ->
            if (effect.level.server !== server) return@removeIf true
            val age = effect.level.gameTime - effect.start
            if (age in 1L..effect.duration.toLong()) effect.draw(age.toInt())
            age >= effect.duration
        }
    }

    fun preview(level: ServerLevel, team: String, center: Vec3, strike: Boolean, age: Long) {
        val drawOutline = age % 10L == 0L
        val drawMarker = age % 3L == 0L
        if (strike && !drawOutline && !drawMarker) return
        if (!strike && age % 4L != 0L) return
        val viewers = level.players().filter { it.team?.name == team }
        if (viewers.isEmpty()) return
        if (strike) {
            if (drawOutline) {
                ring(viewers, center, MatchRules.GUIDED_CANNON_POWER, 40, purple)
                for (segment in 0 until 24) {
                    val angle = segment * PI / 12.0
                    point(viewers, palePurple, around(center, MatchRules.GUIDED_CANNON_FALLOFF, angle))
                    point(viewers, palePurple, around(center, MatchRules.GUIDED_CANNON_FALLOFF, angle + 0.055))
                }
                point(viewers, white, center)
                for (distance in doubleArrayOf(-2.0, -1.0, 1.0, 2.0)) {
                    point(viewers, white, center.add(distance, 0.0, 0.0))
                    point(viewers, white, center.add(0.0, 0.0, distance))
                }
            }
            if (drawMarker) {
                val angle = (age / 3L % 4L) * PI / 2.0
                val edge = around(center, MatchRules.GUIDED_CANNON_POWER, angle)
                point(viewers, white, edge)
                point(viewers, white, edge.add(0.0, 0.8, 0.0))
            }
        } else {
            val reach = 2.4 - (age / 4L % 4L) * 0.45
            point(viewers, white, center)
            point(viewers, white, center.add(0.0, 0.6, 0.0))
            for (x in intArrayOf(-1, 1)) {
                for (z in intArrayOf(-1, 1)) {
                    val corner = center.add(x * reach, 0.0, z * reach)
                    point(viewers, cyan, corner)
                    point(viewers, cyan, corner.add(-x * 0.45, 0.0, 0.0))
                    point(viewers, cyan, corner.add(0.0, 0.0, -z * 0.45))
                }
            }
        }
    }

    fun launch(level: ServerLevel, origin: Vec3) {
        val viewers = nearby(level, origin)
        ring(viewers, origin, 1.7, 14, purple)
        point(viewers, flash, origin)
        cloud(viewers, ParticleTypes.FIREWORK, origin, 22, 0.8f, 0.25f, 0.09f)
    }

    fun trail(level: ServerLevel, from: Vec3, to: Vec3, descending: Boolean, tick: Int) {
        val delta = to.subtract(from)
        val length = delta.length()
        if (length < 0.2) return
        val viewers = nearby(level, from.lerp(to, 0.5))
        if (viewers.isEmpty()) return
        val steps = ceil(length / 2.5).toInt().coerceIn(1, 8)
        for (index in 1..steps) {
            val center = from.add(delta.scale(index.toDouble() / steps))
            val angle = tick * 1.8 + index * 0.85
            point(viewers, purple, around(center, if (descending) 1.05 else 0.45, angle))
            if (descending && index % 2 == 0) point(viewers, gold, around(center, 1.35, angle + PI))
        }
        if (descending) {
            point(viewers, white, to)
            cloud(viewers, ParticleTypes.END_ROD, to, 4, 0.18f, 0.18f, 0.02f)
        }
    }

    fun fallingWarning(level: ServerLevel, center: Vec3, progress: Double) {
        val viewers = nearby(level, center)
        val radius = 7.0 - progress * 5.6
        ring(viewers, center, radius, 16, orange)
        point(viewers, gold, center)
    }

    fun impact(level: ServerLevel, center: Vec3, radius: Double) {
        val ground = center.add(0.0, 0.65, 0.0)
        point(nearby(level, ground), flash, ground)
        animate(level, SHOCKWAVE_TICKS) { age ->
            val viewers = nearby(level, ground)
            val edge = radius * age / SHOCKWAVE_TICKS
            val points = 8 + age * 3
            ring(viewers, ground, edge, points, if (age % 2 == 0) purple else white)
            for (index in 0 until 8) {
                val angle = (index + age * 0.37) * PI / 4.0
                val inside = edge - level.random.nextDouble() * radius / SHOCKWAVE_TICKS
                point(viewers, ParticleTypes.EXPLOSION, around(ground, inside, angle))
            }
            if (age == SHOCKWAVE_TICKS) {
                for (index in 0 until 12) {
                    val edgePoint = around(ground, radius, index * PI / 6.0)
                    cloud(viewers, ParticleTypes.CLOUD, edgePoint, 4, 0.65f, 0.25f, 0.02f)
                }
            }
        }
    }

    fun arrival(level: ServerLevel, feet: Vec3) {
        val ground = feet.add(0.0, 0.15, 0.0)
        val viewers = nearby(level, ground)
        point(viewers, flash, ground.add(0.0, 0.9, 0.0))
        for (step in 0..6) {
            point(viewers, if (step % 2 == 0) white else cyan, ground.add(0.0, step * 0.5, 0.0))
        }
        animate(level, ARRIVAL_TICKS) { age ->
            if (age % 2 != 0) return@animate
            val audience = nearby(level, ground)
            if (age <= 8) ring(audience, ground, 0.65 + age * 0.24, 16, cyan)
            if (age in 4..10) ring(audience, ground.add(0.0, 0.35, 0.0), 0.45 + (age - 4) * 0.22, 12, white)
            val height = 0.4 + age * 0.23
            val sparkRadius = 1.1 * (1.0 - age / 14.0)
            for (index in 0 until 4) {
                val spark = around(ground.add(0.0, height, 0.0), sparkRadius, index * PI / 2.0 + age * 0.35)
                point(audience, ParticleTypes.END_ROD, spark)
            }
        }
    }

    fun followerArrival(level: ServerLevel, feet: Vec3) {
        val viewers = nearby(level, feet, range = 32.0)
        ring(viewers, feet.add(0.0, 0.15, 0.0), 0.65, 6, cyan)
    }

    private fun animate(level: ServerLevel, duration: Int, draw: (Int) -> Unit) {
        animations.add(Animation(level, level.gameTime, duration, draw))
    }

    private fun nearby(level: ServerLevel, point: Vec3, range: Double = PUBLIC_RANGE): List<ServerPlayer> =
        level.players().filter { it.distanceToSqr(point) <= range * range }

    private fun around(center: Vec3, radius: Double, angle: Double): Vec3 =
        center.add(cos(angle) * radius, 0.0, sin(angle) * radius)

    private fun ring(viewers: List<ServerPlayer>, center: Vec3, radius: Double, count: Int, particle: ParticleOptions) {
        if (viewers.isEmpty()) return
        for (index in 0 until count) {
            point(viewers, particle, around(center, radius, index * 2.0 * PI / count))
        }
    }

    private fun point(viewers: List<ServerPlayer>, particle: ParticleOptions, pos: Vec3) =
        cloud(viewers, particle, pos, 1, 0f, 0f, 0f)

    private fun cloud(
        viewers: List<ServerPlayer>,
        particle: ParticleOptions,
        pos: Vec3,
        count: Int,
        spread: Float,
        spreadY: Float,
        speed: Float,
    ) {
        if (viewers.isEmpty()) return
        val packet = ClientboundLevelParticlesPacket(
            particle, true, false, pos.x, pos.y, pos.z, spread, spreadY, spread, speed, count,
        )
        for (viewer in viewers) viewer.connection.send(packet)
    }
}
