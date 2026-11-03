package shina.wallwarRebirth.match

import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import shina.wallwarRebirth.WallwarMod

object MatchRules {
    const val MAP_SIZE = 100
    const val MAP_SIZE_STEP = 20
    const val BASE_INSET = 16
    const val BASE_FORBID = 4
    const val PLATFORM_RADIUS = 3
    const val SPAWN_DISTANCE = 8.0
    const val BASE_HEALTH = 100
    const val SURFACE_ORE_EVERY = 16
    const val SURFACE_ORE_VEIN = 8
    const val BARRIER_TOP = 128
    const val DAY_TIME = 1000L
    const val TICKS_PER_SECOND = 20
    const val WATCH_TICKS = 10 * TICKS_PER_SECOND
    const val BUILDING_FALL_TICKS = 2 * TICKS_PER_SECOND
    const val RESPAWN_TICKS = 8 * TICKS_PER_SECOND

    fun minimumMapSize(): Int {
        val separation = SPAWN_DISTANCE.toInt() + PLATFORM_RADIUS * 2 + 1
        val needed = separation + 3 + BASE_INSET * 2
        var size = MAP_SIZE
        while (size - MAP_SIZE_STEP >= needed) size -= MAP_SIZE_STEP
        return size
    }

    const val DIG_DAMAGE = 1
    const val MINER_DIG_DAMAGE = 2
    const val MINER_ACTIVE_TICKS = 30 * TICKS_PER_SECOND
    const val MINER_LAPIS = 4
    const val MINER_BONUS_CHANCE = 0.5f
    const val GLOW_TICKS = 2 * TICKS_PER_SECOND
    const val MIN_PRODUCTION_TICKS = 1

    fun secondsText(ticks: Int): String {
        val tenths = ticks * 10 / TICKS_PER_SECOND
        val whole = tenths / 10
        val fraction = tenths % 10
        return if (fraction == 0) whole.toString() else "$whole.$fraction"
    }

    const val MODULE_LAPIS = 32
    const val MODULE_TWO_LAPIS = 32

    val REACTOR_HEALTH = listOf(20, 30, 40)
    const val REACTOR_PRICE_IRON = 32
    const val REACTOR_PRICE_COPPER = 32
    const val REACTOR_UPGRADE_2_IRON = 32
    const val REACTOR_UPGRADE_2_COPPER = 32
    const val REACTOR_UPGRADE_3_IRON = 64
    const val REACTOR_UPGRADE_3_COPPER = 64
    const val REACTOR_IRON_TICKS = 10 * TICKS_PER_SECOND
    const val REACTOR_IRON_COUNT = 2
    const val REACTOR_COPPER_COUNT = 2
    const val REACTOR_REDSTONE_TICKS = 10 * TICKS_PER_SECOND
    const val REACTOR_REDSTONE_COUNT = 1
    const val REACTOR_LAPIS_COUNT = 1
    const val REACTOR_DIAMOND_TICKS = 30 * TICKS_PER_SECOND
    const val REACTOR_DIAMOND_COUNT = 1

    val WALL_HEALTH = listOf(60, 90, 120)
    const val WALL_PRICE_COPPER = 8
    const val WALL_UPGRADE_2_COPPER = 8
    const val WALL_UPGRADE_3_COPPER = 8
    const val WALL_THORNS_DAMAGE = 4
    const val WALL_HEAL_RANGE = 32.0
    const val WALL_HEAL_AMOUNT = 1

    val TOWER_HEALTH = listOf(20, 20, 20)
    val TOWER_RANGE = listOf(12.0, 16.0, 20.0)
    val TOWER_SPEED = listOf(2f, 3f, 4f)
    const val TOWER_ARROW_BASE_DAMAGE = 2.0
    val TOWER_INTERVAL = listOf(2 * TICKS_PER_SECOND, 3 * TICKS_PER_SECOND / 2, TICKS_PER_SECOND)
    const val TOWER_PRICE_COPPER = 32
    const val TOWER_UPGRADE_2_COPPER = 24
    const val TOWER_UPGRADE_3_COPPER = 24
    const val TOWER_TIE_DISTANCE = 0.001
    const val TOWER_PIERCE_HITS = 3
    const val TOWER_SPREAD_DEGREES = 10.0

    val BARRACKS_HEALTH = listOf(24, 24, 24)
    const val BARRACKS_PRICE_IRON = 24
    const val BARRACKS_UPGRADE_2_IRON = 32
    const val BARRACKS_UPGRADE_3_IRON = 32
    const val BARRACKS_UPGRADE_3_DIAMOND = 4
    const val BARRACKS_RELEASE_MIN = 1
    const val BARRACKS_RELEASE_MAX = 5

    val CANNON_HEALTH = listOf(10, 10, 10)
    const val CANNON_RANGE = 32.0
    const val CANNON_INTERVAL = 10 * TICKS_PER_SECOND
    val CANNON_CENTER_DAMAGE = listOf(20.0, 30.0, 40.0)
    const val CANNON_PRICE_IRON = 32
    const val CANNON_PRICE_COPPER = 32
    const val CANNON_UPGRADE_2_COPPER = 32
    const val CANNON_UPGRADE_2_IRON = 32
    const val CANNON_UPGRADE_3_IRON = 64
    const val CANNON_UPGRADE_3_COPPER = 64
    const val CANNON_LAUNCH_SPEED = 4.0
    const val CANNON_GRAVITY = 0.04
    const val CANNON_POWER = 4.0
    const val CANNON_POWER_WIDE = 6.0
    const val CANNON_FALLOFF = 8.0
    const val CANNON_FALLOFF_WIDE = 12.0
    const val CANNON_BUILDING_FRACTION = 0.25
    const val CANNON_TIE_DISTANCE = 0.001

    const val ZOMBIE_HEALTH = 40.0
    const val ZOMBIE_ARMOR = 2.0
    const val ZOMBIE_ATTACK = 3.0
    const val SKELETON_HEALTH = 30.0
    const val SKELETON_ARMOR = 0.0
    const val SKELETON_ATTACK = 4.0
    const val SPIDER_HEALTH = 32.0
    const val SPIDER_ARMOR = 0.0
    const val SPIDER_ATTACK = 5.0
    const val SPIDER_BUILDING_DAMAGE = 2
    const val GOLEM_HEALTH = 100.0
    const val GOLEM_ARMOR = 10.0
    const val GOLEM_ATTACK = 12.0
    const val GOLEM_BUILDING_DAMAGE = 4
    const val SMALL_BUILDING_DAMAGE = 1
    const val BUILDING_HIT_TICKS = TICKS_PER_SECOND
    const val BUILDING_ATTACK_RANGE = 2.0
    const val ZOMBIE_COST_REDSTONE = 1
    const val SKELETON_COST_REDSTONE = 1
    const val SPIDER_COST_REDSTONE = 2
    const val GOLEM_COST_REDSTONE = 4
    const val GOLEM_COST_IRON = 4
    const val ZOMBIE_PRODUCTION_TICKS = 15 * TICKS_PER_SECOND
    const val SKELETON_PRODUCTION_TICKS = 15 * TICKS_PER_SECOND
    const val SPIDER_PRODUCTION_TICKS = 15 * TICKS_PER_SECOND
    const val GOLEM_PRODUCTION_TICKS = 30 * TICKS_PER_SECOND

    const val SWORD_MATERIAL = 2
    const val AXE_MATERIAL = 3
    const val PICKAXE_MATERIAL = 3
    const val SHOVEL_MATERIAL = 1
    const val SPEAR_MATERIAL = 1
    const val HELMET_MATERIAL = 5
    const val CHESTPLATE_MATERIAL = 8
    const val LEGGINGS_MATERIAL = 7
    const val BOOTS_MATERIAL = 4
    const val SHIELD_DIAMOND = 2
    const val START_IRON = 64
    const val START_COPPER = 64
    const val BOW_IRON = 16
    const val ARROW_COUNT = 8
    const val ARROW_IRON = 4
    const val CROSSBOW_IRON = 16
    const val BEEF_COUNT = 4
    const val BEEF_IRON = 4
    const val PLANK_COUNT = 16
    const val PLANK_IRON = 4

    const val CORE_RETARGET_TICKS = 2 * TICKS_PER_SECOND
    const val STUCK_TICKS = 3 * TICKS_PER_SECOND
    const val STUCK_HORIZONTAL = 0.5
    const val CHASE_RADIUS_MAX = 16.0
    const val CHASE_RADIUS_START = 16.0
    const val RADIUS_RECOVER_PER_SECOND = 0.5
    const val RADIUS_SHRINK_PER_SECOND = 2.0
    const val CHASE_LOSE_INTEREST_TICKS = 5 * TICKS_PER_SECOND
    const val COUNTER_END_TICKS = 3 * TICKS_PER_SECOND
    const val REPATH_TICKS = 10
    const val SKELETON_BOW_RANGE = 15.0
    const val SKELETON_BOW_SPEED = 1.6f

    val DIMENSION: ResourceKey<Level> = ResourceKey.create(Registries.DIMENSION, WallwarMod.idOf("match"))
}
