package shina.wallwarRebirth.copy

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import shina.wallwarRebirth.building.ModuleSlot

object DescriptionStyle {
    private val compactGroup = Regex("""(?<![0-9.])\d+(?:\.\d+)?(?:/\d+(?:\.\d+)?){2}(?![0-9./])""")
    private const val spacedSlash = " / "

    fun plain(text: String, color: ChatFormatting = ChatFormatting.GRAY): Component =
        Component.literal(text).withStyle { style -> style.withColor(color).withItalic(false) }

    fun moduleSlotOf(text: String): ModuleSlot? = when {
        text.startsWith("强化模块 2：") || text.startsWith("强化模块 2:") -> ModuleSlot.SECONDARY
        text.startsWith("强化模块：") || text.startsWith("强化模块:") -> ModuleSlot.PRIMARY
        text.startsWith("扩散模块：") || text.startsWith("扩散模块:") -> ModuleSlot.DIFFUSION
        else -> null
    }

    fun styled(
        text: String,
        level: Int? = null,
        installed: Set<ModuleSlot> = emptySet(),
        maxLevel: Int? = null,
    ): Component {
        moduleSlotOf(text)?.let { slot ->
            val color = if (slot in installed) ChatFormatting.GREEN else ChatFormatting.GRAY
            return plain(text, color)
        }
        if (level == null || level <= 0) return plain(text)
        return highlightLevel(text, level, maxLevel)
    }

    private fun highlightLevel(text: String, level: Int, maxLevel: Int?): Component {
        data class Span(val start: Int, val end: Int)
        val spans = ArrayList<Span>()

        for (match in compactGroup.findAll(text)) {
            val parts = match.value.split("/")
            val index = (level - 1).coerceIn(0, parts.lastIndex)
            var offset = match.range.first
            for ((i, part) in parts.withIndex()) {
                if (i > 0) offset += 1
                val end = offset + part.length
                if (i == index) spans.add(Span(offset, end))
                offset = end
            }
        }

        if (spacedSlash in text) {
            val label = text.indexOf('：').let { end -> if (end >= 0) end + 1 else 0 }
            val parts = text.substring(label).split(spacedSlash)
            val expected = when {
                maxLevel != null && parts.size == maxLevel -> maxLevel
                parts.size == 3 -> 3
                parts.size == 2 -> 2
                else -> 0
            }
            if (expected > 0 && parts.size == expected) {
                var offset = label
                val index = (level - 1).coerceIn(0, parts.lastIndex)
                for ((i, part) in parts.withIndex()) {
                    if (i == index) spans.add(Span(offset, offset + part.length))
                    offset += part.length
                    if (i < parts.lastIndex) offset += spacedSlash.length
                }
            }
        }

        if (spans.isEmpty()) return plain(text)
        spans.sortBy { it.start }
        val root: MutableComponent = Component.empty().withStyle { style ->
            style.withItalic(false).withColor(ChatFormatting.GRAY)
        }
        var cursor = 0
        for (span in spans) {
            if (span.start < cursor) continue
            if (span.start > cursor) {
                root.append(plain(text.substring(cursor, span.start)))
            }
            root.append(plain(text.substring(span.start, span.end), ChatFormatting.GREEN))
            cursor = span.end
        }
        if (cursor < text.length) root.append(plain(text.substring(cursor)))
        return root
    }
}
