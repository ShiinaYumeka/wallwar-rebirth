package shina.wallwarRebirth.item

import eu.pb4.polymer.core.api.other.PolymerComponent
import com.mojang.serialization.Codec
import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.component.ItemLore
import shina.wallwarRebirth.WallwarMod

object ModComponents {
    @JvmField
    val PRIOR_ITEM_LORE: DataComponentType<ItemLore> = register("prior_item_lore") {
        persistent(ItemLore.CODEC).cacheEncoding()
    }

    @JvmField
    val TERMINAL_TEAM: DataComponentType<String> = register("terminal_team") {
        persistent(Codec.STRING)
    }

    fun init() {
        PRIOR_ITEM_LORE
        TERMINAL_TEAM
    }

    private inline fun <reified T : Any> register(
        name: String,
        builder: DataComponentType.Builder<T>.() -> DataComponentType.Builder<T>,
    ): DataComponentType<T> {
        val componentType = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            WallwarMod.idOf(name),
            builder(DataComponentType.builder()).build(),
        )
        PolymerComponent.registerDataComponent(componentType)
        return componentType
    }
}
