package shina.wallwarRebirth.building

import eu.pb4.polymer.core.api.entity.PolymerEntity
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundExplodePacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BlockTags
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.random.WeightedList
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.MoverType
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.item.PrimedTnt
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.ServerExplosion
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import shina.wallwarRebirth.contract.WallwarTeams
import shina.wallwarRebirth.match.Match
import shina.wallwarRebirth.rules.MatchRules
import shina.wallwarRebirth.util.playNotifySound
import shina.wallwarRebirth.util.showTitle
import shina.wallwarRebirth.monster.ModMonsters
import shina.wallwarRebirth.monster.SummonedUnit
import shina.wallwarRebirth.targeting.Targeting
import xyz.nucleoid.packettweaker.PacketContext
import java.util.Optional
import kotlin.math.PI
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
    var thrown: Boolean = false
    private var mannedStopped = false
    private var flightTicks = 0
    private var guided = false
    private var climbing = false
    private var spiral = 0.0
    private var targetX = 0.5
    private var targetZ = 0.5

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
        guided = false
        climbing = false
        setFuse(FUSE_HOLD)
        snapTo(origin.x, origin.y, origin.z, 0f, 0f)
        setDeltaMovement(motion)
    }

    override fun getPolymerEntityType(context: PacketContext): EntityType<*> = EntityType.TNT

    override fun canAddPassenger(passenger: Entity): Boolean = passengers.isEmpty()

    override fun tick() {
        val server = level() as? ServerLevel ?: return
        if (thrown) {
            tickThrown(server)
            return
        }
        if (Match.frozen(server) && !guided) return
        if (guided) {
            tickGuided(server)
            return
        }
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

    private fun tickGuided(level: ServerLevel) {
        setFuse(FUSE_HOLD)
        flightTicks++
        if (climbing) {
            val speed = MatchRules.GUIDED_CANNON_SPEED
            val from = position()
            val nextY = y + speed
            if (nextY >= level.maxY) {
                helix(level, from, Vec3(x, level.maxY.toDouble(), z))
                climbing = false
                setPos(targetX, level.maxY.toDouble(), targetZ)
                setDeltaMovement(0.0, -speed, 0.0)
                return
            }
            setDeltaMovement(0.0, speed, 0.0)
            setPos(x, nextY, z)
            helix(level, from, position())
            return
        }
        val velocity = deltaMovement
        val from = position()
        val hit = firstSolid(level, from, velocity)
        if (hit != null) {
            helix(level, from, hit.point)
            setPos(hit.point)
            setDeltaMovement(velocity)
            detonate(level, hit.point)
            return
        }
        val next = from.add(velocity)
        setDeltaMovement(velocity)
        setPos(next.x, next.y, next.z)
        helix(level, from, next)
        if (next.y < level.minY - 16 || flightTicks > MAX_FLIGHT_TICKS) discard()
    }

    private fun helix(level: ServerLevel, from: Vec3, to: Vec3) {
        val delta = to.subtract(from)
        val length = delta.length()
        if (length < 0.2) return
        val steps = max(1, ceil(length / 0.85).toInt())
        for (index in 1..steps) {
            val point = from.add(delta.scale(index.toDouble() / steps))
            spiral += 0.62
            val radius = 1.15
            level.sendParticles(
                SPIRAL,
                true,
                true,
                point.x + cos(spiral) * radius,
                point.y,
                point.z + sin(spiral) * radius,
                1,
                0.0,
                0.0,
                0.0,
                0.0,
            )
            level.sendParticles(
                SPIRAL,
                true,
                true,
                point.x + cos(spiral + PI) * radius,
                point.y,
                point.z + sin(spiral + PI) * radius,
                1,
                0.0,
                0.0,
                0.0,
                0.0,
            )
        }
    }

    private fun tickThrown(level: ServerLevel) {
        if (Match.frozen(level)) return
        setDeltaMovement(deltaMovement.add(0.0, -0.04, 0.0))
        move(MoverType.SELF, deltaMovement)
        setDeltaMovement(deltaMovement.scale(0.98))
        if (onGround()) {
            setDeltaMovement(deltaMovement.multiply(0.7, -0.5, 0.7))
        }
        val left = getFuse() - 1
        setFuse(left)
        if (left <= 0) detonate(level, position())
    }

    private fun fillBlast(level: ServerLevel, center: Vec3, radius: Double) {
        val step = 1.75
        var x = -radius
        while (x <= radius + 1.0e-4) {
            var z = -radius
            while (z <= radius + 1.0e-4) {
                if (x * x + z * z <= radius * radius) {
                    level.sendParticles(ParticleTypes.EXPLOSION, true, true, center.x + x, center.y + 0.3, center.z + z, 1, 0.0, 0.15, 0.0, 0.0)
                }
                z += step
            }
            x += step
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, true, true, center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0)
    }

    private fun clearPlanks(level: ServerLevel, center: Vec3, radius: Double) {
        val occupied = HashSet<Long>()
        for (site in SiteCores.all()) {
            if (site.level !== level || site.isRemoved) continue
            occupied.add(site.blockPos.asLong())
            for (part in site.partPositions) occupied.add(part.asLong())
        }
        val reach = ceil(radius).toInt()
        val origin = BlockPos.containing(center)
        val limit = radius * radius
        for (x in -reach..reach) {
            for (y in -reach..reach) {
                for (z in -reach..reach) {
                    val pos = origin.offset(x, y, z)
                    val dx = pos.x + 0.5 - center.x
                    val dy = pos.y + 0.5 - center.y
                    val dz = pos.z + 0.5 - center.z
                    if (dx * dx + dy * dy + dz * dz > limit) continue
                    if (!level.getBlockState(pos).`is`(BlockTags.PLANKS)) continue
                    if (pos.asLong() in occupied) continue
                    level.destroyBlock(pos, true)
                }
            }
        }
    }

    private fun tickManned(level: ServerLevel) {
        setFuse(FUSE_HOLD)
        flightTicks++
        val rider = passengers.firstOrNull() as? ServerPlayer
        val here = position()
        if (flightTicks >= MatchRules.CANNON_MANNED_TICKS) {
            if (rider != null && rider.isAlive) CannonCrew.kill(level, rider, here)
            detonate(level, here)
            return
        }
        if (mannedStopped) return
        if (rider == null || !rider.isAlive) {
            mannedStopped = true
            return
        }
        val look = rider.lookAngle
        if (look.lengthSqr() < 1.0e-8) return
        val step = look.normalize().scale(MatchRules.CANNON_MANNED_STEP)
        val impact = sweep(level, here, step, rider)
        if (impact != null) {
            setPos(impact.x, impact.y, impact.z)
            setDeltaMovement(Vec3.ZERO)
            mannedStopped = true
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
            val open = (1.0 - norm) * seen
            val damageFactor = if (guided) 1.0 - norm else open
            if (damageFactor > 0.0) {
                val raw = centerDamage * (damageFactor * damageFactor + damageFactor) / 2.0
                if (raw > 0.0) {
                    living.hurtServer(level, level.damageSources().explosion(this, null), raw.toFloat())
                }
            }
            if (open > 0.0) {
                val eye = living.eyePosition.subtract(center)
                if (eye.lengthSqr() > 1.0e-8) {
                    val resistance = living.getAttributeValue(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE)
                    val push = eye.normalize().scale(open * (1.0 - resistance))
                    living.push(push)
                    if (living is ServerPlayer) knocks[living] = push
                }
            }
        }
        for (site in SiteCores.all()) {
            if (site.level !== level || site.isRemoved || site.health <= 0 || site.buildingId.isEmpty()) continue
            if (!thrown && site.blockPos == corePos) continue
            if (cannonTeam == site.team) continue
            val box = coreBox(level, site.blockPos)
            if (distanceToBox(center, box) > powerRadius) continue
            val amount = floor(centerDamage * MatchRules.CANNON_BUILDING_FRACTION + 0.5).toInt()
            site.damage(amount, site.blockPos)
        }
        if (guided) {
            clearPlanks(level, center, powerRadius)
            fillBlast(level, center, powerRadius)
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
        output.putBoolean("Thrown", thrown)
        output.putBoolean("MannedStopped", mannedStopped)
        output.putInt("FlightTicks", flightTicks)
        output.putBoolean("Guided", guided)
        output.putBoolean("Climbing", climbing)
        output.putDouble("TargetX", targetX)
        output.putDouble("TargetZ", targetZ)
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
        thrown = input.getBooleanOr("Thrown", false)
        mannedStopped = input.getBooleanOr("MannedStopped", false)
        flightTicks = input.getIntOr("FlightTicks", 0)
        guided = input.getBooleanOr("Guided", false)
        climbing = input.getBooleanOr("Climbing", false)
        targetX = input.getDoubleOr("TargetX", 0.5)
        targetZ = input.getDoubleOr("TargetZ", 0.5)
        if (!thrown) setFuse(FUSE_HOLD)
    }

    private fun teamOf(entity: LivingEntity): String? = when (entity) {
        is Player -> if (!Targeting.canTargetPlayer(entity)) null else entity.team?.name ?: ""
        is SummonedUnit -> entity.summonedTeam
        else -> null
    }

    private data class Impact(val cell: BlockPos, val point: Vec3)


    private fun throwFrom(shooter: Entity, velocity: Float, inaccuracy: Float) {
        val deg = PI / 180.0
        val xRot = shooter.xRot.toDouble()
        val yRot = shooter.yRot.toDouble()
        val f = -sin(yRot * deg) * cos(xRot * deg)
        val g = -sin(xRot * deg)
        val h = cos(yRot * deg) * cos(xRot * deg)
        val dir = Vec3(f, g, h).normalize().add(
            random.triangle(0.0, 0.0172275 * inaccuracy.toDouble()),
            random.triangle(0.0, 0.0172275 * inaccuracy.toDouble()),
            random.triangle(0.0, 0.0172275 * inaccuracy.toDouble()),
        ).scale(velocity.toDouble())
        deltaMovement = dir
        val motion = shooter.deltaMovement
        deltaMovement = deltaMovement.add(motion.x, if (shooter.onGround()) 0.0 else motion.y, motion.z)
    }

    companion object {
        private const val FUSE_HOLD = 30000
        private const val STEP = 0.2
        private const val MAX_FLIGHT_TICKS = 30 * MatchRules.TICKS_PER_SECOND

        fun launchGuided(level: ServerLevel, site: SiteCoreBlockEntity, surface: BlockPos) {
            val origin = aboveTop(site)
            val shell = CannonShell(ModMonsters.CANNON_SHELL, level)
            shell.prime(
                team = site.team,
                core = site.blockPos.immutable(),
                centerDamage = MatchRules.GUIDED_CANNON_CENTER_DAMAGE,
                powerRadius = MatchRules.GUIDED_CANNON_POWER,
                falloff = MatchRules.GUIDED_CANNON_FALLOFF,
                motion = Vec3(0.0, MatchRules.GUIDED_CANNON_SPEED, 0.0),
                origin = origin,
            )
            shell.guided = true
            shell.climbing = true
            shell.targetX = surface.x + 0.5
            shell.targetZ = surface.z + 0.5
            level.addFreshEntity(shell)
            announceLaunch(level, site)
        }

        private fun announceLaunch(level: ServerLevel, site: SiteCoreBlockEntity) {
            val color = when (site.team) {
                WallwarTeams.RED -> ChatFormatting.RED
                WallwarTeams.BLUE -> ChatFormatting.BLUE
                else -> ChatFormatting.WHITE
            }
            val title = Component.literal("晶核制导炮").withStyle(color)
            val subtitle = Component.literal("正在升空")
            for (player in level.players()) {
                player.showTitle(title, subtitle, 5, 40, 10)
                player.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, 0.5f)
            }
        }

        private val SPIRAL = DustParticleOptions(0xC26BFF, 1.5f)

        private fun aboveTop(site: SiteCoreBlockEntity): Vec3 {
            var top = site.blockPos.y
            for (pos in site.partPositions) if (pos.y > top) top = pos.y
            return Vec3(site.blockPos.x + 0.5, top + 1.05, site.blockPos.z + 0.5)
        }

        fun launchThrown(level: ServerLevel, player: ServerPlayer, team: String) {
            val shell = CannonShell(ModMonsters.CANNON_SHELL, level)
            shell.thrown = true
            shell.cannonTeam = team
            shell.corePos = BlockPos.ZERO
            shell.centerDamage = MatchRules.CANNON_CENTER_DAMAGE[1]
            shell.powerRadius = MatchRules.CANNON_POWER
            shell.falloff = MatchRules.CANNON_FALLOFF
            val look = player.lookAngle
            val origin = player.eyePosition.add(look.scale(0.4))
            shell.snapTo(origin.x, origin.y, origin.z, player.yRot, player.xRot)
            shell.throwFrom(player, MatchRules.SHOP_TNT_SPEED, MatchRules.SHOP_TNT_INACCURACY)
            shell.setFuse(MatchRules.SHOP_TNT_FUSE_TICKS)
            level.addFreshEntity(shell)
            level.playSound(null, player.blockPosition(), SoundEvents.TNT_PRIMED, SoundSource.PLAYERS, 1f, 1f)
        }

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
                centerDamage = centerDamage * MatchRules.CANNON_MANNED_DAMAGE_SCALE,
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
