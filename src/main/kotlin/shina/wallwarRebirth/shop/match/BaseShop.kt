package shina.wallwarRebirth.shop.match
import shina.wallwarRebirth.profession.ProfessionEffects
import shina.wallwarRebirth.match.Match
import shina.wallwarRebirth.rules.MatchRules

import eu.pb4.sgui.api.ClickType
import eu.pb4.sgui.api.elements.GuiElement
import eu.pb4.sgui.api.elements.GuiElementInterface
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import shina.wallwarRebirth.WallwarMod
import shina.wallwarRebirth.building.BuildingMenus
import shina.wallwarRebirth.building.ModuleSlot
import shina.wallwarRebirth.building.SiteCoreBlockEntity
import shina.wallwarRebirth.gui.ItemStackBuilder
import shina.wallwarRebirth.item.ModItems
import shina.wallwarRebirth.shop.ShopSession
import shina.wallwarRebirth.shop.element.BaseShopItem
import shina.wallwarRebirth.shop.element.ShopItemWithIndex
import shina.wallwarRebirth.shop.element.ShopLayer
import shina.wallwarRebirth.shop.element.view.ShopItemViewModel
import shina.wallwarRebirth.shop.gui.ShopGui
import shina.wallwarRebirth.util.hideTooltip

object BaseShop {
    enum class Access { CORE, TERMINAL }

    enum class Page(val title: String, val icon: ItemStack, val tabSlot: Int) {
        WAREHOUSE("团队箱", ItemStack(Items.CHEST), 0),
        BUILDING("建筑", ItemStack(Items.BRICKS), 1),
        WEAPON("武器", ItemStack(Items.IRON_SWORD), 2),
        ARMOR("护甲", ItemStack(Items.IRON_CHESTPLATE), 3),
        BASE("基地总览", ItemStack(Items.BEACON), 6),
    }

    private const val ROWS = 3
    private const val PAGE_SLOT = 53
    private val LOCKED_PAGES = setOf(Page.BUILDING, Page.WEAPON, Page.ARMOR)

    fun open(
        player: ServerPlayer,
        site: SiteCoreBlockEntity,
        page: Page = Page.WAREHOUSE,
        sheet: Int = 0,
        access: Access = Access.CORE,
    ) {
        if (Match.phase != Match.Phase.WATCH && Match.phase != Match.Phase.FIGHT && Match.phase != Match.Phase.SETTLE) {
            player.sendSystemMessage(Component.literal("现在不能打开商店"))
            return
        }
        if (!site.isAlly(player)) {
            player.sendSystemMessage(Component.literal("敌方打不开"))
            return
        }
        val currentPage = if (locked(site, page, access)) Page.WAREHOUSE else page
        val goods = listings(currentPage)
        val sheets = when (currentPage) {
            Page.WAREHOUSE -> 2
            Page.BASE -> 1
            else -> (goods.maxOfOrNull { it.row / ROWS } ?: 0) + 1
        }
        val current = sheet.coerceIn(0, (sheets - 1).coerceAtLeast(0))
        val items = ArrayList<ShopItemWithIndex>()
        for (tab in Page.entries) {
            items.add(Tab(tab.tabSlot, tab, currentPage, site, access))
        }
        for (good in goods) {
            if (good.row / ROWS != current) continue
            items.add(Offer(slotOf(good.row % ROWS, good.column), good.item))
        }
        if (sheets > 1) items.add(PageTurn(PAGE_SLOT, site, currentPage, current, sheets, access))
        val reserved = items.map { it.index() }.toSet()
        PageGui(
            ShopSession(useTeamWarehouse = true),
            player,
            listOf(ShopLayer(6, items)),
            currentPage.title,
            site,
            currentPage,
            current,
            reserved,
            access,
        ).open()
    }

    fun hasFlux(site: SiteCoreBlockEntity): Boolean =
        !site.moduleStack(ModuleSlot.DIFFUSION).isEmpty

    fun locked(site: SiteCoreBlockEntity, page: Page, access: Access): Boolean =
        access == Access.TERMINAL && !hasFlux(site) && page in LOCKED_PAGES

    private fun slotOf(row: Int, column: Int): Int = (row + 2) * 9 + column

    private fun listings(page: Page): List<Listing> = when (page) {
        Page.WEAPON -> listOf(
            tool(0, 0, Items.IRON_SWORD, "iron", MatchRules.SWORD_MATERIAL),
            tool(0, 1, Items.IRON_AXE, "iron", MatchRules.AXE_MATERIAL),
            tool(0, 2, Items.IRON_PICKAXE, "iron", MatchRules.PICKAXE_MATERIAL),
            tool(0, 3, Items.IRON_SHOVEL, "iron", MatchRules.SHOVEL_MATERIAL),
            tool(0, 4, Items.IRON_SPEAR, "iron", MatchRules.SPEAR_MATERIAL),
            tool(0, 5, Items.BOW, "iron", MatchRules.BOW_IRON),
            tool(0, 6, Items.ARROW, "iron", MatchRules.ARROW_IRON, MatchRules.ARROW_COUNT),
            tool(1, 0, Items.DIAMOND_SWORD, "diamond", MatchRules.SWORD_MATERIAL),
            tool(1, 1, Items.DIAMOND_AXE, "diamond", MatchRules.AXE_MATERIAL),
            tool(1, 2, Items.DIAMOND_PICKAXE, "diamond", MatchRules.PICKAXE_MATERIAL),
            tool(1, 3, Items.DIAMOND_SHOVEL, "diamond", MatchRules.SHOVEL_MATERIAL),
            tool(1, 4, Items.DIAMOND_SPEAR, "diamond", MatchRules.SPEAR_MATERIAL),
            tool(1, 5, Items.CROSSBOW, "iron", MatchRules.CROSSBOW_IRON),
            tool(1, 6, Items.COOKED_BEEF, "iron", MatchRules.BEEF_IRON, MatchRules.BEEF_COUNT),
            tool(2, 6, Items.OAK_PLANKS, "iron", MatchRules.PLANK_IRON, MatchRules.PLANK_COUNT),
        )
        Page.ARMOR -> listOf(
            tool(0, 0, Items.COPPER_HELMET, "copper", MatchRules.HELMET_MATERIAL),
            tool(0, 1, Items.COPPER_CHESTPLATE, "copper", MatchRules.CHESTPLATE_MATERIAL),
            tool(0, 2, Items.COPPER_LEGGINGS, "copper", MatchRules.LEGGINGS_MATERIAL),
            tool(0, 3, Items.COPPER_BOOTS, "copper", MatchRules.BOOTS_MATERIAL),
            tool(0, 4, Items.SHIELD, "diamond", MatchRules.SHIELD_DIAMOND),
            tool(1, 0, Items.IRON_HELMET, "iron", MatchRules.HELMET_MATERIAL),
            tool(1, 1, Items.IRON_CHESTPLATE, "iron", MatchRules.CHESTPLATE_MATERIAL),
            tool(1, 2, Items.IRON_LEGGINGS, "iron", MatchRules.LEGGINGS_MATERIAL),
            tool(1, 3, Items.IRON_BOOTS, "iron", MatchRules.BOOTS_MATERIAL),
            tool(2, 0, Items.DIAMOND_HELMET, "diamond", MatchRules.HELMET_MATERIAL),
            tool(2, 1, Items.DIAMOND_CHESTPLATE, "diamond", MatchRules.CHESTPLATE_MATERIAL),
            tool(2, 2, Items.DIAMOND_LEGGINGS, "diamond", MatchRules.LEGGINGS_MATERIAL),
            tool(2, 3, Items.DIAMOND_BOOTS, "diamond", MatchRules.BOOTS_MATERIAL),
        )
        Page.BASE, Page.WAREHOUSE -> emptyList()
        Page.BUILDING -> listOf(
            listing(
                0,
                1,
                ItemStack(ModItems.CORE_RELAY),
                mapOf(
                    WallwarMod.idOf("iron") to MatchRules.RELAY_PRICE_IRON,
                    WallwarMod.idOf("copper") to MatchRules.RELAY_PRICE_COPPER,
                ),
            ),
            listing(
                0,
                2,
                ItemStack(ModItems.CORE_FARMLAND),
                mapOf(
                    WallwarMod.idOf("iron") to MatchRules.FARMLAND_PRICE_IRON,
                    WallwarMod.idOf("copper") to MatchRules.FARMLAND_PRICE_COPPER,
                ),
            ),
            listing(1, 1, ItemStack(ModItems.CORE_BARRACKS), iron(MatchRules.BARRACKS_PRICE_IRON)),
            listing(1, 2, ItemStack(ModItems.CORE_PILLAGER), iron(MatchRules.PILLAGER_TOWER_PRICE_IRON)),
            listing(2, 1, ItemStack(ModItems.CORE_WALL), copper(MatchRules.WALL_PRICE_COPPER)),
            listing(2, 2, ItemStack(ModItems.CORE_TOWER), copper(MatchRules.TOWER_PRICE_COPPER)),
            listing(2, 3, ItemStack(ModItems.CORE_TESLA), copper(MatchRules.TESLA_PRICE_COPPER)),
            listing(
                2,
                4,
                ItemStack(ModItems.CORE_CANNON),
                mapOf(
                    WallwarMod.idOf("iron") to MatchRules.CANNON_PRICE_IRON,
                    WallwarMod.idOf("copper") to MatchRules.CANNON_PRICE_COPPER,
                ),
            ),
            listing(3, 0, ItemStack(ModItems.REINFORCE), lapis(MatchRules.MODULE_LAPIS)),
            listing(3, 1, ItemStack(ModItems.REINFORCE_TWO), lapis(MatchRules.MODULE_TWO_LAPIS)),
            listing(3, 2, ItemStack(ModItems.DIFFUSION), lapis(MatchRules.MODULE_DIFFUSION_LAPIS)),
        )
    }

    private fun tool(row: Int, column: Int, item: Item, material: String, cost: Int, count: Int = 1) =
        listing(row, column, ItemStack(item, count), mapOf(WallwarMod.idOf(material) to cost))

    private fun iron(count: Int) = mapOf(WallwarMod.idOf("iron") to count)

    private fun copper(count: Int) = mapOf(WallwarMod.idOf("copper") to count)

    private fun lapis(count: Int) = mapOf(WallwarMod.idOf("lapis") to count)

    private fun listing(row: Int, column: Int, stack: ItemStack, costs: Map<Identifier, Int>): Listing {
        val id = BuiltInRegistries.ITEM.getKey(stack.item)
        val item = BaseShopItem(
            stack,
            costs,
            listOf("give @s $id ${stack.count}"),
            true,
            0,
            false,
            Component.literal("商店只在战斗阶段可购买"),
        )
        return Listing(row, column, item)
    }
}

private data class Listing(val row: Int, val column: Int, val item: BaseShopItem)

private class PageGui(
    session: ShopSession,
    player: ServerPlayer,
    layers: Collection<ShopLayer>,
    heading: String,
    private val site: SiteCoreBlockEntity,
    private val page: BaseShop.Page,
    sheet: Int,
    reserved: Set<Int>,
    private val access: BaseShop.Access,
) : ShopGui(session, player, layers, 6) {
    init {
        title = Component.literal(heading)
        val border = ItemStack(Items.GRAY_STAINED_GLASS_PANE).hideTooltip()
        for (slot in 0 until size) {
            if (slot !in reserved) setSlot(slot, GuiElement(border, GuiElementInterface.EMPTY_CALLBACK))
        }
        val barrier = ItemStack(Items.BARRIER).hideTooltip()
        for (slot in listOf(4, 5)) setSlot(slot, GuiElement(barrier, GuiElementInterface.EMPTY_CALLBACK))
        if (page != BaseShop.Page.WAREHOUSE) {
            val marker = ItemStack(Items.LIME_STAINED_GLASS_PANE).hideTooltip()
            setSlot(9 + page.tabSlot, GuiElement(marker, GuiElementInterface.EMPTY_CALLBACK))
        }
        if (page == BaseShop.Page.WAREHOUSE) fillWarehouse(sheet)
        if (page == BaseShop.Page.BASE) BuildingMenus.paintBaseShop(this, site)
        if (page == BaseShop.Page.BUILDING && sheet == 0) paintBuildingLabels()
    }

    override fun canSpectatorsClick(): Boolean = true

    override fun onTick() {
        super.onTick()
        session.valid = stillOpen()
        if (page == BaseShop.Page.BASE && stillOpen()) BuildingMenus.paintBaseShop(this, site)
    }

    private fun fillWarehouse(sheet: Int) {
        val box = TeamWarehouses.of(site.team) ?: return
        val width = 36
        val start = sheet * width
        for (offset in 0 until width) {
            val index = start + offset
            if (index >= box.containerSize) break
            setSlotRedirect(9 + offset, Slot(box, index, 0, 0))
        }
    }

    private fun paintBuildingLabels() {
        setSlot(18, GuiElement(sectionLabel("资源类"), GuiElementInterface.EMPTY_CALLBACK))
        setSlot(27, GuiElement(sectionLabel("生产类"), GuiElementInterface.EMPTY_CALLBACK))
        setSlot(36, GuiElement(sectionLabel("防御类"), GuiElementInterface.EMPTY_CALLBACK))
    }

    private fun sectionLabel(title: String): ItemStack =
        ItemStackBuilder.of(Items.NAME_TAG)
            .setName(Component.literal(title).withStyle { style ->
                style.withColor(ChatFormatting.YELLOW).withItalic(false)
            })
            .build()

    fun stillOpen(): Boolean {
        val world = site.level
        if (world == null || world.getBlockEntity(site.blockPos) !== site) return false
        if (Match.phase != Match.Phase.WATCH && Match.phase != Match.Phase.FIGHT && Match.phase != Match.Phase.SETTLE) {
            return false
        }
        return site.isAlly(player)
    }
}

private class Tab(
    private val slot: Int,
    private val page: BaseShop.Page,
    private val current: BaseShop.Page,
    private val site: SiteCoreBlockEntity,
    private val access: BaseShop.Access,
) : ShopItemWithIndex {
    override fun index(): Int = slot

    override fun getDefaultIcon(): ItemStack = icon()

    override fun getIcon(gui: ShopGui, viewModel: ShopItemViewModel): ItemStack = icon()

    override fun getBaseItem(gui: ShopGui, clickType: ClickType, viewModel: ShopItemViewModel): BaseShopItem =
        BaseShopItem(icon(), emptyMap(), emptyList(), false, 0, true, null)

    override fun executeBuying(item: BaseShopItem, gui: ShopGui, clickType: ClickType, materialSuffix: Component?) {
        if (page == current) return
        if (BaseShop.locked(site, page, access)) return
        val player = gui.player
        gui.close()
        player.level().server.execute { BaseShop.open(player, site, page, 0, access) }
    }

    private fun icon(): ItemStack {
        if (BaseShop.locked(site, page, access)) {
            return ItemStackBuilder.of(Items.BARRIER)
                .setName(Component.literal(page.title).withStyle { style ->
                    style.withColor(ChatFormatting.RED).withItalic(false)
                })
                .appendLore(Component.literal("需要本队基地装上扩散模块").withStyle { style ->
                    style.withColor(ChatFormatting.GRAY).withItalic(false)
                })
                .build()
        }
        return ItemStackBuilder.of(page.icon.copy())
            .setName(Component.literal(page.title).withStyle { style ->
                style.withColor(ChatFormatting.YELLOW).withItalic(false)
            })
            .build()
    }
}

private class PageTurn(
    private val slot: Int,
    private val site: SiteCoreBlockEntity,
    private val page: BaseShop.Page,
    private val sheet: Int,
    private val sheets: Int,
    private val access: BaseShop.Access,
) : ShopItemWithIndex {
    override fun index(): Int = slot

    override fun getDefaultIcon(): ItemStack = icon()

    override fun getIcon(gui: ShopGui, viewModel: ShopItemViewModel): ItemStack = icon()

    override fun getBaseItem(gui: ShopGui, clickType: ClickType, viewModel: ShopItemViewModel): BaseShopItem =
        BaseShopItem(icon(), emptyMap(), emptyList(), false, 0, true, null)

    override fun executeBuying(item: BaseShopItem, gui: ShopGui, clickType: ClickType, materialSuffix: Component?) {
        val player = gui.player
        gui.close()
        player.level().server.execute { BaseShop.open(player, site, page, (sheet + 1) % sheets, access) }
    }

    private fun icon(): ItemStack =
        ItemStackBuilder.of(Items.ARROW)
            .setName(Component.literal("翻页").withStyle { style -> style.withColor(ChatFormatting.WHITE).withItalic(false) })
            .build()
}

private class Offer(
    private val slot: Int,
    private val offer: BaseShopItem,
) : ShopItemWithIndex {
    override fun index(): Int = slot

    override fun getDefaultIcon(): ItemStack = shown(null, locked = false)

    override fun getIcon(gui: ShopGui, viewModel: ShopItemViewModel): ItemStack = shown(gui, isLocked(gui, offer))

    override fun getBaseItem(gui: ShopGui, clickType: ClickType, viewModel: ShopItemViewModel): BaseShopItem = offer

    override fun isLocked(gui: ShopGui, item: BaseShopItem?): Boolean = !Match.isFight()

    override fun updateView(gui: ShopGui, viewModel: ShopItemViewModel) {
        val page = gui as? PageGui
        gui.session.valid = page?.stillOpen() == true
    }

    override fun executeBuying(item: BaseShopItem, gui: ShopGui, clickType: ClickType, materialSuffix: Component?) {
        val player = gui.player
        if (ProfessionEffects.givePurchased(player, item)) {
            if (!item.hideBuyText) player.sendSystemMessage(ProfessionEffects.boughtMessage(item, materialSuffix))
        } else {
            super.executeBuying(item, gui, clickType, materialSuffix)
        }
        ProfessionEffects.refundIfBastionWall(player, item)
    }

    private fun shown(gui: ShopGui?, locked: Boolean): ItemStack {
        val built = if (gui == null) {
            ItemStackBuilder.of(offer.icon).build()
        } else {
            ItemStackBuilder.of(offer.icon).addCostInfo(gui.player, offer, locked, useTeamWarehouse = true).build()
        }
        built.remove(DataComponents.CUSTOM_MODEL_DATA)
        return built
    }
}
