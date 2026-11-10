package shina.wallwarRebirth.monster

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
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
    ): LivingEntity? {
        val mob = create(level, definition) ?: return null
        val unit = mob as SummonedUnit
        unit.summonedTeam = team
        unit.kindId = definition.id
        SummonedGear.applyStats(mob, definition, fill = true)
        SummonedGear.apply(mob, unit)
        if (swift) {
            mob.addEffect(MobEffectInstance(MobEffects.SPEED, MobEffectInstance.INFINITE_DURATION, 0, false, true))
        }
        mob.snapTo(x, y, z, yaw, 0f)
        return if (level.addFreshEntity(mob)) mob else null
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

    private fun create(level: ServerLevel, definition: MonsterDefinition): Mob? = when (definition.id) {
        Monsters.SKELETON.id -> SummonedSkeleton(ModMonsters.SKELETON, level)
        Monsters.GOLEM.id -> SummonedGolem(ModMonsters.GOLEM, level)
        Monsters.SPIDER.id -> SummonedSpider(ModMonsters.SPIDER, level)
        Monsters.ZOMBIE.id -> SummonedZombie(ModMonsters.ZOMBIE, level)
        Monsters.MINER.id -> SummonedMiner(ModMonsters.MINER, level)
        Monsters.HUSK.id -> SummonedHusk(ModMonsters.HUSK, level)
        Monsters.DEATH_KNIGHT.id -> SummonedDeathknight(ModMonsters.DEATH_KNIGHT, level)
        Monsters.CREEPER.id -> SummonedCreeper(ModMonsters.CREEPER, level)
        Monsters.WARRIOR.id -> SummonedWarrior(ModMonsters.WARRIOR, level)
        else -> null
    }
}
