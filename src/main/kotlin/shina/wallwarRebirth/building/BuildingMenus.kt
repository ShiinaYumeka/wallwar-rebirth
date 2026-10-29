package shina.wallwarRebirth.building

import eu.pb4.sgui.api.ClickType
import eu.pb4.sgui.api.elements.GuiElement
import eu.pb4.sgui.api.elements.GuiElementInterface
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
import shina.wallwarRebirth.shop.element.view.ShopItemViewModel
import shina.wallwarRebirth.shop.gui.ShopGui
import shina.wallwarRebirth.util.hideTooltip

object BuildingMenus {
    private val pickerSlots = intArrayOf(1, 3, 5, 7)

    fun open(player: ServerPlayer, site: SiteCoreBlockEntity) {
        val definition = Buildings.byId(site.buildingId)
        if (definition == null) {
            openPicker(player, site, replace = false)
        } else {
            BuildingPanelGui(player, site, definition).open()
        }
    }

    fun openPicker(player: ServerPlayer, site: SiteCoreBlockEntity, replace: Boolean) {
        val layer = ShopLayer(
            1,
            Buildings.ofCategory(BuildingCategory.NORMAL).mapIndexed { index, definition ->
                SelectBuildingItem(pickerSlots.getOrElse(index) { index }, site, definition, replace)
            },
        )
        BuildingShopGui(ShopSession(), player, listOf(layer), "选择建筑").open()
    }
}

class BuildingPanelGui(
    private val viewer: ServerPlayer,
    private val site: SiteCoreBlockEntity,
    private val definition: BuildingDefinition,
) : SimpleGui(
    if (definition.showsContainer) MenuType.GENERIC_9x6 else MenuType.GENERIC_9x2,
    viewer,
    false,
) {
    init {
        title = Component.literal(definition.displayName)
        val glass = ItemStack(Items.GRAY_STAINED_GLASS_PANE).hideTooltip()
        val decorated = if (definition.showsContainer) STORAGE_ORIGIN else size
        for (slot in 0 until decorated) {
            setSlot(slot, GuiElement(glass, GuiElementInterface.EMPTY_CALLBACK))
        }
        if (definition.showsContainer) {
            for (slot in 0 until site.storage.containerSize) {
                setSlotRedirect(STORAGE_ORIGIN + slot, Slot(site.storage, slot, 0, 0))
            }
        }
        paintButtons()
    }

    override fun onTick() {
        super.onTick()
        val world = site.level
        if (world == null || world.getBlockEntity(site.blockPos) !== site) {
            close()
            return
        }
        paintButtons()
    }

    private fun paintButtons() {
        setSlot(UPGRADE_SLOT, GuiElement(upgradeIcon()) { _, clickType, _, _ ->
            if (!clickType.isLeft || clickType.shift) return@GuiElement
            if (site.tryUpgrade(viewer)) paintButtons()
        })
        setSlot(INFO_SLOT, GuiElement(infoIcon(), GuiElementInterface.EMPTY_CALLBACK))
        setSlot(REBUILD_SLOT, GuiElement(rebuildIcon()) { _, clickType, _, _ ->
            if (!clickType.isLeft || clickType.shift) return@GuiElement
            close()
            viewer.level().server.execute { BuildingMenus.openPicker(viewer, site, replace = true) }
        })
        setSlot(DEMOLISH_SLOT, GuiElement(demolishIcon()) { _, clickType, _, _ ->
            if (!clickType.isLeft || clickType.shift) return@GuiElement
            if (site.demolish(viewer)) close()
        })
    }

    private fun infoIcon(): ItemStack {
        val builder = ItemStackBuilder.of(definition.icon)
            .setName(line("${definition.displayName}（${levelMark(site.buildingLevel)}级）", ChatFormatting.YELLOW))
        builder.appendLore(line("类别：${definition.category.label}"))
        builder.appendLore(line("等级：${site.buildingLevel}"))
        builder.appendLore(line("生命：${site.health}/${definition.maxHealth(site.buildingLevel)}"))
        builder.appendLore(line("队伍：${site.team.ifEmpty { "无" }}"))
        if (definition.summary.isNotEmpty()) builder.appendLore(line(definition.summary))
        for (rule in definition.production) {
            val color = if (site.buildingLevel >= rule.minLevel) ChatFormatting.GREEN else ChatFormatting.GRAY
            builder.appendLore(line(rule.describe(), color))
        }
        for (detail in definition.details) {
            val color = if (site.buildingLevel >= detail.minLevel) ChatFormatting.GREEN else ChatFormatting.GRAY
            builder.appendLore(line(detail.text, color))
        }
        return builder.build()
    }

    private fun upgradeIcon(): ItemStack {
        val builder = ItemStackBuilder.of(Items.EXPERIENCE_BOTTLE)
            .setName(line(if (site.buildingLevel >= definition.maxLevel) "已满级" else "升级", ChatFormatting.YELLOW))
        for (upgrade in definition.upgrades) {
            val color = if (upgrade.toLevel == site.buildingLevel + 1) ChatFormatting.YELLOW else ChatFormatting.GRAY
            builder.appendLore(line(upgrade.describe(), color))
        }
        return builder.build()
    }

    private fun rebuildIcon(): ItemStack =
        ItemStackBuilder.of(Items.BARRIER)
            .setName(line("重建", ChatFormatting.YELLOW))
            .appendLore(line("重新选择建筑"))
            .build()

    private fun demolishIcon(): ItemStack =
        ItemStackBuilder.of(Items.TNT)
            .setName(line("拆除", ChatFormatting.RED))
            .appendLore(line("不返还购买或升级花掉的材料，容器物品掉在地上"))
            .build()

    private fun line(text: String, color: ChatFormatting = ChatFormatting.GRAY): Component =
        Component.literal(text).withStyle { style -> style.withColor(color).withItalic(false) }

    companion object {
        const val UPGRADE_SLOT = 3
        const val INFO_SLOT = 4
        const val REBUILD_SLOT = 5
        const val DEMOLISH_SLOT = 6
        const val STORAGE_ORIGIN = 18
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
    private val replace: Boolean,
) : ShopItemWithIndex {
    override fun index(): Int = slot

    override fun getDefaultIcon(): ItemStack = icon()

    override fun getIcon(gui: ShopGui, viewModel: ShopItemViewModel): ItemStack = icon()

    override fun getBaseItem(gui: ShopGui, clickType: ClickType, viewModel: ShopItemViewModel): BaseShopItem =
        BaseShopItem(icon(), emptyMap(), emptyList(), false, 0, true, null)

    override fun updateView(gui: ShopGui, viewModel: ShopItemViewModel) {
        val world = site.level
        val present = world != null && world.getBlockEntity(site.blockPos) === site
        gui.session.valid = present && (replace || site.buildingId.isEmpty())
    }

    override fun executeBuying(
        item: BaseShopItem,
        gui: ShopGui,
        clickType: ClickType,
        materialSuffix: Component?,
    ) {
        site.assign(definition, replace, gui.player.team?.name ?: "")
        gui.player.sendSystemMessage(Component.literal("已选定${definition.displayName}"))
        gui.close()
    }

    private fun icon(): ItemStack {
        val builder = ItemStackBuilder.of(definition.icon)
            .setName(Component.literal(definition.displayName).withStyle { style ->
                style.withColor(ChatFormatting.YELLOW).withItalic(false)
            })
        builder.appendLore(Component.literal(definition.category.label).withStyle { style ->
            style.withColor(ChatFormatting.GRAY).withItalic(false)
        })
        return builder.build()
    }
}
