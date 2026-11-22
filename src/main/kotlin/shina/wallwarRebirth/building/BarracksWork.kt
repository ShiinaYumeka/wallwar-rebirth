package shina.wallwarRebirth.building

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.ItemTags
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.entity.ai.attributes.Attributes
import shina.wallwarRebirth.rules.MatchRules
import shina.wallwarRebirth.shop.match.TeamWarehouses
import shina.wallwarRebirth.monster.MonsterDefinition
import shina.wallwarRebirth.monster.Monsters
import shina.wallwarRebirth.monster.Summoning
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max

object BarracksSlots {
    fun acceptsMaterial(stack: ItemStack, wheat: Boolean = false): Boolean =
        stack.`is`(Items.IRON_INGOT) ||
            stack.`is`(Items.COPPER_INGOT) ||
            stack.`is`(Items.REDSTONE) ||
            stack.`is`(Items.LAPIS_LAZULI) ||
            stack.`is`(Items.DIAMOND) ||
            stack.`is`(ItemTags.LOGS) ||
            (wheat && stack.`is`(Items.WHEAT))

}

private class Producer(
    val defaultId: String,
    val source: shina.wallwarRebirth.monster.MonsterSource,
    val bindHome: Boolean,
    val applySwift: Boolean,
    val plated: Boolean,
)

private fun producerOf(site: SiteCoreBlockEntity): Producer? = when (site.buildingId) {
    Buildings.BARRACKS.id -> Producer(Monsters.ZOMBIE.id, shina.wallwarRebirth.monster.MonsterSource.BARRACKS, bindHome = false, applySwift = true, plated = false)
    Buildings.PILLAGER.id -> Producer(Monsters.PILLAGER.id, shina.wallwarRebirth.monster.MonsterSource.TOWER, bindHome = true, applySwift = false, plated = true)
    else -> null
}

object BarracksWork {
    fun tick(site: SiteCoreBlockEntity, level: ServerLevel) {
        val producer = producerOf(site) ?: return
        val current = Monsters.byId(site.producedMonster)
        if (current == null || current.source != producer.source) site.producedMonster = producer.defaultId
        if (!site.marching) return
        val definition = Monsters.byId(site.producedMonster) ?: Monsters.byId(producer.defaultId) ?: return
        produce(site, level, definition)
        drip(site, level)
        if (site.buildingId == Buildings.BARRACKS.id && !site.reinforceTwo.isEmpty && site.barracksStock.size >= releaseQuota(site)) {
            releaseListed(site, level, site.barracksStock, vanish = false)
        }
        dripQueue(site, level)
    }

    fun hasEggs(site: SiteCoreBlockEntity): Boolean {
        val container = site.barracksEggs
        for (slot in 0 until container.containerSize) {
            if (!container.getItem(slot).isEmpty) return true
        }
        return false
    }

    private fun produce(site: SiteCoreBlockEntity, level: ServerLevel, definition: MonsterDefinition) {
        if (!definition.productionReady) return
        if (site.buildingLevel < definition.rank) return
        if (definition.requiresWorkshop && !SiteCores.hasLiveWorkshop(level, site.team)) return
        val costs = definition.cost.map { ItemAmount(it.item, it.count, it.name) }
        val warehouse = TeamWarehouses.of(site.team)
        if (!InventoryCosts.hasAll(site.barracksMaterials, warehouse, costs)) return
        val interval = definition.productionTicks(site.buildingLevel)
        site.actionAge = IntervalGate.advance(site.actionAge, interval, counting = true)
        if (!IntervalGate.due(site.actionAge, interval)) return
        val count = definition.batch.coerceAtLeast(1)
        if (site.buildingId == Buildings.BARRACKS.id && !site.reinforceTwo.isEmpty) {
            InventoryCosts.takeAll(site.barracksMaterials, warehouse, costs)
            repeat(count) { site.barracksStock.add(definition.id) }
            site.actionAge = 0
            site.setChanged()
            if (site.barracksStock.size >= releaseQuota(site)) {
                releaseListed(site, level, site.barracksStock, vanish = false)
            }
            return
        }
        val spots = reserveSpots(site, level, definition, count)
        if (spots.size < count) return
        val spawned = ArrayList<LivingEntity>()
        for (feet in spots) {
            val mob = spawnAt(site, level, definition, feet)
            if (mob == null) {
                spawned.forEach { it.discard() }
                return
            }
            spawned.add(mob)
        }
        InventoryCosts.takeAll(site.barracksMaterials, warehouse, costs)
        site.actionAge = 0
        site.setChanged()
    }

    private fun drip(site: SiteCoreBlockEntity, level: ServerLevel) {
        if (!hasEggs(site)) {
            site.eggReleaseAge = 0
            return
        }
        site.eggReleaseAge++
        if (site.eggReleaseAge < MatchRules.TICKS_PER_SECOND) return
        site.eggReleaseAge = 0
        releaseOne(site, level)
    }

    private fun releaseOne(site: SiteCoreBlockEntity, level: ServerLevel): Boolean {
        val container = site.barracksEggs
        for (slot in 0 until container.containerSize) {
            val stack = container.getItem(slot)
            if (stack.isEmpty) continue
            val definition = Monsters.byEgg(stack.item) ?: continue
            if (!spawnPreferred(site, level, definition)) return false
            stack.shrink(1)
            if (stack.isEmpty) container.setItem(slot, ItemStack.EMPTY) else container.setChanged()
            return true
        }
        return false
    }

    fun dumpStock(site: SiteCoreBlockEntity, level: ServerLevel) {
        releaseListed(site, level, site.barracksStock, vanish = false)
        site.dripQueue.addAll(site.barracksStock)
        site.barracksStock.clear()
        site.dripAge = 0
        site.setChanged()
    }

    fun discardStock(site: SiteCoreBlockEntity, level: ServerLevel) {
        if (site.barracksStock.isEmpty() && site.dripQueue.isEmpty()) return
        releaseListed(site, level, site.barracksStock, vanish = true)
        releaseListed(site, level, site.dripQueue, vanish = true)
    }

    private fun releaseQuota(site: SiteCoreBlockEntity): Int {
        val count = site.releaseCount
        val selected = if (count in MatchRules.BARRACKS_RELEASE_MIN..MatchRules.BARRACKS_RELEASE_MAX) {
            count
        } else {
            MatchRules.BARRACKS_RELEASE_MIN
        }
        val batch = (Monsters.byId(site.producedMonster)?.batch ?: 1).coerceAtLeast(1)
        return selected * batch
    }

    private fun dripQueue(site: SiteCoreBlockEntity, level: ServerLevel) {
        if (site.dripQueue.isEmpty()) {
            site.dripAge = 0
            return
        }
        site.dripAge++
        if (site.dripAge < MatchRules.TICKS_PER_SECOND) return
        site.dripAge = 0
        releaseListed(site, level, site.dripQueue, vanish = false)
    }

    private fun releaseListed(site: SiteCoreBlockEntity, level: ServerLevel, queue: MutableList<String>, vanish: Boolean) {
        val pending = queue.toList()
        queue.clear()
        val blocked = HashSet<BlockPos>()
        val remain = ArrayList<String>()
        for (id in pending) {
            val definition = Monsters.byId(id)
            val feet = if (definition == null) null else findFeet(site, level, definition, blocked)
            if (definition == null || feet == null || spawnAt(site, level, definition, feet) == null) {
                if (!vanish && definition != null) remain.add(id)
                continue
            }
            blocked.addAll(Summoning.footprint(definition, feet))
        }
        if (!vanish) queue.addAll(remain)
        site.setChanged()
    }

    private fun spawnPreferred(site: SiteCoreBlockEntity, level: ServerLevel, definition: MonsterDefinition): Boolean {
        val feet = findFeet(site, level, definition, emptySet()) ?: return false
        return spawnAt(site, level, definition, feet) != null
    }

    private fun reserveSpots(site: SiteCoreBlockEntity, level: ServerLevel, definition: MonsterDefinition, count: Int): List<BlockPos> {
        val blocked = HashSet<BlockPos>()
        val found = ArrayList<BlockPos>()
        repeat(count) {
            val feet = findFeet(site, level, definition, blocked) ?: return found
            found.add(feet)
            blocked.addAll(Summoning.footprint(definition, feet))
        }
        return found
    }

    private fun spawnAt(
        site: SiteCoreBlockEntity,
        level: ServerLevel,
        definition: MonsterDefinition,
        feet: BlockPos,
    ): LivingEntity? {
        val step = spawnStep(site, level)
        val yaw = Math.toDegrees(atan2(-step.x.toDouble(), step.z.toDouble())).toFloat()
        val producer = producerOf(site)
        val mob = Summoning.place(
            level,
            definition,
            site.team,
            feet.x + 0.5,
            feet.y.toDouble(),
            feet.z + 0.5,
            yaw,
            swift = producer?.applySwift == true && !site.reinforce.isEmpty,
            home = if (producer?.bindHome == true) site.blockPos.immutable() else null,
        ) ?: return null
        if (producer?.plated == true && !site.reinforce.isEmpty) {
            mob.getAttribute(Attributes.ARMOR)?.baseValue = MatchRules.PILLAGER_REINFORCE_ARMOR
        }
        return mob
    }

    private fun findFeet(
        site: SiteCoreBlockEntity,
        level: ServerLevel,
        definition: MonsterDefinition,
        blocked: Set<BlockPos>,
    ): BlockPos? =
        searchFeet(site, level, definition, blocked, SEARCH)
            ?: searchFeet(site, level, definition, blocked, SEARCH + SEARCH_EXTRA)

    private fun searchFeet(
        site: SiteCoreBlockEntity,
        level: ServerLevel,
        definition: MonsterDefinition,
        blocked: Set<BlockPos>,
        radius: Int,
    ): BlockPos? {
        val preferred = site.blockPos.offset(spawnStep(site, level))
        val spots = ArrayList<BlockPos>()
        for (dx in -radius..radius) {
            for (dz in -radius..radius) {
                spots.add(preferred.offset(dx, 0, dz))
            }
        }
        spots.sortWith(
            compareBy<BlockPos> { max(abs(it.x - preferred.x), abs(it.z - preferred.z)) }
                .thenBy { dist2(site.blockPos, it) }
                .thenBy { it.x }
                .thenBy { it.z },
        )
        return spots.firstOrNull { pos ->
            val cells = Summoning.footprint(definition, pos)
            cells.none { it in blocked } && cells.all { Summoning.canOccupy(level, it, null) }
        }
    }

    private fun spawnStep(site: SiteCoreBlockEntity, level: ServerLevel): BlockPos {
        val enemy = nearestCore(site, level)
        if (enemy == null || (enemy.blockPos.x == site.blockPos.x && enemy.blockPos.z == site.blockPos.z)) {
            val facing = site.facingDirection()
            return BlockPos(facing.stepX, 0, facing.stepZ)
        }
        val dx = enemy.blockPos.x.compareTo(site.blockPos.x)
        val dz = enemy.blockPos.z.compareTo(site.blockPos.z)
        return BlockPos(dx, 0, dz)
    }

    private fun nearestCore(site: SiteCoreBlockEntity, level: ServerLevel): SiteCoreBlockEntity? =
        SiteCores.all()
            .filter {
                it.level === level &&
                    !it.isRemoved &&
                    it.health > 0 &&
                    it.buildingId.isNotEmpty() &&
                    it.blockPos != site.blockPos &&
                    it.team != site.team
            }
            .minWithOrNull(
                compareBy<SiteCoreBlockEntity> { dist2(site.blockPos, it.blockPos) }
                    .thenBy { it.health }
                    .thenBy { it.blockPos.x }
                    .thenBy { it.blockPos.z }
                    .thenBy { it.blockPos.y },
            )

    private fun dist2(origin: BlockPos, pos: BlockPos): Long {
        val dx = (origin.x - pos.x).toLong()
        val dy = (origin.y - pos.y).toLong()
        val dz = (origin.z - pos.z).toLong()
        return dx * dx + dy * dy + dz * dz
    }

    private const val SEARCH = 3
    private const val SEARCH_EXTRA = 2
}
