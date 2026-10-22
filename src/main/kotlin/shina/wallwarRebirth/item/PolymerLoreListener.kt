package shina.wallwarRebirth.item

import eu.pb4.polymer.core.api.item.PolymerItemUtils
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.component.ItemLore

object PolymerLoreListener {
    fun register() {
        PolymerItemUtils.ITEM_MODIFICATION_EVENT.register { origin, virtual, _ ->
            val prior = origin[ModComponents.PRIOR_ITEM_LORE]
            if (prior == null || prior.lines.isEmpty()) {
                virtual
            } else {
                val existing = virtual.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines
                if (existing.size >= prior.lines.size && existing.subList(0, prior.lines.size) == prior.lines) {
                    virtual
                } else {
                    virtual[DataComponents.LORE] = ItemLore(prior.lines + existing)
                    virtual
                }
            }
        }
    }
}
