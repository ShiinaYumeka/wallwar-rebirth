package shina.wallwarRebirth.building

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import shina.wallwarRebirth.item.ModItems
import shina.wallwarRebirth.match.MatchRules
import shina.wallwarRebirth.monster.MonsterDefinition
import shina.wallwarRebirth.monster.Monsters
import shina.wallwarRebirth.match.MatchPlacement
import shina.wallwarRebirth.monster.Summoning
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max

object BarracksSlots {
    fun acceptsMaterial(stack: ItemStack): Boolean =
        stack.`is`(Items.REDSTONE) || stack.`is`(Items.REDSTONE_BLOCK) || stack.`is`(Items.IRON_INGOT)

    fun acceptsEgg(stack: ItemStack): Boolean =
        stack.`is`(ModItems.EGG_ZOMBIE) || stack.`is`(ModItems.EGG_SKELETON) || stack.`is`(ModItems.EGG_GOLEM)
}

object BarracksWork {
    fun tick(site: SiteCoreBlockEntity, level: ServerLevel) {
        if (Monsters.byId(site.producedMonster) == null) site.producedMonster = Monsters.ZOMBIE.id
        val definition = Monsters.byId(site.producedMonster) ?: Monsters.ZOMBIE
        produce(site, level, definition)
        if (site.marching) drip(site, level) else site.eggReleaseAge = 0
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
        val costs = definition.cost.map { ItemAmount(it.item, it.count, it.name) }
        if (!InventoryCosts.hasAll(site.barracksMaterials, costs)) return
        val interval = definition.productionTicks(site.buildingLevel)
        if (site.actionAge < interval) {
            site.actionAge++
            if (site.actionAge < interval) return
        }
        val delivered = if (site.marching) {
            spawnPreferred(site, level, definition)
        } else if (insertEgg(site, definition)) {
            true
        } else {
            spawnPreferred(site, level, definition)
        }
        if (!delivered) return
        InventoryCosts.takeAll(site.barracksMaterials, costs)
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

    private fun spawnPreferred(site: SiteCoreBlockEntity, level: ServerLevel, definition: MonsterDefinition): Boolean {
        val feet = findFeet(site, level) ?: return false
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
        )
    }

    private fun findFeet(site: SiteCoreBlockEntity, level: ServerLevel): BlockPos? {
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
