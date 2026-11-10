package shina.wallwarRebirth.contract

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level

/** Narrow match facts buildings and summons are allowed to read or report. */
interface MatchPort {
    fun onBaseFallen(level: ServerLevel, team: String)
    fun isArena(level: Level): Boolean
    fun isFrozen(level: Level): Boolean
    fun isFight(): Boolean
}
