package shina.wallwarRebirth

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import shina.wallwarRebirth.building.ModSites
import shina.wallwarRebirth.command.WallwarCommands
import shina.wallwarRebirth.flux.gui.backend.FluxGuiRegistry
import shina.wallwarRebirth.item.ModComponents
import shina.wallwarRebirth.item.ModItems
import shina.wallwarRebirth.item.PolymerLoreListener
import shina.wallwarRebirth.shop.ShopLayerRegistry
import shina.wallwarRebirth.shop.ShopMaterialRegistry
import shina.wallwarRebirth.shop.branch.BranchGroupRegistry

class WallwarRebirth : ModInitializer {
    override fun onInitialize() {
        ModComponents.init()
        ModItems.init()
        ModSites.init()
        PolymerLoreListener.register()
        ShopMaterialRegistry.init()
        ShopLayerRegistry.init()
        BranchGroupRegistry.init()
        FluxGuiRegistry.init()
        PolymerResourcePackUtils.addModAssets(WallwarMod.MOD_ID)
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(WallwarCommands.root.node)
        }
    }
}
