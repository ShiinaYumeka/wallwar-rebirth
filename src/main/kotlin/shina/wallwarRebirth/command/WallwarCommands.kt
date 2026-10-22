package shina.wallwarRebirth.command

import com.mojang.brigadier.Command
import eu.pb4.sgui.api.elements.GuiElement
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomModelData
import shina.wallwarRebirth.flux.gui.FluxGui
import shina.wallwarRebirth.gui.GuiTheme
import shina.wallwarRebirth.gui.SimpleHotbarGui
import shina.wallwarRebirth.item.ModItems
import shina.wallwarRebirth.shop.PlayerShopSettings
import shina.wallwarRebirth.shop.ShopLayerRegistry
import shina.wallwarRebirth.shop.ShopLayerTags
import shina.wallwarRebirth.shop.ShopSession
import shina.wallwarRebirth.shop.gui.ShopGui
import shina.wallwarRebirth.util.command.builder.executes
import shina.wallwarRebirth.util.command.builder.literal
import shina.wallwarRebirth.util.command.builder.literalCommand
import shina.wallwarRebirth.util.command.context.CommandContext

object WallwarCommands {
    val root = literalCommand("ww") {
        literal("gui") {
            executes { openFluxGui() }
        }
        literal("hotbar") {
            executes { openHotbar() }
        }
        literal("shop") {
            executes { openShop() }
            literal("teamrepo") {
                executes { toggleTeamRepo() }
            }
        }
        literal("token") {
            executes { giveToken() }
        }
    }

    private fun CommandContext.openFluxGui(): Int {
        val player = context.source.playerOrException
        FluxGui(player, Component.literal("Wallwar"), rows = 3) {
            pattern(
                "GGGGGGGGG",
                "GGBGBBGBG",
                "GGGGGGGGG",
            ) {
                bind('G') { item(GuiTheme.GLASS_BLACK) }
                bind('B') { item(GuiTheme.GLASS_BLUE) }
            }
        }.open()
        return Command.SINGLE_SUCCESS
    }

    private fun CommandContext.openHotbar(): Int {
        val player = context.source.playerOrException
        val gui = object : SimpleHotbarGui(player) {}
        gui.setSlot(0, GuiElement(ItemStack(Items.DIAMOND)) { _, _, _, _ ->
            player.sendSystemMessage(Component.literal("hotbar"))
        })
        gui.open()
        return Command.SINGLE_SUCCESS
    }

    private fun CommandContext.openShop(): Int {
        val player = context.source.playerOrException
        val layers = ShopLayerRegistry.getTag(ShopLayerTags.STANDARD_TAG)
        if (layers.isEmpty()) {
            sendFailure(Component.translatable("text.wallwar.shop.empty"))
            return 0
        }
        ShopGui(ShopSession(supportsFurnaceUpgrade = true), player, layers).open()
        return Command.SINGLE_SUCCESS
    }

    private fun CommandContext.toggleTeamRepo(): Int {
        val player = context.source.playerOrException
        val preference = PlayerShopSettings.of(player)
        preference.shopTeamRepoEnabled = !preference.shopTeamRepoEnabled
        val key = if (preference.shopTeamRepoEnabled) {
            "text.wallwar.shop.team_repo.enabled"
        } else {
            "text.wallwar.shop.team_repo.disabled"
        }
        sendSystemMessage(Component.translatable(key))
        return Command.SINGLE_SUCCESS
    }

    private fun CommandContext.giveToken(): Int {
        val player = context.source.playerOrException
        val stack = ItemStack(ModItems.EXAMPLE_TOKEN)
        val empty = CustomModelData.EMPTY
        stack[DataComponents.CUSTOM_MODEL_DATA] = CustomModelData(
            empty.floats(),
            empty.flags(),
            listOf("upgrade"),
            empty.colors(),
        )
        if (!player.inventory.add(stack)) {
            player.drop(stack, false)
        }
        return Command.SINGLE_SUCCESS
    }
}
