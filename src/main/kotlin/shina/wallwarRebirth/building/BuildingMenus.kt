package shina.wallwarRebirth.building

import eu.pb4.sgui.api.ClickType
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import shina.wallwarRebirth.gui.ItemStackBuilder
import shina.wallwarRebirth.shop.ShopSession
import shina.wallwarRebirth.shop.element.BaseShopItem
import shina.wallwarRebirth.shop.element.ShopItemWithIndex
import shina.wallwarRebirth.shop.element.ShopLayer
import shina.wallwarRebirth.shop.element.type.BuildingPanelItem
import shina.wallwarRebirth.shop.element.view.ShopItemViewModel
import shina.wallwarRebirth.shop.gui.ShopGui

object BuildingMenus {
    private val pickerSlots = intArrayOf(2, 4, 6, 0, 1, 3, 5, 7, 8)

    fun open(player: ServerPlayer, site: SiteCoreBlockEntity) {
        val definition = Buildings.byId(site.buildingId)
        val layer = if (definition == null) picker(site) else panel(site)
        val title = definition?.displayName ?: "选择核心建筑"
        BuildingShopGui(ShopSession(), player, listOf(layer), title).open()
    }

    private fun picker(site: SiteCoreBlockEntity): ShopLayer {
        val items = Buildings.ofCategory(BuildingCategory.CORE).mapIndexed { index, definition ->
            SelectBuildingItem(pickerSlots.getOrElse(index) { index }, site, definition)
        }
        return ShopLayer(1, items)
    }

    private fun panel(site: SiteCoreBlockEntity): ShopLayer = ShopLayer(
        1,
        listOf(
            BuildingPanelItem(2, BuildingPanelItem.Role.STORAGE, site),
            BuildingPanelItem(4, BuildingPanelItem.Role.INFO, site),
            BuildingPanelItem(6, BuildingPanelItem.Role.UPGRADE, site),
        ),
    )
}

class SiteChestGui(player: ServerPlayer, site: SiteCoreBlockEntity) : SimpleGui(MenuType.GENERIC_9x3, player, false) {
    init {
        title = Component.literal("存储箱")
        for (slot in 0 until site.storage.containerSize) {
            setSlotRedirect(slot, Slot(site.storage, slot, 0, 0))
        }
    }
}

private class BuildingShopGui(
    session: ShopSession,
    player: ServerPlayer,
    layers: Collection<ShopLayer>,
    private val heading: String,
) : ShopGui(session, player, layers, 1) {
    init {
        title = Component.literal(heading)
    }
}

private class SelectBuildingItem(
    private val slot: Int,
    private val site: SiteCoreBlockEntity,
    private val definition: BuildingDefinition,
) : ShopItemWithIndex {
    override fun index(): Int = slot

    override fun getDefaultIcon(): ItemStack = icon()

    override fun getIcon(gui: ShopGui, viewModel: ShopItemViewModel): ItemStack = icon()

    override fun getBaseItem(gui: ShopGui, clickType: ClickType, viewModel: ShopItemViewModel): BaseShopItem =
        BaseShopItem(icon(), emptyMap(), emptyList(), false, 0, true, null)

    override fun updateView(gui: ShopGui, viewModel: ShopItemViewModel) {
        val world = site.level
        gui.session.valid = world != null && world.getBlockEntity(site.blockPos) === site && site.buildingId.isEmpty()
    }

    override fun executeBuying(
        item: BaseShopItem,
        gui: ShopGui,
        clickType: ClickType,
        materialSuffix: Component?,
    ) {
        site.select(definition)
        gui.player.sendSystemMessage(Component.literal("已选定${definition.displayName}"))
        gui.close()
    }

    private fun icon(): ItemStack {
        val builder = ItemStackBuilder.of(Items.BLAST_FURNACE)
            .setName(Component.literal(definition.displayName).withStyle { style ->
                style.withColor(ChatFormatting.YELLOW).withItalic(false)
            })
        builder.appendLore(Component.literal(definition.category.label).withStyle { style ->
            style.withColor(ChatFormatting.GRAY).withItalic(false)
        })
        return builder.build()
    }
}
