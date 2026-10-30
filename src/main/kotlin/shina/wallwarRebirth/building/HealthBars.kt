package shina.wallwarRebirth.building

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import kotlin.math.ceil
import kotlin.math.min

object HealthBars {
    private val partial = arrayOf("▏", "▎", "▍", "▌", "▋", "▊", "▉")

    fun tick(site: SiteCoreBlockEntity, level: ServerLevel, definition: BuildingDefinition) {
        val max = definition.maxHealth(site.buildingLevel)
        val label = find(site, level)
        if (site.health <= 0 || site.health >= max) {
            label?.discard()
            site.healthDisplayId = null
            return
        }
        val shown = label ?: spawn(site, level)
        shown.buildingTeam = site.team
        shown.currentHealth = site.health
        shown.maxHealth = max
        shown.corePos = site.blockPos
        shown.refresh()
        val x = site.blockPos.x + 0.5
        val y = site.blockPos.y + 1.2
        val z = site.blockPos.z + 0.5
        if (shown.x != x || shown.y != y || shown.z != z) {
            shown.snapTo(x, y, z, 0f, 0f)
        }
        site.healthDisplayId = shown.uuid
    }

    fun discard(site: SiteCoreBlockEntity) {
        val level = site.level as? ServerLevel ?: return
        find(site, level)?.discard()
        site.healthDisplayId = null
    }

    fun color(viewer: ServerPlayer, buildingTeam: String): ChatFormatting {
        if (buildingTeam.isEmpty()) return ChatFormatting.WHITE
        val viewerTeam = viewer.team?.name ?: ""
        if (viewerTeam.isEmpty() || viewerTeam != buildingTeam) return ChatFormatting.RED
        return ChatFormatting.GREEN
    }

    fun component(current: Int, max: Int, color: ChatFormatting): Component {
        val result = Component.empty()
        val cells = ceil(max / 10.0).toInt().coerceAtLeast(1)
        for (index in 0 until cells) {
            val start = index * 10
            val capacity = min(10, max - start)
            val filled = (current - start).coerceIn(0, capacity)
            val glyphColor = if (filled <= 0) ChatFormatting.DARK_GRAY else color
            result.append(Component.literal(glyph(filled, capacity)).withStyle(glyphColor))
        }
        result.append(Component.literal(" $current/$max").withStyle { style ->
            style.withColor(color).withItalic(false)
        })
        return result
    }

    private fun glyph(filled: Int, capacity: Int): String {
        if (filled <= 0) return "░"
        if (filled >= capacity) return "█"
        val steps = ((filled * 8) / capacity).coerceIn(1, 7)
        return partial[steps - 1]
    }

    private fun find(site: SiteCoreBlockEntity, level: ServerLevel): BuildingHealthLabel? {
        val known = site.healthDisplayId?.let { level.getEntity(it) as? BuildingHealthLabel }
        if (known != null && !known.isRemoved) return known
        val box = net.minecraft.world.phys.AABB(site.blockPos).inflate(1.5)
        return level.getEntitiesOfClass(BuildingHealthLabel::class.java, box)
            .firstOrNull { it.corePos == site.blockPos && !it.isRemoved }
    }

    private fun spawn(site: SiteCoreBlockEntity, level: ServerLevel): BuildingHealthLabel {
        val label = BuildingHealthLabel(shina.wallwarRebirth.monster.ModMonsters.HEALTH_LABEL, level)
        label.corePos = site.blockPos
        label.snapTo(site.blockPos.x + 0.5, site.blockPos.y + 1.2, site.blockPos.z + 0.5, 0f, 0f)
        level.addFreshEntity(label)
        return label
    }
}
