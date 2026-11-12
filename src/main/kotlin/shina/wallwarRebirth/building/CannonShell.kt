package shina.wallwarRebirth.building

import eu.pb4.polymer.core.api.entity.PolymerEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.protocol.game.ClientboundExplodePacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.random.WeightedList
import net.minecraft.world.entity.Entity
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
import shina.wallwarRebirth.rules.MatchRules
import shina.wallwarRebirth.monster.ModMonsters
import shina.wallwarRebirth.monster.SummonedUnit
import shina.wallwarRebirth.targeting.Targeting
import xyz.nucleoid.packettweaker.PacketContext
import java.util.Optional
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

class CannonShell(type: EntityType<out CannonShell>, level: Level) : PrimedTnt(type, level), PolymerEntity {
    var cannonTeam: String = ""
    var corePos: BlockPos = BlockPos.ZERO
    var centerDamage: Double = 0.0
    var powerRadius: Double = MatchRules.CANNON_POWER
    var falloff: Double = MatchRules.CANNON_FALLOFF
    private var leftCore = false
    var manned: Boolean = false
    private var flightTicks = 0

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

    override fun canAddPassenger(passenger: Entity): Boolean = passengers.isEmpty()

    override fun tick() {
        val server = level() as? ServerLevel ?: return
        if (Match.frozen(server)) return
        if (manned) {
            tickManned(server)
            return
        }
        setFuse(FUSE_HOLD)
        val velocity = deltaMovement.add(0.0, -MatchRules.CANNON_GRAVITY, 0.0)
        val from = position()
        val hit = firstSolid(server, from, velocity)
        if (hit != null) {
            setPos(hit.point)
            setDeltaMovement(velocity)
            detonate(server, hit.point)
            return
        }
        val next = from.add(velocity)
        setDeltaMovement(velocity)
        setPos(next.x, next.y, next.z)
        if (next.y < server.minY - 16 || tickCount > MAX_FLIGHT_TICKS) discard()
    }

    private fun tickManned(level: ServerLevel) {
        setFuse(FUSE_HOLD)
        flightTicks++
        val rider = passengers.firstOrNull() as? ServerPlayer
        val here = position()
        if (rider == null || !rider.isAlive) {
            detonate(level, here)
            return
        }
        if (flightTicks >= MatchRules.CANNON_MANNED_TICKS) {
            CannonCrew.kill(level, rider, here)
            detonate(level, here)
            return
        }
        val look = rider.lookAngle
        if (look.lengthSqr() < 1.0e-8) return
        val step = look.normalize().scale(MatchRules.CANNON_MANNED_STEP)
        val impact = sweep(level, here, step, rider)
        if (impact != null) {
            setPos(impact.x, impact.y, impact.z)
            CannonCrew.kill(level, rider, impact)
            detonate(level, impact)
            return
        }
        val next = here.add(step)
        setDeltaMovement(step)
        setPos(next.x, next.y, next.z)
    }

    private fun sweep(level: ServerLevel, from: Vec3, velocity: Vec3, rider: ServerPlayer): Vec3? {
        val distance = velocity.length()
        if (distance < 1.0e-8) return null
        val steps = max(1, ceil(distance / STEP).toInt())
        val step = velocity.scale(1.0 / steps)
        var point = from
        repeat(steps) {
            point = point.add(step)
            val cell = BlockPos.containing(point)
            if (cell == corePos && !leftCore) {
                if (enemyAt(level, from, point, rider)) return point
                return@repeat
            }
            if (cell != corePos) leftCore = true
            if (solidPoint(level, point)) return edge(level, point.subtract(step), point).point
            if (enemyAt(level, from, point, rider)) return point
        }
        return null
    }

    private fun enemyAt(level: ServerLevel, from: Vec3, point: Vec3, rider: ServerPlayer): Boolean {
        val box = boundingBox.move(point.x - from.x, point.y - from.y, point.z - from.z)
        for (entity in level.getEntities(this, box) { it !== rider }) {
            if (enemy(entity)) return true
        }
        return false
    }

    private fun enemy(entity: Entity): Boolean {
        val living = entity as? LivingEntity ?: return false
        if (!living.isAlive) return false
        if (living is Player && !Targeting.canTargetPlayer(living)) return false
        val other = when (living) {
            is Player -> living.team?.name ?: ""
            is SummonedUnit -> living.summonedTeam
            else -> return false
        }
        return other.isNotEmpty() && other != cannonTeam
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
            if (solidPoint(level, point)) return edge(level, point.subtract(step), point)
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

    private fun edge(level: ServerLevel, outside: Vec3, inside: Vec3): Impact {
        var air = outside
        var solid = inside
        repeat(16) {
            val mid = Vec3((air.x + solid.x) / 2.0, (air.y + solid.y) / 2.0, (air.z + solid.z) / 2.0)
            if (solidPoint(level, mid)) solid = mid else air = mid
        }
        return Impact(BlockPos.containing(solid), air)
    }

    private fun detonate(level: ServerLevel, center: Vec3) {
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
        output.putBoolean("Manned", manned)
        output.putInt("FlightTicks", flightTicks)
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
        manned = input.getBooleanOr("Manned", false)
        flightTicks = input.getIntOr("FlightTicks", 0)
        setFuse(FUSE_HOLD)
    }

    private fun teamOf(entity: LivingEntity): String? = when (entity) {
        is Player -> if (!Targeting.canTargetPlayer(entity)) null else entity.team?.name ?: ""
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
            val motion = CannonFlight.velocity(origin, aim) ?: return
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

        fun launchManned(
            level: ServerLevel,
            site: SiteCoreBlockEntity,
            player: ServerPlayer,
            centerDamage: Double,
            wide: Boolean,
        ) {
            val origin = Vec3.atCenterOf(site.blockPos)
            val shell = CannonShell(ModMonsters.CANNON_SHELL, level)
            shell.prime(
                team = site.team,
                core = site.blockPos.immutable(),
                centerDamage = centerDamage,
                powerRadius = if (wide) MatchRules.CANNON_POWER_WIDE else MatchRules.CANNON_POWER,
                falloff = if (wide) MatchRules.CANNON_FALLOFF_WIDE else MatchRules.CANNON_FALLOFF,
                motion = Vec3.ZERO,
                origin = origin,
            )
            shell.manned = true
            level.addFreshEntity(shell)
            CannonCrew.embark(site, player, shell)
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
    private val ANGLE = Math.toRadians(MatchRules.CANNON_LAUNCH_ANGLE)

    fun velocity(start: Vec3, aim: Vec3): Vec3? {
        val offset = aim.subtract(start)
        val distance = sqrt(offset.x * offset.x + offset.z * offset.z)
        if (distance < 1.0e-4) return null
        val speed = speedFor(distance, offset.y) ?: return null
        val cosine = cos(ANGLE)
        val sine = sin(ANGLE)
        return Vec3(offset.x / distance * speed * cosine, speed * sine, offset.z / distance * speed * cosine)
    }

    private fun speedFor(distance: Double, height: Double): Double? {
        val cosine = cos(ANGLE)
        var low = distance / (MAX_TICKS * cosine)
        if (low < 1.0e-3) low = 1.0e-3
        var high = low
        var guard = 0
        while (arrivalY(distance, high, ANGLE) < height && high < 4096.0 && guard++ < 24) high *= 2.0
        if (arrivalY(distance, high, ANGLE) < height - HIT) return null
        repeat(40) {
            val mid = (low + high) / 2.0
            if (arrivalY(distance, mid, ANGLE) < height) low = mid else high = mid
        }
        val arrived = arrivalY(distance, high, ANGLE)
        if (arrived < height - HIT || arrived > height + HIT) return null
        return high
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
