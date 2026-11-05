package shina.wallwarRebirth.building

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.ItemTags
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import shina.wallwarRebirth.item.ModItems
import shina.wallwarRebirth.match.MatchRules
import shina.wallwarRebirth.shop.TeamWarehouses
import shina.wallwarRebirth.monster.MonsterDefinition
import shina.wallwarRebirth.monster.Monsters
import shina.wallwarRebirth.match.MatchPlacement
import shina.wallwarRebirth.monster.Summoning
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max

object BarracksSlots {
    fun acceptsMaterial(stack: ItemStack): Boolean =
        stack.`is`(Items.IRON_INGOT) ||
            stack.`is`(Items.COPPER_INGOT) ||
            stack.`is`(Items.REDSTONE) ||
            stack.`is`(Items.LAPIS_LAZULI) ||
            stack.`is`(Items.DIAMOND) ||
            stack.`is`(ItemTags.LOGS)

    fun acceptsEgg(stack: ItemStack): Boolean =
        stack.`is`(ModItems.EGG_ZOMBIE) ||
            stack.`is`(ModItems.EGG_SKELETON) ||
            stack.`is`(ModItems.EGG_GOLEM) ||
            stack.`is`(ModItems.EGG_SPIDER)
}

object BarracksWork {
    fun tick(site: SiteCoreBlockEntity, level: ServerLevel) {
        if (Monsters.byId(site.producedMonster) == null) site.producedMonster = Monsters.ZOMBIE.id
        if (!site.marching) return
        val definition = Monsters.byId(site.producedMonster) ?: Monsters.ZOMBIE
        produce(site, level, definition)
        drip(site, level)
        if (!site.reinforceTwo.isEmpty && site.barracksStock.size >= releaseQuota(site)) {
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

    fun releaseNow(site: SiteCoreBlockEntity, level: ServerLevel): Boolean = releaseOne(site, level)

    private fun produce(site: SiteCoreBlockEntity, level: ServerLevel, definition: MonsterDefinition) {
        if (site.buildingLevel < definition.rank) return
        val costs = definition.cost.map { ItemAmount(it.item, it.count, it.name) }
        val warehouse = TeamWarehouses.of(site.team)
        if (!InventoryCosts.hasAll(site.barracksMaterials, warehouse, costs)) return
        val interval = definition.productionTicks(site.buildingLevel)
        if (site.actionAge < interval) {
            site.actionAge++
            if (site.actionAge < interval) return
        }
        if (!site.reinforceTwo.isEmpty) {
            InventoryCosts.takeAll(site.barracksMaterials, warehouse, costs)
            site.barracksStock.add(definition.id)
            site.actionAge = 0
            site.setChanged()
            if (site.barracksStock.size >= releaseQuota(site)) {
                releaseListed(site, level, site.barracksStock, vanish = false)
            }
            return
        }
        val delivered = if (site.marching) {
            spawnPreferred(site, level, definition)
        } else if (insertEgg(site, definition)) {
            true
        } else {
            spawnPreferred(site, level, definition)
        }
        if (!delivered) return
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

    private fun insertEgg(site: SiteCoreBlockEntity, definition: MonsterDefinition): Boolean {
        val container = site.barracksEggs
        val item = definition.egg
        for (slot in 0 until container.containerSize) {
            val stack = container.getItem(slot)
            if (stack.`is`(item) && stack.count < stack.maxStackSize) {
                stack.grow(1)
                container.setChanged()
                return true
            }
        }
        for (slot in 0 until container.containerSize) {
            if (container.getItem(slot).isEmpty) {
                container.setItem(slot, ItemStack(item))
                return true
            }
        }
        return false
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
        return if (count in MatchRules.BARRACKS_RELEASE_MIN..MatchRules.BARRACKS_RELEASE_MAX) {
            count
        } else {
            MatchRules.BARRACKS_RELEASE_MIN
        }
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
            val feet = if (definition == null) null else findFeet(site, level, blocked)
            if (definition == null || feet == null || !spawnAt(site, level, definition, feet)) {
                if (!vanish && definition != null) remain.add(id)
                continue
            }
            blocked.add(feet)
            blocked.add(feet.above())
        }
        if (!vanish) queue.addAll(remain)
        site.setChanged()
    }

    private fun spawnPreferred(site: SiteCoreBlockEntity, level: ServerLevel, definition: MonsterDefinition): Boolean {
        val feet = findFeet(site, level, emptySet()) ?: return false
        return spawnAt(site, level, definition, feet)
    }

    private fun spawnAt(site: SiteCoreBlockEntity, level: ServerLevel, definition: MonsterDefinition, feet: BlockPos): Boolean {
        val step = spawnStep(site, level)
        val yaw = Math.toDegrees(atan2(-step.x.toDouble(), step.z.toDouble())).toFloat()
        return Summoning.spawn(
            level,
            definition,
            site.team,
            feet.x + 0.5,
            feet.y.toDouble(),
            feet.z + 0.5,
            yaw,
            swift = !site.reinforce.isEmpty,
        )
    }

    private fun findFeet(site: SiteCoreBlockEntity, level: ServerLevel, blocked: Set<BlockPos>): BlockPos? {
        val preferred = site.blockPos.offset(spawnStep(site, level))
        val spots = ArrayList<BlockPos>()
        for (dx in -SEARCH..SEARCH) {
            for (dz in -SEARCH..SEARCH) {
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
            pos !in blocked &&
                pos.above() !in blocked &&
                MatchPlacement.ownHalf(level, pos, site.team) &&
                Summoning.canOccupy(level, pos, null) &&
                Summoning.canOccupy(level, pos.above(), null)
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
}
