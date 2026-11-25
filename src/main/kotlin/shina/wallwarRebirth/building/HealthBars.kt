package shina.wallwarRebirth.building

import net.minecraft.ChatFormatting
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil
import kotlin.math.min

object HealthBars {
    private val partial = arrayOf("▏", "▎", "▍", "▌", "▋", "▊", "▉")
    private const val SIDE_OFFSET = 0.6

    fun tick(site: SiteCoreBlockEntity, level: ServerLevel, definition: BuildingDefinition) {
        val max = definition.maxHealth(site.buildingLevel)
        val label = find(site, level)
        if (site.health !in 1..<max) {
            label?.discard()
            site.healthDisplayId = null
            return
        }
        val position = position(site, level)
        val shown = label ?: spawn(site, level, position)
        shown.buildingTeam = site.team
        shown.currentHealth = site.health
        shown.maxHealth = max
        shown.corePos = site.blockPos
        shown.refresh()
        if (shown.position() != position) {
            shown.snapTo(position.x, position.y, position.z, 0f, 0f)
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

    private fun position(site: SiteCoreBlockEntity, level: ServerLevel): Vec3 {
        val core = site.blockPos
        val center = Vec3(core.x + 0.5, core.y + 1.2, core.z + 0.5)
        val above = core.above()
        if (level.getBlockState(above).isAir) return center

        val front = site.facingDirection()
        val side = if (front.axis.isHorizontal && level.getBlockState(above.relative(front)).isAir) {
            front
        } else {
            Direction.Plane.HORIZONTAL.firstOrNull { level.getBlockState(above.relative(it)).isAir }
                ?: return center
        }
        return center.add(side.stepX * SIDE_OFFSET, 0.0, side.stepZ * SIDE_OFFSET)
    }

    private fun spawn(site: SiteCoreBlockEntity, level: ServerLevel, position: Vec3): BuildingHealthLabel {
        val label = BuildingHealthLabel(shina.wallwarRebirth.monster.ModMonsters.HEALTH_LABEL, level)
        label.corePos = site.blockPos
        label.snapTo(position.x, position.y, position.z, 0f, 0f)
        level.addFreshEntity(label)
        return label
    }
}
