package shina.wallwarRebirth.shop.element

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.advancements.criterion.ItemPredicate
import net.minecraft.core.HolderSet
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.*
import kotlin.jvm.optionals.getOrDefault
import kotlin.jvm.optionals.getOrNull

data class Material(
    val description: Component,
    val itemFilter: ItemPredicate,
    val iconOverride: ItemStack?
) {
    private constructor(
        description: Component,
        itemFilter: ItemPredicate,
        iconOverride: Optional<ItemStack>
    ) : this(description, itemFilter, iconOverride.getOrNull())

    fun matches(stack: ItemStack): Boolean = itemFilter.test(stack)

    val representativeStack by lazy { computeRepresentativeStack() }

    private fun computeRepresentativeStack(): ItemStack {
        if (iconOverride != null) return iconOverride
        val set = itemFilter.items.getOrNull() ?: HolderSet.empty()
        if (set.size() == 0) return unresolvedRepresentativeStack()
        for (holder in set) {
            val candidate = ItemStack(holder)
            if (matches(candidate)) return candidate
        }
        return unresolvedRepresentativeStack()
    }

    fun contains(other: Material): Boolean {
        if (other === this) return true
        if (other.itemFilter.count != itemFilter.count) return false
        if (other.itemFilter.components != itemFilter.components) return false

        val thisItems = itemFilter.items.getOrDefault(HolderSet.empty())
        val otherItems = other.itemFilter.items.getOrNull() ?: return true
        return otherItems.all { thisItems.contains(it) }
    }

    companion object {
        val CODEC: Codec<Material> = RecordCodecBuilder.create { instance ->
            instance.group(
                ComponentSerialization.CODEC.fieldOf("description")
                    .forGetter(Material::description),
                ItemPredicate.CODEC.fieldOf("item_filter")
                    .forGetter(Material::itemFilter),
                ItemStack.CODEC.optionalFieldOf("icon_override")
                    .forGetter { Optional.ofNullable(it.iconOverride) }
            ).apply(instance, ::Material)
        }

        private fun unresolvedRepresentativeStack(): ItemStack = Items.BARRIER.defaultInstance
    }
}
