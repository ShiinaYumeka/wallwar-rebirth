package shina.wallwarRebirth.building

import eu.pb4.polymer.core.api.entity.PolymerEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.protocol.game.ClientboundExplodePacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.random.WeightedList
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.item.PrimedTnt
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.ServerExplosion
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import shina.wallwarRebirth.match.Match
import shina.wallwarRebirth.match.MatchRules
import shina.wallwarRebirth.monster.ModMonsters
import shina.wallwarRebirth.monster.SummonedUnit
import xyz.nucleoid.packettweaker.PacketContext
import java.util.Optional
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class CannonShell(type: EntityType<out CannonShell>, level: Level) : PrimedTnt(type, level), PolymerEntity {
    var cannonTeam: String = ""
    var corePos: BlockPos = BlockPos.ZERO
    var centerDamage: Double = 0.0
    var powerRadius: Double = MatchRules.CANNON_POWER
    var falloff: Double = MatchRules.CANNON_FALLOFF
    private var leftCore = false

    init {
        setFuse(FUSE_HOLD)
    }

    fun prime(
        team: String,
        core: BlockPos,
        centerDamage: Double,
        powerRadius: Double,
        falloff: Double,
        motion: Vec3,
        origin: Vec3,
    ) {
        cannonTeam = team
        corePos = core
        this.centerDamage = centerDamage
        this.powerRadius = powerRadius
        this.falloff = falloff
        leftCore = false
        setFuse(FUSE_HOLD)
        snapTo(origin.x, origin.y, origin.z, 0f, 0f)
        setDeltaMovement(motion)
    }

    override fun getPolymerEntityType(context: PacketContext): EntityType<*> = EntityType.TNT

    override fun tick() {
        val server = level() as? ServerLevel ?: return
        if (Match.frozen(server)) return
        setFuse(FUSE_HOLD)
        val velocity = deltaMovement.add(0.0, -MatchRules.CANNON_GRAVITY, 0.0)
        val from = position()
        val hit = firstSolid(server, from, velocity)
        if (hit != null) {
            setPos(hit.point)
            setDeltaMovement(velocity)
            detonate(server, hit.cell)
            return
        }
        val next = from.add(velocity)
        setDeltaMovement(velocity)
        setPos(next.x, next.y, next.z)
        if (next.y < server.minY - 16 || tickCount > MAX_FLIGHT_TICKS) discard()
    }

    private fun firstSolid(level: ServerLevel, from: Vec3, velocity: Vec3): Impact? {
        val distance = velocity.length()
        if (distance < 1.0e-8) return null
        val steps = max(1, ceil(distance / STEP).toInt())
        val step = velocity.scale(1.0 / steps)
        var point = from
        repeat(steps) {
            point = point.add(step)
            val cell = BlockPos.containing(point)
            if (cell == corePos && !leftCore) return@repeat
            if (cell != corePos) leftCore = true
            if (solidPoint(level, point)) return Impact(cell, point)
        }
        return null
    }

    private fun solidPoint(level: ServerLevel, point: Vec3): Boolean {
        val cell = BlockPos.containing(point)
        val shape = level.getBlockState(cell).getCollisionShape(level, cell)
        if (shape.isEmpty) return false
        val x = point.x - cell.x
        val y = point.y - cell.y
        val z = point.z - cell.z
        var inside = false
        shape.forAllBoxes { minX, minY, minZ, maxX, maxY, maxZ ->
            if (x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ) inside = true
        }
        return inside
    }

    private fun detonate(level: ServerLevel, cell: BlockPos) {
        val center = Vec3.atCenterOf(cell)
        val knocks = HashMap<ServerPlayer, Vec3>()
        val area = AABB.ofSize(center, (falloff + 2.0) * 2.0, (falloff + 2.0) * 2.0, (falloff + 2.0) * 2.0)
        for (entity in level.getEntities(this, area)) {
            val living = entity as? LivingEntity ?: continue
            if (!living.isAlive) continue
            val other = teamOf(living) ?: continue
            if (other.isEmpty() || other == cannonTeam) continue
            val distance = sqrt(living.distanceToSqr(center))
            val norm = distance / falloff
            if (norm > 1.0) continue
            val seen = ServerExplosion.getSeenPercent(center, living).toDouble()
            val exposure = (1.0 - norm) * seen
            if (exposure > 0.0) {
                val raw = centerDamage * (exposure * exposure + exposure) / 2.0
                if (raw > 0.0) {
                    living.hurtServer(level, level.damageSources().explosion(this, null), raw.toFloat())
                }
                val eye = living.eyePosition.subtract(center)
                if (eye.lengthSqr() > 1.0e-8) {
                    val resistance = living.getAttributeValue(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE)
                    val push = eye.normalize().scale(exposure * (1.0 - resistance))
                    living.push(push)
                    if (living is ServerPlayer) knocks[living] = push
                }
            }
        }
        for (site in SiteCores.all()) {
            if (site.level !== level || site.isRemoved || site.health <= 0 || site.buildingId.isEmpty()) continue
            if (site.blockPos == corePos && site.buildingId == Buildings.CANNON.id) continue
            if (cannonTeam == site.team) continue
            val box = coreBox(level, site.blockPos)
            if (distanceToBox(center, box) > powerRadius) continue
            val amount = floor(centerDamage * MatchRules.CANNON_BUILDING_FRACTION + 0.5).toInt()
            site.damage(amount, site.blockPos)
        }
        discard()
        val particle = ParticleTypes.EXPLOSION_EMITTER
        val sound = SoundEvents.GENERIC_EXPLODE
        val debris = WeightedList.of<net.minecraft.core.particles.ExplosionParticleInfo>()
        for (player in level.players()) {
            if (player.distanceToSqr(center) > 64.0 * 64.0) continue
            player.connection.send(
                ClientboundExplodePacket(
                    center,
                    powerRadius.toFloat(),
                    0,
                    Optional.ofNullable(knocks[player]),
                    particle,
                    sound,
                    debris,
                ),
            )
        }
    }

    override fun addAdditionalSaveData(output: net.minecraft.world.level.storage.ValueOutput) {
        super.addAdditionalSaveData(output)
        output.putString("CannonTeam", cannonTeam)
        output.putInt("CoreX", corePos.x)
        output.putInt("CoreY", corePos.y)
        output.putInt("CoreZ", corePos.z)
        output.putDouble("CenterDamage", centerDamage)
        output.putDouble("PowerRadius", powerRadius)
        output.putDouble("Falloff", falloff)
        output.putBoolean("LeftCore", leftCore)
    }

    override fun readAdditionalSaveData(input: net.minecraft.world.level.storage.ValueInput) {
        super.readAdditionalSaveData(input)
        cannonTeam = input.getStringOr("CannonTeam", "")
        corePos = BlockPos(
            input.getIntOr("CoreX", 0),
            input.getIntOr("CoreY", 0),
            input.getIntOr("CoreZ", 0),
        )
        centerDamage = input.getDoubleOr("CenterDamage", 0.0)
        powerRadius = input.getDoubleOr("PowerRadius", MatchRules.CANNON_POWER)
        falloff = input.getDoubleOr("Falloff", MatchRules.CANNON_FALLOFF)
        leftCore = input.getBooleanOr("LeftCore", false)
        setFuse(FUSE_HOLD)
    }

    private fun teamOf(entity: LivingEntity): String? = when (entity) {
        is Player -> if (entity.isSpectator) null else entity.team?.name ?: ""
        is SummonedUnit -> entity.summonedTeam
        else -> null
    }

    private data class Impact(val cell: BlockPos, val point: Vec3)

    companion object {
        private const val FUSE_HOLD = 30000
        private const val STEP = 0.2
        private const val MAX_FLIGHT_TICKS = 30 * MatchRules.TICKS_PER_SECOND

        fun launch(level: ServerLevel, site: SiteCoreBlockEntity, aim: Vec3, centerDamage: Double, wide: Boolean) {
            val origin = Vec3.atCenterOf(site.blockPos)
            val motion = CannonFlight.velocity(origin, aim, MatchRules.CANNON_LAUNCH_SPEED) ?: return
            val shell = CannonShell(ModMonsters.CANNON_SHELL, level)
            shell.prime(
                team = site.team,
                core = site.blockPos.immutable(),
                centerDamage = centerDamage,
                powerRadius = if (wide) MatchRules.CANNON_POWER_WIDE else MatchRules.CANNON_POWER,
                falloff = if (wide) MatchRules.CANNON_FALLOFF_WIDE else MatchRules.CANNON_FALLOFF,
                motion = motion,
                origin = origin,
            )
            level.addFreshEntity(shell)
        }

        private fun coreBox(level: ServerLevel, pos: BlockPos): AABB {
            val shape = level.getBlockState(pos).getCollisionShape(level, pos)
            if (shape.isEmpty) return AABB(pos)
            return shape.bounds().move(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
        }

        private fun distanceToBox(origin: Vec3, box: AABB): Double {
            val dx = if (origin.x in box.minX..box.maxX) 0.0 else origin.x.coerceIn(box.minX, box.maxX) - origin.x
            val dy = if (origin.y in box.minY..box.maxY) 0.0 else origin.y.coerceIn(box.minY, box.maxY) - origin.y
            val dz = if (origin.z in box.minZ..box.maxZ) 0.0 else origin.z.coerceIn(box.minZ, box.maxZ) - origin.z
            return sqrt(dx * dx + dy * dy + dz * dz)
        }
    }
}

object CannonFlight {
    private const val HIT = 0.05
    private const val MAX_TICKS = 12000

    fun velocity(start: Vec3, aim: Vec3, baseSpeed: Double): Vec3? {
        val offset = aim.subtract(start)
        val distance = sqrt(offset.x * offset.x + offset.z * offset.z)
        var speed = baseSpeed
        if (highAngle(distance, offset.y, speed) == null) {
            var ceiling = baseSpeed
            var found = false
            while (ceiling < 256.0) {
                ceiling *= 2.0
                if (highAngle(distance, offset.y, ceiling) != null) {
                    found = true
                    break
                }
            }
            if (!found) return null
            var low = baseSpeed
            var high = ceiling
            repeat(24) {
                val mid = (low + high) / 2.0
                if (highAngle(distance, offset.y, mid) != null) high = mid else low = mid
            }
            speed = high
        }
        val angle = highAngle(distance, offset.y, speed) ?: return null
        val cosine = cos(angle)
        val sine = sin(angle)
        val dirX = if (distance < 1.0e-4) 0.0 else offset.x / distance
        val dirZ = if (distance < 1.0e-4) 0.0 else offset.z / distance
        return Vec3(dirX * speed * cosine, speed * sine, dirZ * speed * cosine)
    }

    private fun highAngle(distance: Double, height: Double, speed: Double): Double? {
        if (distance < 1.0e-4) {
            return if (height <= HIT || apex(speed) + HIT >= height) Math.PI / 2.0 else null
        }
        var peakAngle = 0.01
        var peakY = arrivalY(distance, speed, peakAngle)
        var theta = 0.03
        while (theta < Math.PI / 2.0) {
            val here = arrivalY(distance, speed, theta)
            if (here > peakY) {
                peakY = here
                peakAngle = theta
            }
            theta += 0.02
        }
        if (peakY < height - HIT) return null
        var low = peakAngle
        var high = min(peakAngle + 0.02, Math.PI / 2.0 - 1.0e-4)
        var steps = 0
        while (arrivalY(distance, speed, high) >= height && high < Math.PI / 2.0 - 1.0e-3 && steps++ < 60) {
            low = high
            high = min(high + max(0.02, (Math.PI / 2.0 - high) * 0.5), Math.PI / 2.0 - 1.0e-4)
        }
        if (arrivalY(distance, speed, high) >= height) return low
        repeat(28) {
            val mid = (low + high) / 2.0
            if (arrivalY(distance, speed, mid) >= height) low = mid else high = mid
        }
        return low
    }

    private fun apex(speed: Double): Double {
        var vertical = speed
        var height = 0.0
        repeat(MAX_TICKS) {
            vertical -= MatchRules.CANNON_GRAVITY
            if (vertical <= 0.0) return height
            height += vertical
        }
        return height
    }

    private fun arrivalY(distance: Double, speed: Double, theta: Double): Double {
        val horizontal = speed * cos(theta)
        if (horizontal < 1.0e-8) return Double.NEGATIVE_INFINITY
        var vertical = speed * sin(theta)
        var traveled = 0.0
        var height = 0.0
        repeat(MAX_TICKS) {
            vertical -= MatchRules.CANNON_GRAVITY
            val nextTraveled = traveled + horizontal
            val nextHeight = height + vertical
            if (nextTraveled >= distance) {
                val blend = (distance - traveled) / (nextTraveled - traveled)
                return height + (nextHeight - height) * blend
            }
            traveled = nextTraveled
            height = nextHeight
        }
        return Double.NEGATIVE_INFINITY
    }
}
