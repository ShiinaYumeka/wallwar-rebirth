package shina.wallwarRebirth.targeting

import net.minecraft.world.phys.Vec3

enum class TargetKind { PLAYER, SUMMON, CORE }

data class TargetRequest(
    val origin: Vec3,
    val range: Double?,
    val team: String,
    val kinds: Set<TargetKind>,
    val skipDefensive: Boolean = false,
    val excludeCorePos: net.minecraft.core.BlockPos? = null,
    val tie: Double = Targeting.TIE,
)
