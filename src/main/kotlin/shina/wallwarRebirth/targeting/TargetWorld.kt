package shina.wallwarRebirth.targeting

import net.minecraft.server.level.ServerLevel

/** Lists candidates. It does not shoot, path, or apply damage. */
interface TargetWorld {
    fun players(level: ServerLevel): List<ActorTarget>
    fun summons(level: ServerLevel): List<ActorTarget>
    fun cores(level: ServerLevel): List<CoreTarget>
}
