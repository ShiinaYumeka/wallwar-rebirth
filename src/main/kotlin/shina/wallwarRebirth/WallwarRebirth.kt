package shina.wallwarRebirth

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import shina.wallwarRebirth.bootstrap.LiveTargetWorld
import shina.wallwarRebirth.building.ModSites
import shina.wallwarRebirth.building.SiteBuildingQuery
import shina.wallwarRebirth.command.WallwarCommands
import shina.wallwarRebirth.contract.Ports
import shina.wallwarRebirth.contract.WallwarTeams
import shina.wallwarRebirth.item.ModComponents
import shina.wallwarRebirth.item.ModItems
import shina.wallwarRebirth.match.Match
import shina.wallwarRebirth.match.MatchHooks
import shina.wallwarRebirth.monster.ModMonsters
import shina.wallwarRebirth.monster.SummonPorts
import shina.wallwarRebirth.targeting.TargetWorlds
import shina.wallwarRebirth.item.PolymerLoreListener
import shina.wallwarRebirth.shop.ShopLayerRegistry
import shina.wallwarRebirth.shop.ShopMaterialRegistry
import shina.wallwarRebirth.shop.match.TeamWarehouses
import shina.wallwarRebirth.shop.branch.BranchGroupRegistry

class WallwarRebirth : ModInitializer {
    override fun onInitialize() {
        Ports.match = Match
        Ports.buildings = SiteBuildingQuery
        Ports.summons = SummonPorts
        Ports.teams = WallwarTeams
        TargetWorlds.world = LiveTargetWorld
        ModComponents.init()
        ModItems.init()
        ModSites.init()
        ModMonsters.init()
        PolymerLoreListener.register()
        ShopMaterialRegistry.init()
        TeamWarehouses.init()
        ShopLayerRegistry.init()
        BranchGroupRegistry.init()
        MatchHooks.init()
        PolymerResourcePackUtils.addModAssets(WallwarMod.MOD_ID)
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            WallwarTeams.ensure(server)
            Match.attach(server)
        }
        ServerLifecycleEvents.SERVER_STOPPING.register { server ->
            Match.stop(server)
        }
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(WallwarCommands.root.node)
        }
    }
}
