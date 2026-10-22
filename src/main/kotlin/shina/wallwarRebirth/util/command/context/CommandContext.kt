package shina.wallwarRebirth.util.command.context

import com.google.common.base.Supplier
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.ChatType
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.OutgoingChatMessage
import kotlin.reflect.KProperty
import com.mojang.brigadier.context.CommandContext as BrigadierCommandContext

@Suppress("unused")
class CommandContext(val context: BrigadierCommandContext<CommandSourceStack>) {
    inline operator fun <reified T : Any> getValue(thisRef: Any?, property: KProperty<*>): T =
        when (T::class.java) {
            BrigadierCommandContext::class.java -> context as T
            CommandSourceStack::class.java -> context.source as T
            else -> context.getArgument<T>(property.name, T::class.java)
        }

    fun sendSystemMessage(component: Component) {
        this.context.source.sendSystemMessage(component)
    }

    fun sendFailure(component: Component) {
        this.context.source.sendFailure(component)
    }

    fun sendSuccess(messageSupplier: Supplier<Component>, allowLogging: Boolean) {
        this.context.source.sendSuccess(messageSupplier, allowLogging)
    }

    fun sendChatMessage(message: OutgoingChatMessage, shouldFilter: Boolean, boundChatType: ChatType.Bound) {
        this.context.source.sendChatMessage(message, shouldFilter, boundChatType)
    }
}
