package shina.wallwarRebirth.building

import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.pathfinder.Path
import net.minecraft.world.phys.Vec3
import shina.wallwarRebirth.util.playNotifySound
import shina.wallwarRebirth.match.Match
import shina.wallwarRebirth.monster.MonsterRules
import shina.wallwarRebirth.monster.SummonedEscape
import shina.wallwarRebirth.monster.SummonedUnits
import shina.wallwarRebirth.monster.SummonedVillager
import shina.wallwarRebirth.rules.MatchRules
import java.util.UUID

object MineralReactorCapture {
    private const val REACH = MatchRules.MINERAL_REACTOR_CAPTURE_RANGE
    private const val REACH_SQR = REACH * REACH
    private const val STUCK_TICKS = 10 * MatchRules.TICKS_PER_SECOND
    private const val WALK_SPEED = 1.0
    private const val DROP_REPATH_DISTANCE = 1.5

    private data class Job(
        val pos: BlockPos,
        val villagerId: UUID,
        val playerId: UUID,
        val team: String,
        var lastPosition: Vec3,
        var lastProgressAt: Int,
    ) {
        val escape = SummonedEscape.Memory()
        var nextPathAt = lastProgressAt
        var lastGroundY = lastPosition.y
    }

    private val jobs = HashMap<Long, Job>()

    fun openRefusal(player: ServerPlayer): String? {
        val team = player.team?.name ?: ""
        if (team.isEmpty()) return "先加入队伍"
        return null
    }

    fun canOccupy(site: SiteCoreBlockEntity, player: ServerPlayer): Boolean {
        val team = player.team?.name ?: ""
        return !(team.isEmpty() || team == site.team)
    }

    fun tryStart(site: SiteCoreBlockEntity, player: ServerPlayer): String {
        val team = player.team?.name ?: ""
        if (team.isEmpty()) return "先加入队伍"
        if (site.buildingId != Buildings.MINERAL_REACTOR.id) return "不是矿物反应堆"
        if (site.health <= 0) return "这座反应堆已经不在了"
        if (team == site.team) return "已经是本队的反应堆"
        val world = site.level as? ServerLevel ?: return "这个位置不能占领"
        if (Match.phase != Match.Phase.FIGHT) return "只有战斗阶段可以占领"
        if (!Match.isArena(world)) return "这个位置不能占领"
        val existing = jobs[site.blockPos.asLong()]
        if (existing != null && existing.team == team && stillValid(world, site, existing)) {
            return "已经有无职业村民正在前往占领"
        }
        val villager = nearestUnemployed(world, site, team)
        if (villager == null) {
            player.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1f, 1f)
            return "没有无职业村民"
        }
        val job = Job(site.blockPos.immutable(), villager.uuid, player.uuid, team, villager.position(), villager.tickCount)
        jobs[site.blockPos.asLong()] = job
        if (arrived(villager, job.pos)) {
            capture(world, site, job, villager)
            return "已占领矿物反应堆"
        }
        villager.navigation.stop()
        return "无职业村民正在前往占领"
    }

    fun redirect(mob: SummonedVillager, level: ServerLevel): Boolean {
        val job = jobOf(mob) ?: return false
        val site = SiteCores.find(level, job.pos)
        if (site == null || !stillValid(level, site, job, mob)) {
            cancel(job.pos, mob, "未能到达，占领中断")
            return false
        }
        if (arrived(mob, job.pos)) {
            capture(level, site, job, mob)
            return true
        }
        val now = mob.tickCount
        val navigation = mob.navigation
        val grounded = mob.onGround()
        if (grounded) {
            if (job.lastGroundY - mob.y >= DROP_REPATH_DISTANCE) {
                navigation.stop()
                job.lastPosition = mob.position()
                job.lastProgressAt = now
                job.nextPathAt = now
                job.escape.pillarFloor = Int.MIN_VALUE
            }
            job.lastGroundY = mob.y
        }
        if (navigation.isInProgress && (grounded || mob.isInWater)) {
            val step = mob.position().subtract(job.lastPosition)
            val minStep = MatchRules.ESCAPE_STUCK_DISTANCE
            if (step.lengthSqr() >= minStep * minStep) {
                job.lastPosition = mob.position()
                job.lastProgressAt = now
                if (step.horizontalDistanceSqr() >= minStep * minStep) {
                    job.escape.pillarFloor = Int.MIN_VALUE
                }
            }
        }
        val idle = now - job.lastProgressAt
        val stillTrying = idle < STUCK_TICKS
        when (SummonedEscape.pillarFromPit(mob, level, job.escape, stillTrying && idle >= MatchRules.ESCAPE_STUCK_TICKS)) {
            SummonedEscape.PillarStep.PLACED -> {
                job.lastPosition = mob.position()
                job.lastProgressAt = now
                job.nextPathAt = now
                return true
            }
            SummonedEscape.PillarStep.WORKING -> return true
            SummonedEscape.PillarStep.NONE -> Unit
        }
        if (!grounded && !mob.isInWater) return true
        if (!stillTrying) {
            cancel(job.pos, mob, "未能到达，占领中断")
            return false
        }
        if (navigation.isInProgress && idle < MatchRules.ESCAPE_STUCK_TICKS) return true
        if (now < job.nextPathAt) return true
        job.nextPathAt = now + MonsterRules.REPATH_TICKS
        navigation.stop()
        val path = approachPath(mob, level, job.pos)
        if (path != null && path.nodeCount >= 2) navigation.moveTo(path, WALK_SPEED)
        return true
    }

    fun tickSite(site: SiteCoreBlockEntity, level: ServerLevel) {
        val job = jobs[site.blockPos.asLong()] ?: return
        val villager = level.getEntity(job.villagerId) as? SummonedVillager
        if (villager == null || !stillValid(level, site, job, villager)) {
            cancel(job.pos, villager, "未能到达，占领中断")
            return
        }
        if (arrived(villager, job.pos)) capture(level, site, job, villager)
    }

    private fun arrived(villager: SummonedVillager, pos: BlockPos): Boolean =
        villager.onGround() && villager.position().distanceToSqr(Vec3.atCenterOf(pos)) <= REACH_SQR

    private fun approachPath(mob: SummonedVillager, level: ServerLevel, pos: BlockPos): Path? {
        val center = Vec3.atCenterOf(pos)
        val radius = REACH.toInt()
        val targets = HashSet<BlockPos>()
        val box = mob.boundingBox
        val min = pos.offset(-radius, -radius, -radius)
        val max = pos.offset(radius, radius + 1, radius)
        for (feet in BlockPos.betweenClosed(min, max)) {
            val stand = Vec3.atBottomCenterOf(feet)
            if (stand.distanceToSqr(center) > REACH_SQR) continue
            if (!level.chunkSource.hasChunk(SectionPos.blockToSectionCoord(feet.x), SectionPos.blockToSectionCoord(feet.z))) continue
            val floor = feet.below()
            if (level.getBlockState(floor).getCollisionShape(level, floor).isEmpty) continue
            if (level.getBlockCollisions(mob, box.move(stand.subtract(mob.position()))).iterator().hasNext()) continue
            targets.add(feet.immutable())
        }
        return if (targets.isEmpty()) null else mob.navigation.createPath(targets, 0)
    }

    private fun capture(level: ServerLevel, site: SiteCoreBlockEntity, job: Job, villager: SummonedVillager) {
        if (jobs[site.blockPos.asLong()] !== job) return
        jobs.remove(site.blockPos.asLong())
        villager.navigation.stop()
        villager.discard()
        site.dropInstalledModules()
        site.team = job.team
        site.mineralAges.clear()
        site.healAge = 0
        site.setChanged()
        val player = level.server.playerList.getPlayer(job.playerId)
        player?.sendSystemMessage(Component.literal("已占领矿物反应堆"))
    }

    private fun cancel(pos: BlockPos, villager: SummonedVillager?, message: String) {
        val job = jobs.remove(pos.asLong()) ?: return
        villager?.navigation?.stop()
        val world = villager?.level() as? ServerLevel
        val player = world?.server?.playerList?.getPlayer(job.playerId)
        player?.sendSystemMessage(Component.literal(message))
    }

    private fun stillValid(level: ServerLevel, site: SiteCoreBlockEntity, job: Job, mob: SummonedVillager? = null): Boolean {
        if (Match.phase != Match.Phase.FIGHT || !Match.isArena(level)) return false
        if (site.isRemoved || site.health <= 0 || site.buildingId != Buildings.MINERAL_REACTOR.id) return false
        if (site.team == job.team) return false
        val villager = mob ?: level.getEntity(job.villagerId) as? SummonedVillager ?: return false
        if (!villager.isAlive || villager.isRemoved) return false
        if (!villager.unemployed()) return false
        if (villager.summonedTeam != job.team) return false
        return insideMap(villager)
    }

    private fun insideMap(entity: Entity): Boolean {
        val size = Match.mapSize
        val min = -size / 2
        val max = min + size - 1
        val x = entity.blockX
        val z = entity.blockZ
        return x in min..max && z in min..max
    }

    private fun jobOf(mob: SummonedVillager): Job? =
        jobs.values.firstOrNull { it.villagerId == mob.uuid }

    private fun nearestUnemployed(world: ServerLevel, site: SiteCoreBlockEntity, team: String): SummonedVillager? {
        val origin = Vec3.atCenterOf(site.blockPos)
        return SummonedUnits.all().filterIsInstance<SummonedVillager>().filter { villager ->
            villager.isAlive &&
                !villager.isRemoved &&
                villager.level() === world &&
                villager.unemployed() &&
                villager.summonedTeam == team &&
                insideMap(villager)
        }.minByOrNull { it.position().distanceToSqr(origin) }
    }
}
