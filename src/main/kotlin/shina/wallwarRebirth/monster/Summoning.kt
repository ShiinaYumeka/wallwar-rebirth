package shina.wallwarRebirth.monster

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Mob
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
    ): Boolean {
        val mob = create(level, definition) ?: return false
        val unit = mob as SummonedUnit
        unit.summonedTeam = team
        unit.kindId = definition.id
        SummonedGear.applyStats(mob, definition, fill = true)
        SummonedGear.apply(mob, unit)
        mob.snapTo(x, y, z, yaw, 0f)
        return level.addFreshEntity(mob)
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
        Monsters.ZOMBIE.id -> SummonedZombie(ModMonsters.ZOMBIE, level)
        else -> null
    }
}
