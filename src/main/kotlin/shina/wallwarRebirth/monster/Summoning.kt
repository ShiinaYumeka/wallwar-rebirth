package shina.wallwarRebirth.monster
import shina.wallwarRebirth.rules.MatchRules

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

object Summoning {
    fun spawn(
        level: ServerLevel,
        definition: MonsterDefinition,
        team: String,
        x: Double,
        y: Double,
        z: Double,
        yaw: Float,
        swift: Boolean = false,
    ): Boolean = place(level, definition, team, x, y, z, yaw, swift) != null

    fun place(
        level: ServerLevel,
        definition: MonsterDefinition,
        team: String,
        x: Double,
        y: Double,
        z: Double,
        yaw: Float,
        swift: Boolean = false,
        home: BlockPos? = null,
    ): LivingEntity? {
        val mob = create(level, definition) ?: return null
        val unit = mob as SummonedUnit
        unit.summonedTeam = team
        unit.kindId = definition.id
        (mob as? SummonedCreeper)?.refreshLook()
        unit.mind.bindHome(home)
        SummonedGear.applyStats(mob, definition, fill = true)
        SummonedGear.apply(mob, unit)
        if (swift) {
            mob.addEffect(MobEffectInstance(MobEffects.SPEED, MobEffectInstance.INFINITE_DURATION, 0, false, true))
        }
        mob.snapTo(x, y, z, yaw, 0f)
        mob.addTag(MatchRules.SUMMONER_TAG)
        return if (level.addFreshEntity(mob)) mob else null
    }

    fun footprint(definition: MonsterDefinition, feet: BlockPos): List<BlockPos> {
        val type = entityType(definition.id) ?: return listOf(feet, feet.above())
        val box = type.dimensions.makeBoundingBox(feet.x + 0.5, feet.y.toDouble(), feet.z + 0.5)
        val minX = Mth.floor(box.minX)
        val maxX = Mth.floor(box.maxX - BOX_EDGE)
        val minY = Mth.floor(box.minY)
        val maxY = Mth.floor(box.maxY - BOX_EDGE)
        val minZ = Mth.floor(box.minZ)
        val maxZ = Mth.floor(box.maxZ - BOX_EDGE)
        val cells = ArrayList<BlockPos>()
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    cells.add(BlockPos(x, y, z))
                }
            }
        }
        return if (cells.isEmpty()) listOf(feet) else cells
    }

    fun canOccupy(level: ServerLevel, pos: BlockPos, player: ServerPlayer?): Boolean {
        if (level.isOutsideBuildHeight(pos)) return false
        val state = level.getBlockState(pos)
        if (state.isAir) return true
        val actor = player ?: level.players().firstOrNull() ?: return false
        val hit = BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)
        val context = BlockPlaceContext(actor, InteractionHand.MAIN_HAND, ItemStack.EMPTY, hit)
        return state.canBeReplaced(context)
    }

    private fun entityType(id: String): EntityType<*>? = when (id) {
        Monsters.SKELETON.id -> ModMonsters.SKELETON
        Monsters.GOLEM.id -> ModMonsters.GOLEM
        Monsters.SPIDER.id -> ModMonsters.SPIDER
        Monsters.ZOMBIE.id -> ModMonsters.ZOMBIE
        Monsters.MINER.id -> ModMonsters.MINER
        Monsters.HUSK.id -> ModMonsters.HUSK
        Monsters.DEATH_KNIGHT.id -> ModMonsters.DEATH_KNIGHT
        Monsters.CREEPER.id, Monsters.CHARGED_CREEPER.id -> ModMonsters.CREEPER
        Monsters.WARRIOR.id -> ModMonsters.WARRIOR
        Monsters.PILLAGER.id -> ModMonsters.PILLAGER
        Monsters.VINDICATOR.id -> ModMonsters.VINDICATOR
        Monsters.EVOKER.id -> ModMonsters.EVOKER
        Monsters.ILLUSIONER.id -> ModMonsters.ILLUSIONER
        Monsters.RAVAGER.id -> ModMonsters.RAVAGER
        Monsters.VILLAGER.id -> ModMonsters.VILLAGER
        else -> null
    }

    private fun create(level: ServerLevel, definition: MonsterDefinition): Mob? = when (definition.id) {
        Monsters.SKELETON.id -> SummonedSkeleton(ModMonsters.SKELETON, level)
        Monsters.GOLEM.id -> SummonedGolem(ModMonsters.GOLEM, level)
        Monsters.SPIDER.id -> SummonedSpider(ModMonsters.SPIDER, level)
        Monsters.ZOMBIE.id -> SummonedZombie(ModMonsters.ZOMBIE, level)
        Monsters.MINER.id -> SummonedMiner(ModMonsters.MINER, level)
        Monsters.HUSK.id -> SummonedHusk(ModMonsters.HUSK, level)
        Monsters.DEATH_KNIGHT.id -> SummonedDeathknight(ModMonsters.DEATH_KNIGHT, level)
        Monsters.CREEPER.id, Monsters.CHARGED_CREEPER.id -> SummonedCreeper(ModMonsters.CREEPER, level)
        Monsters.WARRIOR.id -> SummonedWarrior(ModMonsters.WARRIOR, level)
        Monsters.PILLAGER.id -> SummonedPillager(ModMonsters.PILLAGER, level)
        Monsters.VINDICATOR.id -> SummonedVindicator(ModMonsters.VINDICATOR, level)
        Monsters.EVOKER.id -> SummonedEvoker(ModMonsters.EVOKER, level)
        Monsters.ILLUSIONER.id -> SummonedIllusioner(ModMonsters.ILLUSIONER, level)
        Monsters.RAVAGER.id -> SummonedRavager(ModMonsters.RAVAGER, level)
        Monsters.VILLAGER.id -> SummonedVillager(ModMonsters.VILLAGER, level)
        else -> null
    }

    private const val BOX_EDGE = 1.0E-6
}
