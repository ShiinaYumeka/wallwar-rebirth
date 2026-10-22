package shina.wallwarRebirth.shop.branch

import com.mojang.serialization.JsonOps
import shina.wallwarRebirth.WallwarMod
import net.fabricmc.fabric.api.resource.v1.DataResourceLoader
import net.minecraft.core.HolderLookup
import net.minecraft.resources.FileToIdConverter
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.PreparableReloadListener
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

object BranchGroupRegistry {
    private val logger = LoggerFactory.getLogger("hgwar/BranchGroupRegistry")
    private var branchGroups: Map<Identifier, BranchGroup> = emptyMap()

    fun init() {
        DataResourceLoader.get().registerReloader(WallwarMod.idOf("branch_group")) { registries ->
            BranchGroupDataLoader(registries)
        }
    }

    fun get(id: Identifier): BranchGroup? = branchGroups[id]

    private class BranchGroupDataLoader(registries: HolderLookup.Provider) : PreparableReloadListener {
        private val ops = registries.createSerializationContext(JsonOps.INSTANCE)
        private val lister = FileToIdConverter.json("${WallwarMod.MOD_ID}/branch_group")

        @Suppress("ForbiddenVoid")
        override fun reload(
            sharedState: PreparableReloadListener.SharedState,
            prepareExecutor: Executor,
            preparationBarrier: PreparableReloadListener.PreparationBarrier,
            applyExecutor: Executor
        ): CompletableFuture<Void> {
            val future = CompletableFuture.supplyAsync(
                { prepareGroups(sharedState.resourceManager()) },
                prepareExecutor
            )
            return future
                .thenCompose(preparationBarrier::wait)
                .thenAcceptAsync({ groups ->
                    branchGroups = groups
                    groups.keys.forEach { key ->
                        logger.info("Loaded branch group: $key")
                    }
                }, applyExecutor)
        }

        private fun prepareGroups(resourceManager: ResourceManager): Map<Identifier, BranchGroup> {
            val map = mutableMapOf<Identifier, BranchGroup>()
            SimpleJsonResourceReloadListener
                .scanDirectory(resourceManager, this.lister, this.ops, BranchGroup.CODEC, map)
            return map
        }
    }
}
