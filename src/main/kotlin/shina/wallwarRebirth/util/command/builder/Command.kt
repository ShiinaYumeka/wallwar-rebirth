package shina.wallwarRebirth.util.command.builder

import com.mojang.brigadier.Command
import com.mojang.brigadier.builder.ArgumentBuilder
import net.minecraft.commands.CommandSourceStack

@CommandDSL
interface Command {
    val node: ArgumentBuilder<CommandSourceStack, *>

    companion object {
        const val SINGLE_SUCCESS = Command.SINGLE_SUCCESS
        const val ILLEGAL_ARGUMENT = -2
    }
}
