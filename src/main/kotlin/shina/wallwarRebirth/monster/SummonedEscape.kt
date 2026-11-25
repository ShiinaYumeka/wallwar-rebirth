package shina.wallwarRebirth.monster

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.BlockTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Mob
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import shina.wallwarRebirth.building.SiteCoreBlock
import shina.wallwarRebirth.building.SitePartBlock
import shina.wallwarRebirth.building.SiteStructureBlock
import shina.wallwarRebirth.rules.MatchRules
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Ground summons that stay put while trying to move break a plank blocking
 * the body, or pillar out of a pit whose lip is at least two blocks high.
 */
internal object SummonedEscape {
    class Memory {
        var ready = false
        var ticks = 0
        var x = 0.0
        var z = 0.0
        var breakPos: BlockPos? = null
        var breakTicks = 0
        var pillarFloor = Int.MIN_VALUE
        var placeWait = 0
        var pendingPlace: BlockPos? = null
        var escaping = false
        var acted = false
    }

    enum class PillarStep { NONE, WORKING, PLACED }

    fun pillarFromPit(mob: Mob, level: ServerLevel, memory: Memory, mayStart: Boolean): PillarStep {
        memory.acted = false
        if (memory.placeWait > 0) memory.placeWait--
        val pending = memory.pendingPlace
        if (pending != null) {
            if (mob.blockX != pending.x || mob.blockZ != pending.z || memory.placeWait == 0) {
                memory.pendingPlace = null
            } else {
                if (mob.y < pending.y + 1.0) return PillarStep.WORKING
                finishPendingPlace(mob, level, memory)
                return if (memory.acted) PillarStep.PLACED else PillarStep.NONE
            }
        }
        if (!mayStart || !mob.onGround() || memory.placeWait > 0) return PillarStep.NONE
        val feet = mob.blockPosition()
        if (memory.pillarFloor != Int.MIN_VALUE && feet.y - memory.pillarFloor >= MatchRules.PILLAR_MAX_RISE) {
            return PillarStep.NONE
        }
        val hasRim = Direction.Plane.HORIZONTAL.any { side ->
            val column = feet.relative(side)
            if (blockedByBuilding(level, column, feet.y) || !canPillar(mob, level, column)) return@any false
            val rim = column.above(ledgeRise(level, column, feet.y))
            open(level, rim.above())
        }
        if (!hasRim) return PillarStep.NONE
        mob.navigation.stop()
        mob.moveControl.setWantedPosition(mob.x, mob.y, mob.z, 0.0)
        climb(mob, level, memory)
        return if (memory.acted) PillarStep.WORKING else PillarStep.NONE
    }

    fun tick(mob: Mob, level: ServerLevel, goal: Vec3?, trying: Boolean, memory: Memory) {
        memory.acted = false
        if (memory.placeWait > 0) memory.placeWait--
        if (!trying || goal == null) {
            reset(memory)
            return
        }
        if (!memory.ready) {
            memory.ready = true
            memory.ticks = 0
            memory.x = mob.x
            memory.z = mob.z
            return
        }
        val moved = hypot(mob.x - memory.x, mob.z - memory.z)
        if (!memory.escaping) {
            memory.ticks++
            if (memory.ticks < MatchRules.ESCAPE_STUCK_TICKS) return
            memory.ticks = 0
            memory.x = mob.x
            memory.z = mob.z
            if (moved >= MatchRules.ESCAPE_STUCK_DISTANCE) return
            memory.escaping = true
        } else if (moved >= MatchRules.ESCAPE_STUCK_DISTANCE) {
            reset(memory)
            return
        }
        finishPendingPlace(mob, level, memory)
        val cell = blockingCell(level, mob, goal)
        if (cell == null) {
            if (memory.escaping) reset(memory)
            return
        }
        val feetY = mob.blockPosition().y
        val plank = blockingPlank(level, cell, feetY)
        if (plank != null) {
            memory.pendingPlace = null
            breakPlank(mob, level, plank, memory)
            return
        }
        if (blockedByBuilding(level, cell, feetY)) return
        if (ledgeRise(level, cell, feetY) <= 1) {
            reset(memory)
            return
        }
        if (canPillar(mob, level, cell)) climb(mob, level, memory)
    }

    private fun steps(mob: Mob, goal: Vec3): Pair<BlockPos, BlockPos?>? {
        val dx = goal.x - mob.x
        val dz = goal.z - mob.z
        if (hypot(dx, dz) < 0.2) return null
        val signX = when {
            dx > 0.3 -> 1
            dx < -0.3 -> -1
            else -> 0
        }
        val signZ = when {
            dz > 0.3 -> 1
            dz < -0.3 -> -1
            else -> 0
        }
        if (signX == 0 && signZ == 0) return null
        val feet = mob.blockPosition()
        val alongX = abs(dx) >= abs(dz)
        val primary = when {
            alongX && signX != 0 -> feet.offset(signX, 0, 0)
            !alongX && signZ != 0 -> feet.offset(0, 0, signZ)
            signX != 0 -> feet.offset(signX, 0, 0)
            else -> feet.offset(0, 0, signZ)
        }
        val secondary = if (signX != 0 && signZ != 0) {
            if (alongX) feet.offset(0, 0, signZ) else feet.offset(signX, 0, 0)
        } else {
            null
        }
        return primary to secondary
    }

    private fun blockingCell(level: ServerLevel, mob: Mob, goal: Vec3): BlockPos? {
        val (primary, secondary) = steps(mob, goal) ?: return null
        val feetY = mob.blockPosition().y
        val primaryBlocked = bodyCollision(level, primary, feetY)
        if (primaryBlocked) return primary
        if (secondary != null && bodyCollision(level, secondary, feetY)) return secondary
        return null
    }

    private fun bodyCollision(level: ServerLevel, cell: BlockPos, feetY: Int): Boolean {
        val feet = BlockPos(cell.x, feetY, cell.z)
        return occupies(level, feet) || occupies(level, feet.above())
    }

    private fun occupies(level: ServerLevel, pos: BlockPos): Boolean {
        val state = level.getBlockState(pos)
        return isSite(state) || isPlank(state) || !state.getCollisionShape(level, pos).isEmpty
    }

    private fun blockingPlank(level: ServerLevel, cell: BlockPos, feetY: Int): BlockPos? {
        val feet = BlockPos(cell.x, feetY, cell.z)
        val head = feet.above()
        if (isPlank(level.getBlockState(feet))) return feet
        if (isPlank(level.getBlockState(head))) return head
        return null
    }

    private fun isPlank(state: BlockState): Boolean {
        if (isSite(state)) return false
        return state.`is`(BlockTags.PLANKS)
    }

    private fun breakPlank(mob: Mob, level: ServerLevel, pos: BlockPos, memory: Memory) {
        if (memory.breakPos != pos) {
            memory.breakPos = pos.immutable()
            memory.breakTicks = 0
        }
        memory.breakTicks++
        memory.acted = true
        mob.lookControl.setLookAt(Vec3.atCenterOf(pos))
        if (memory.breakTicks == 1 || memory.breakTicks % 10 == 0) {
            mob.swing(InteractionHand.MAIN_HAND)
        }
        if (memory.breakTicks < MatchRules.PLANK_BREAK_TICKS) return
        if (isPlank(level.getBlockState(pos))) level.destroyBlock(pos, true, mob)
        memory.breakPos = null
        memory.breakTicks = 0
    }

    private fun blockedByBuilding(level: ServerLevel, cell: BlockPos, feetY: Int): Boolean {
        val feet = BlockPos(cell.x, feetY, cell.z)
        return isSite(level.getBlockState(feet)) || isSite(level.getBlockState(feet.above()))
    }

    private fun isSite(state: BlockState): Boolean =
        state.block is SiteCoreBlock || state.block is SiteStructureBlock || state.block is SitePartBlock

    private fun canPillar(mob: Mob, level: ServerLevel, column: BlockPos): Boolean {
        if (!mob.onGround()) return false
        val feet = mob.blockPosition()
        val headroom = level.getBlockState(feet.above(2))
        if (!headroom.canBeReplaced() || isSite(headroom)) return false
        if (isCliff(level, column, feet.y)) return false
        val rise = ledgeRise(level, column, feet.y)
        return rise >= 2 && rise <= MatchRules.PILLAR_MAX_RISE + 1
    }

    private fun isCliff(level: ServerLevel, column: BlockPos, feetY: Int): Boolean {
        val floor = BlockPos(column.x, feetY - 1, column.z)
        return open(level, floor) && open(level, floor.below())
    }

    private fun ledgeRise(level: ServerLevel, column: BlockPos, feetY: Int): Int {
        var rise = 0
        val limit = MatchRules.PILLAR_MAX_RISE + 1
        while (rise <= limit) {
            val stand = BlockPos(column.x, feetY + rise, column.z)
            if (open(level, stand)) return rise
            rise++
        }
        return rise
    }

    private fun open(level: ServerLevel, pos: BlockPos): Boolean =
        level.getBlockState(pos).getCollisionShape(level, pos).isEmpty

    private fun finishPendingPlace(mob: Mob, level: ServerLevel, memory: Memory) {
        val pending = memory.pendingPlace ?: return
        if (mob.y <= pending.y + 0.9) return
        val state = level.getBlockState(pending)
        if (state.canBeReplaced() && !isSite(state) &&
            level.setBlockAndUpdate(pending, Blocks.OAK_PLANKS.defaultBlockState())
        ) {
            level.playSound(null, pending, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1f, 0.8f)
            memory.acted = true
        }
        memory.pendingPlace = null
    }

    private fun climb(mob: Mob, level: ServerLevel, memory: Memory) {
        if (!mob.onGround()) return
        val feet = mob.blockPosition()
        val support = feet.below()
        if (open(level, support)) return
        if (memory.pillarFloor == Int.MIN_VALUE) memory.pillarFloor = feet.y
        if (feet.y - memory.pillarFloor >= MatchRules.PILLAR_MAX_RISE) return
        if (memory.placeWait > 0) return
        val state = level.getBlockState(feet)
        if (!state.canBeReplaced() || isSite(state)) return
        memory.pendingPlace = feet.immutable()
        memory.placeWait = MatchRules.PILLAR_PLACE_TICKS
        mob.jumpFromGround()
        memory.acted = true
    }

    private fun reset(memory: Memory) {
        memory.ready = false
        memory.ticks = 0
        memory.escaping = false
        memory.breakPos = null
        memory.breakTicks = 0
        memory.pillarFloor = Int.MIN_VALUE
        memory.pendingPlace = null
        memory.placeWait = 0
    }
}
