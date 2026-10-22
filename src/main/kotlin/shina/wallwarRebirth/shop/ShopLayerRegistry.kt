package shina.wallwarRebirth.shop

import com.mojang.serialization.JsonOps
import shina.wallwarRebirth.WallwarMod
import shina.wallwarRebirth.shop.element.ShopLayer
import net.fabricmc.fabric.api.resource.v1.DataResourceLoader
import net.minecraft.core.HolderLookup
import net.minecraft.core.Registry
import net.minecraft.core.registries.Registries
import net.minecraft.resources.FileToIdConverter
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.packs.resources.PreparableReloadListener
import net.minecraft.server.packs.resources.PreparableReloadListener.PreparationBarrier
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener
import net.minecraft.tags.TagLoader
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import kotlin.jvm.optionals.getOrNull
import kotlin.properties.Delegates

object ShopLayerRegistry {
    private val logger = LoggerFactory.getLogger("hgwar/ShopLayerRegistry")
    private val ID = WallwarMod.idOf("shop_layer")
    private val REGISTRY_KEY: ResourceKey<Registry<ShopLayer>> = ResourceKey.createRegistryKey(ID)
    private var dataLoader: ShopLayerDataLoader by Delegates.notNull()

    fun init() {
        DataResourceLoader.get().registerReloader(ID) { registries ->
            dataLoader = ShopLayerDataLoader(registries)
            dataLoader
        }
    }

    fun get(id: Identifier): ShopLayer? = dataLoader.get(id).getOrNull()

    @JvmStatic
    fun getTag(id: Identifier): Collection<ShopLayer> = dataLoader.shopLayerTags.getOrDefault(id, emptyList())

    private class ShopLayerDataLoader(registries: HolderLookup.Provider) : PreparableReloadListener {
        var shopLayers: Map<Identifier, ShopLayer> = emptyMap()
        var shopLayerTags: Map<Identifier, List<ShopLayer>> = emptyMap()
        private val ops = registries.createSerializationContext(JsonOps.INSTANCE)
        private val lister = FileToIdConverter.registry(REGISTRY_KEY)
        private val tagLoader = TagLoader({ resLoc, _ -> this.get(resLoc) }, Registries.tagsDirPath(REGISTRY_KEY))

        fun get(id: Identifier): Optional<ShopLayer> = Optional.ofNullable(shopLayers[id])

        @Suppress("ForbiddenVoid")
        override fun reload(
            sharedState: PreparableReloadListener.SharedState,
            prepareExecutor: Executor,
            preparationBarrier: PreparationBarrier,
            applyExectutor: Executor
        ): CompletableFuture<Void> {
            val resourceManager = sharedState.resourceManager()
            val future1 = CompletableFuture.supplyAsync({ tagLoader.load(resourceManager) }, prepareExecutor)
            val future2 = CompletableFuture.supplyAsync({ prepareLayers(resourceManager) }, prepareExecutor)

            return future1
                .thenCombine(future2) { tagEntries, layers -> tagEntries to layers }
                .thenCompose(preparationBarrier::wait)
                .thenAcceptAsync({ (tagEntryMap, layerMap) ->
                    layerMap.keys.forEach { key ->
                        logger.info("Loaded shop layer $key")
                    }
                    shopLayers = layerMap
                    shopLayerTags = tagLoader.build(tagEntryMap)
                    logger.info("Loaded ${shopLayerTags.size} shop layer tags")
                }, applyExectutor)
        }

        private fun prepareLayers(resourceManager: ResourceManager): Map<Identifier, ShopLayer> {
            val map = mutableMapOf<Identifier, ShopLayer>()
            SimpleJsonResourceReloadListener.scanDirectory(resourceManager, this.lister, this.ops, ShopLayer.CODEC, map)
            return map
        }
    }
}
