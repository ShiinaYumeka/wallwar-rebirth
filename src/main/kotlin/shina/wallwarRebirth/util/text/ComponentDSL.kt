package shina.wallwarRebirth.util.text

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style

@DslMarker
annotation class ComponentDslMarker

@ComponentDslMarker
class ComponentBuilder {
    private var rootComponent: MutableComponent? = null
    private var lastComponent: MutableComponent? = null

    fun empty(): ComponentBuilder {
        insert(Component.empty())
        return this
    }

    fun text(text: String): ComponentBuilder {
        insert(Component.literal(text))
        return this
    }

    fun translatable(key: String, vararg args: Any): ComponentBuilder {
        insert(Component.translatable(key, *args))
        return this
    }

    fun raw(component: Component): ComponentBuilder {
        insert(component.copy())
        return this
    }

    infix fun formatted(formatting: ChatFormatting): ComponentBuilder {
        lastComponent?.withStyle(formatting)
        return this
    }

    infix fun provide(style: Style): ComponentBuilder {
        lastComponent?.withStyle(style)
        return this
    }

    private fun insert(other: MutableComponent) {
        if (rootComponent == null) {
            rootComponent = other
            lastComponent = rootComponent
        } else {
            checkNotNull(rootComponent).append(other)
            lastComponent = other
        }
    }

    fun build(): MutableComponent = rootComponent ?: Component.empty()
}

fun component(block: ComponentBuilder.() -> Unit): MutableComponent = ComponentBuilder().apply(block).build()

@ComponentDslMarker
class StyleBuilder {
    private var style = Style.EMPTY

    fun color(formatting: ChatFormatting): StyleBuilder {
        style = style.withColor(formatting)
        return this
    }

    fun color(color: Int?): StyleBuilder {
        if (color != null) {
            style = style.withColor(color)
        }
        return this
    }

    fun italic(italic: Boolean = true): StyleBuilder {
        style = style.withItalic(italic)
        return this
    }

    fun bold(bold: Boolean = true): StyleBuilder {
        style = style.withBold(bold)
        return this
    }

    fun font(font: FontDescription): StyleBuilder {
        style = style.withFont(font)
        return this
    }

    fun shadowColor(color: Int): StyleBuilder {
        style = style.withShadowColor(color)
        return this
    }

    fun build(): Style = style
}

fun style(builder: StyleBuilder.() -> Unit): Style = StyleBuilder().apply(builder).build()
