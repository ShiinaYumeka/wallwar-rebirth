package shina.wallwarRebirth.bootstrap

import net.minecraft.resources.Identifier

/**
 * Leftover datapacks `data/wallwar` and `data/minecraft` stay in the jar as a reference library.
 * They are not loaded as a whole, and they are not deleted by the modular refactor.
 *
 * Structures still referenced from Kotlin (via [net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager]):
 * - `data/wallwar/structure/electric.nbt` — Tesla tower body ([TESLA])
 * - `data/wallwar/structure/trans.nbt` — relay body ([RELAY])
 *
 * No other Kotlin source reads a path under those two datapacks. Building templates that this mod
 * actually places live under `data/wallwar-rebirth/structure`.
 */
object LegacyDatapacks {
    val TESLA: Identifier = Identifier.fromNamespaceAndPath("wallwar", "electric")
    val RELAY: Identifier = Identifier.fromNamespaceAndPath("wallwar", "trans")
}
