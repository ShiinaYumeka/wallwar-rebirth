package shina.wallwarRebirth.item

import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import shina.wallwarRebirth.WallwarMod
import shina.wallwarRebirth.item.core.PolymerInteractionType

object ModItems {
    @JvmField
    val EXAMPLE_TOKEN: Item = register("example_token") { properties ->
        SimpleModdedItem(
            properties = properties.stacksTo(1),
            polymerItem = Items.PAPER,
            interactionType = PolymerInteractionType.CLASSIC,
        )
    }

    fun init() {
        EXAMPLE_TOKEN
    }

    private fun <T : Item> register(path: String, constructor: (Item.Properties) -> T): T {
        val id = WallwarMod.idOf(path)
        val key = ResourceKey.create(Registries.ITEM, id)
        return Registry.register(BuiltInRegistries.ITEM, key, constructor(Item.Properties().setId(key)))
    }
}
