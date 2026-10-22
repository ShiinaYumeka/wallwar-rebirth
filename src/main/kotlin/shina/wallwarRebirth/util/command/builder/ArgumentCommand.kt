package shina.wallwarRebirth.util.command.builder

import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import shina.wallwarRebirth.util.command.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import java.util.concurrent.CompletableFuture

@CommandDSL
class ArgumentCommand<T>(root: String, type: ArgumentType<T>) : Command {
    override val node: RequiredArgumentBuilder<CommandSourceStack, T> = RequiredArgumentBuilder.argument(root, type)

    fun suggests(block: CommandContext.(builder: SuggestionsBuilder) -> CompletableFuture<Suggestions>) {
        node.suggests { context, builder ->
            CommandContext(context).block(builder)
        }
    }

    fun suggests(provider: SuggestionProvider<CommandSourceStack>) {
        node.suggests(provider)
    }
}
