package shina.wallwarRebirth.targeting

import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import shina.wallwarRebirth.contract.WallwarTeams
import kotlin.math.sqrt

object Targeting {
    const val TIE = 0.001

    fun hostile(left: String, right: String): Boolean = WallwarTeams.hostile(left, right)

    /** Spectator and creative players never enter targeting, chase, or counter lists. */
    @JvmStatic
    fun canTargetPlayer(player: Player): Boolean = !player.isSpectator && !player.isCreative

    fun distanceToBox(origin: Vec3, box: AABB): Double {
        val dx = if (origin.x in box.minX..box.maxX) 0.0 else origin.x.coerceIn(box.minX, box.maxX) - origin.x
        val dy = if (origin.y in box.minY..box.maxY) 0.0 else origin.y.coerceIn(box.minY, box.maxY) - origin.y
        val dz = if (origin.z in box.minZ..box.maxZ) 0.0 else origin.z.coerceIn(box.minZ, box.maxZ) - origin.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    fun <T> nearest(
        items: List<T>,
        distance: (T) -> Double,
        health: (T) -> Float,
        id: (T) -> Int,
        tie: Double,
    ): T? = items.minWithOrNull(
        compareBy<T> { (distance(it) / tie).toLong() }
            .thenBy { health(it) }
            .thenBy { id(it) },
    )
}

data class ActorTarget(val entity: LivingEntity, val team: String)

data class CoreTarget(
    val entityId: Int,
    val pos: net.minecraft.core.BlockPos,
    val team: String,
    val health: Int,
    val buildingId: String,
    val defensive: Boolean,
    val base: Boolean,
    val box: AABB,
)
