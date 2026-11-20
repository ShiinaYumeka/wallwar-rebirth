package shina.wallwarRebirth.building

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import shina.wallwarRebirth.match.Match
import shina.wallwarRebirth.monster.MonsterRules
import shina.wallwarRebirth.monster.SummonedUnits
import shina.wallwarRebirth.monster.SummonedVillager
import shina.wallwarRebirth.rules.MatchRules
import java.util.UUID

object MineralReactorCapture {
    private const val REACH = 2.5
    private const val STUCK_TICKS = 10 * MatchRules.TICKS_PER_SECOND
    private const val WALK_SPEED = 1.0

    private data class Job(
        val pos: BlockPos,
        val villagerId: UUID,
        val playerId: UUID,
        val team: String,
        var lastDistance: Double,
        var lastProgressAt: Int,
    )

    private val jobs = HashMap<Long, Job>()

    fun openRefusal(site: SiteCoreBlockEntity, player: ServerPlayer): String? {
        val team = player.team?.name ?: ""
        if (team.isEmpty()) return "先加入队伍"
        if (site.team.isEmpty()) return null
        if (site.isAlly(player)) return null
        if (followingVillager(player) != null) return null
        return "需要有村民跟随才能打开"
    }

    fun canOccupy(site: SiteCoreBlockEntity, player: ServerPlayer): Boolean {
        val team = player.team?.name ?: ""
        if (team.isEmpty() || team == site.team) return false
        return true
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
        val villager = unemployedFollower(player) ?: return "需要有正在跟随的无职业村民"
        val distance = villager.position().distanceTo(Vec3.atCenterOf(site.blockPos))
        val job = Job(site.blockPos.immutable(), villager.uuid, player.uuid, team, distance, villager.tickCount)
        jobs[site.blockPos.asLong()] = job
        if (distance <= REACH) {
            capture(world, site, job, villager)
            return "已占领矿物反应堆"
        }
        return "无职业村民正在前往占领"
    }

    fun redirect(mob: SummonedVillager, level: ServerLevel): Boolean {
        val job = jobOf(mob) ?: return false
        val site = SiteCores.find(level, job.pos)
        if (site == null || !stillValid(level, site, job, mob)) {
            cancel(job.pos, mob, "未能到达，占领中断")
            return false
        }
        val distance = mob.position().distanceTo(Vec3.atCenterOf(site.blockPos))
        if (distance + 0.05 < job.lastDistance) {
            job.lastDistance = distance
            job.lastProgressAt = mob.tickCount
        }
        if (mob.tickCount - job.lastProgressAt >= STUCK_TICKS) {
            cancel(job.pos, mob, "未能到达，占领中断")
            return false
        }
        if (distance <= REACH) {
            capture(level, site, job, mob)
            return true
        }
        if (mob.tickCount % MonsterRules.REPATH_TICKS != 0 && mob.navigation.isInProgress) return true
        val path = mob.navigation.createPath(site.blockPos, 0)
        if (path == null) {
            cancel(job.pos, mob, "未能到达，占领中断")
            return false
        }
        mob.navigation.moveTo(path, WALK_SPEED)
        return true
    }

    fun tickSite(site: SiteCoreBlockEntity, level: ServerLevel) {
        val job = jobs[site.blockPos.asLong()] ?: return
        val villager = level.getEntity(job.villagerId) as? SummonedVillager
        if (villager == null || !stillValid(level, site, job, villager)) {
            cancel(job.pos, villager, "未能到达，占领中断")
            return
        }
        val distance = villager.position().distanceTo(Vec3.atCenterOf(site.blockPos))
        if (distance <= REACH) capture(level, site, job, villager)
    }

    private fun capture(level: ServerLevel, site: SiteCoreBlockEntity, job: Job, villager: SummonedVillager) {
        if (jobs[site.blockPos.asLong()] !== job) return
        jobs.remove(site.blockPos.asLong())
        villager.navigation.stop()
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
        if (!insideMap(villager)) return false
        val player = level.server.playerList.getPlayer(job.playerId) ?: return false
        return villager.mind.follows(player)
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

    private fun followingVillager(player: ServerPlayer): SummonedVillager? {
        val team = player.team?.name ?: return null
        if (team.isEmpty()) return null
        return SummonedUnits.all().firstOrNull { unit ->
            val villager = unit as? SummonedVillager ?: return@firstOrNull false
            villager.isAlive && villager.summonedTeam == team && villager.mind.follows(player)
        } as? SummonedVillager
    }

    private fun unemployedFollower(player: ServerPlayer): SummonedVillager? {
        val team = player.team?.name ?: return null
        if (team.isEmpty()) return null
        return SummonedUnits.all().firstOrNull { unit ->
            val villager = unit as? SummonedVillager ?: return@firstOrNull false
            villager.isAlive &&
                villager.unemployed() &&
                villager.summonedTeam == team &&
                villager.mind.follows(player)
        } as? SummonedVillager
    }
}
