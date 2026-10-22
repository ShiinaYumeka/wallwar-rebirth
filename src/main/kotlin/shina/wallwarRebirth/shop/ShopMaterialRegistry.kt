package shina.wallwarRebirth.shop

import shina.wallwarRebirth.WallwarMod
import shina.wallwarRebirth.shop.element.Material
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.registry.DynamicRegistries
import net.minecraft.core.Holder
import net.minecraft.core.Registry
import net.minecraft.core.RegistryAccess
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.optionals.getOrNull

object ShopMaterialRegistry {
    private val logger = LoggerFactory.getLogger("hgwar/ShopMaterialRegistry")

    private val cache = ConcurrentHashMap<Identifier, Holder.Reference<Material>>()
    private val missing: ConcurrentHashMap.KeySetView<Identifier, Boolean> = ConcurrentHashMap.newKeySet()

    private val materialSpecificityComparator =
        compareBy<Holder<Material>> { holder ->
            holder.value().itemFilter.items.getOrNull()?.size() ?: Int.MAX_VALUE
        }.thenBy { it.registeredName }

    @JvmField
    val REGISTRY_KEY: ResourceKey<Registry<Material>> =
        ResourceKey.createRegistryKey(WallwarMod.idOf("material"))

    fun init() {
        DynamicRegistries.register(REGISTRY_KEY, Material.CODEC)
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register { server, _, _ ->
            cache.clear()
            missing.clear()
            val lookup = server.registryAccess().lookupOrThrow(REGISTRY_KEY)
            lookup.listElements().forEach { holder ->
                val id = holder.key().identifier()
                cache[id] = holder
                val stack = holder.value().representativeStack
                if (stack.`is`(Items.BARRIER)) {
                    logger.warn("Could not resolve representative item stack for material {}", id)
                }
            }
        }
    }

    @JvmStatic
    fun getMaterial(access: RegistryAccess, id: Identifier): Material? {
        cache[id]?.let { return it.value() }
        if (id in missing) return null
        return try {
            val material = access.lookupOrThrow(REGISTRY_KEY).getOrThrow(ResourceKey.create(REGISTRY_KEY, id))
            cache[id] = material
            material.value()
        } catch (e: Exception) {
            if (missing.add(id)) {
                logger.warn("Missing material \"{}\"", id, e)
            }
            null
        }
    }

    @JvmStatic
    fun getMaterials(): Collection<Holder<Material>> = cache.values

    fun findMaterialId(material: Material): Identifier? {
        var containsMatch: Identifier? = null
        for ((id, holder) in cache) {
            if (holder == material) return id
            if (containsMatch == null && (holder.value().contains(material) || material.contains(holder.value()))) {
                containsMatch = id
            }
        }
        return containsMatch
    }

    fun findMaterialIdByStack(stack: ItemStack, access: RegistryAccess): Identifier? {
        if (stack.isEmpty) return null
        return runCatching {
            val lookup = access.lookupOrThrow(REGISTRY_KEY)
            val matches = lookup.listElements().filter { it.value().matches(stack) }.toList()
            matches.minWithOrNull(materialSpecificityComparator)?.let { holder ->
                val id = holder.key().identifier()
                cache[id] = holder
                id
            }
        }.getOrElse { e ->
            logger.warn("Failed to resolve material id by stack {}", stack.item, e)
            null
        }
    }

    fun findFlowMaterialIdByStack(stack: ItemStack, access: RegistryAccess): Identifier? =
        findMaterialIdByStack(stack, access)
}
