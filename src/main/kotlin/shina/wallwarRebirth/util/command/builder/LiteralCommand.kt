package shina.wallwarRebirth.util.command.builder

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.commands.CommandSourceStack

@CommandDSL
class LiteralCommand(root: String) : Command {
    override val node: LiteralArgumentBuilder<CommandSourceStack> = LiteralArgumentBuilder.literal(root)
}

fun literalCommand(literal: String, block: LiteralCommand.() -> Unit) = LiteralCommand(literal).apply(block)
