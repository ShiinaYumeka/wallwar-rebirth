package shina.wallwarRebirth.item

import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
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

    @JvmField
    val REINFORCE: Item = module("reinforce_module", "强化模块", Items.LAPIS_LAZULI)

    @JvmField
    val REINFORCE_TWO: Item = module("reinforce_module_2", "强化模块 2", Items.LAPIS_BLOCK)

    fun init() {
        EXAMPLE_TOKEN
        REINFORCE
        REINFORCE_TWO
    }

    private fun module(path: String, name: String, icon: Item): Item = register(path) { properties ->
        SimpleModdedItem(
            properties = properties.stacksTo(1).component(
                DataComponents.CUSTOM_NAME,
                Component.literal(name).withStyle { style -> style.withItalic(false) },
            ),
            polymerItem = icon,
            interactionType = PolymerInteractionType.CLASSIC,
        )
    }

    private fun <T : Item> register(path: String, constructor: (Item.Properties) -> T): T {
        val id = WallwarMod.idOf(path)
        val key = ResourceKey.create(Registries.ITEM, id)
        return Registry.register(BuiltInRegistries.ITEM, key, constructor(Item.Properties().setId(key)))
    }
}
