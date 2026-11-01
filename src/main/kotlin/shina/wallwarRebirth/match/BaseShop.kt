package shina.wallwarRebirth.match

import eu.pb4.sgui.api.ClickType
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import shina.wallwarRebirth.WallwarMod
import shina.wallwarRebirth.building.SiteCoreBlockEntity
import shina.wallwarRebirth.gui.ItemStackBuilder
import shina.wallwarRebirth.item.ModItems
import shina.wallwarRebirth.shop.ShopSession
import shina.wallwarRebirth.shop.element.BaseShopItem
import shina.wallwarRebirth.shop.element.ShopItemWithIndex
import shina.wallwarRebirth.shop.element.ShopLayer
import shina.wallwarRebirth.shop.element.view.ShopItemViewModel
import shina.wallwarRebirth.shop.gui.ShopGui

object BaseShop {
    enum class Page(val title: String) {
        BUILDING("建筑核心和强化"),
        EQUIPMENT("装备"),
    }

    fun open(player: ServerPlayer, site: SiteCoreBlockEntity, page: Page = Page.BUILDING) {
        if (!Match.isFight()) {
            player.sendSystemMessage(Component.literal("商店只在战斗阶段可购买"))
            return
        }
        if (site.team.isNotEmpty() && (player.team?.name ?: "") != site.team) {
            player.sendSystemMessage(Component.literal("敌方打不开"))
            return
        }
        val goods = when (page) {
            Page.BUILDING -> building()
            Page.EQUIPMENT -> equipment()
        }
        val items = ArrayList<ShopItemWithIndex>()
        items.add(Tab(1, Page.BUILDING, page, site))
        items.add(Tab(3, Page.EQUIPMENT, page, site))
        items.addAll(goods)
        PageGui(ShopSession(), player, listOf(ShopLayer(5, items)), page.title, site).open()
    }

    private fun equipment(): List<ShopItemWithIndex> = listOf(
        priced(10, Items.OAK_LOG, 1, iron(MatchRules.LOG_IRON), "minecraft:oak_log 1"),
        priced(15, Items.BOW, 1, iron(MatchRules.BOW_IRON), "minecraft:bow 1"),
        priced(16, Items.ARROW, MatchRules.ARROW_COUNT, iron(MatchRules.ARROW_IRON), "minecraft:arrow ${MatchRules.ARROW_COUNT}"),
        priced(19, Items.COOKED_BEEF, MatchRules.BEEF_COUNT, iron(MatchRules.BEEF_IRON), "minecraft:cooked_beef ${MatchRules.BEEF_COUNT}"),
    )

    private fun building(): List<ShopItemWithIndex> = listOf(
        give(
            10,
            ModItems.CORE_REACTOR,
            mapOf(
                WallwarMod.idOf("iron") to MatchRules.REACTOR_PRICE_IRON,
                WallwarMod.idOf("copper") to MatchRules.REACTOR_PRICE_COPPER,
            ),
        ),
        give(12, ModItems.CORE_WALL, copper(MatchRules.WALL_PRICE_COPPER)),
        give(14, ModItems.CORE_TOWER, copper(MatchRules.TOWER_PRICE_COPPER)),
        give(16, ModItems.CORE_BARRACKS, iron(MatchRules.BARRACKS_PRICE_IRON)),
        give(19, ModItems.REINFORCE, lapis(MatchRules.MODULE_LAPIS)),
        give(21, ModItems.REINFORCE_TWO, lapis(MatchRules.MODULE_TWO_LAPIS)),
    )

    private fun iron(count: Int) = mapOf(WallwarMod.idOf("iron") to count)

    private fun copper(count: Int) = mapOf(WallwarMod.idOf("copper") to count)

    private fun lapis(count: Int) = mapOf(WallwarMod.idOf("lapis") to count)

    private fun priced(slot: Int, item: Item, count: Int, costs: Map<Identifier, Int>, give: String): Offer {
        val icon = ItemStack(item, count)
        val command = "give @s $give"
        return Offer(slot, BaseShopItem(icon, costs, listOf(command), true, 0, false, Component.literal("商店只在战斗阶段可购买")))
    }

    private fun give(slot: Int, item: Item, costs: Map<Identifier, Int>): Offer {
        val id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item)
        return Offer(slot, BaseShopItem(ItemStack(item), costs, listOf("give @s $id 1"), true, 0, false, Component.literal("商店只在战斗阶段可购买")))
    }
}

private class PageGui(
    session: ShopSession,
    player: ServerPlayer,
    layers: Collection<ShopLayer>,
    heading: String,
    private val site: SiteCoreBlockEntity,
) : ShopGui(session, player, layers, 5) {
    init {
        title = Component.literal(heading)
    }

    fun stillOpen(): Boolean {
        val world = site.level
        return Match.isFight() && world != null && world.getBlockEntity(site.blockPos) === site
    }
}

private class Tab(
    private val slot: Int,
    private val page: BaseShop.Page,
    private val current: BaseShop.Page,
    private val site: SiteCoreBlockEntity,
) : ShopItemWithIndex {
    override fun index(): Int = slot

    override fun getDefaultIcon(): ItemStack = icon()

    override fun getIcon(gui: ShopGui, viewModel: ShopItemViewModel): ItemStack = icon()

    override fun getBaseItem(gui: ShopGui, clickType: ClickType, viewModel: ShopItemViewModel): BaseShopItem =
        BaseShopItem(icon(), emptyMap(), emptyList(), false, 0, true, null)

    override fun executeBuying(item: BaseShopItem, gui: ShopGui, clickType: ClickType, materialSuffix: Component?) {
        if (page == current) return
        val player = gui.player
        gui.close()
        player.level().server.execute { BaseShop.open(player, site, page) }
    }

    private fun icon(): ItemStack {
        val item = when (page) {
            BaseShop.Page.BUILDING -> Items.BEACON
            BaseShop.Page.EQUIPMENT -> Items.BOW
        }
        val stack = ItemStackBuilder.of(item)
            .setName(Component.literal(page.title).withStyle { style -> style.withColor(net.minecraft.ChatFormatting.YELLOW).withItalic(false) })
            .build()
        if (page == current) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
        return stack
    }
}

private class Offer(
    private val slot: Int,
    private val offer: BaseShopItem,
) : ShopItemWithIndex {
    override fun index(): Int = slot

    override fun getDefaultIcon(): ItemStack = offer.icon.copy()

    override fun getIcon(gui: ShopGui, viewModel: ShopItemViewModel): ItemStack =
        ItemStackBuilder.of(offer.icon).addCostInfo(gui.player, offer, isLocked(gui, offer)).build()

    override fun getBaseItem(gui: ShopGui, clickType: ClickType, viewModel: ShopItemViewModel): BaseShopItem = offer

    override fun isLocked(gui: ShopGui, item: BaseShopItem?): Boolean = !Match.isFight()

    override fun updateView(gui: ShopGui, viewModel: ShopItemViewModel) {
        val page = gui as? PageGui
        gui.session.valid = page?.stillOpen() == true
    }
}
