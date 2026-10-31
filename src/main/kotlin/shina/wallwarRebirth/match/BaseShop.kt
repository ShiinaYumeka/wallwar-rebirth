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
import shina.wallwarRebirth.monster.MonsterDefinition
import shina.wallwarRebirth.monster.Monsters
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
        EQUIPMENT("装备"),
        CORE("建筑核心"),
        SUMMON("唤灵"),
        MODULE("强化"),
    }

    fun open(player: ServerPlayer, site: SiteCoreBlockEntity, page: Page = Page.EQUIPMENT) {
        if (!Match.isFight()) {
            player.sendSystemMessage(Component.literal("商店只在战斗阶段可购买"))
            return
        }
        if (site.team.isNotEmpty() && (player.team?.name ?: "") != site.team) {
            player.sendSystemMessage(Component.literal("敌方打不开"))
            return
        }
        val goods = when (page) {
            Page.EQUIPMENT -> equipment()
            Page.CORE -> cores()
            Page.SUMMON -> summons()
            Page.MODULE -> modules()
        }
        val items = ArrayList<ShopItemWithIndex>()
        items.add(Tab(1, Page.EQUIPMENT, page, site))
        items.add(Tab(3, Page.CORE, page, site))
        items.add(Tab(5, Page.SUMMON, page, site))
        items.add(Tab(7, Page.MODULE, page, site))
        items.addAll(goods)
        PageGui(ShopSession(), player, listOf(ShopLayer(5, items)), page.title, site).open()
    }

    private fun equipment(): List<ShopItemWithIndex> = listOf(
        priced(10, Items.IRON_SWORD, 1, iron(MatchRules.SWORD_IRON), "minecraft:iron_sword 1"),
        priced(11, Items.IRON_HELMET, 1, iron(MatchRules.HELMET_IRON), "minecraft:iron_helmet 1"),
        priced(12, Items.IRON_CHESTPLATE, 1, iron(MatchRules.CHEST_IRON), "minecraft:iron_chestplate 1"),
        priced(13, Items.IRON_LEGGINGS, 1, iron(MatchRules.LEGS_IRON), "minecraft:iron_leggings 1"),
        priced(14, Items.IRON_BOOTS, 1, iron(MatchRules.BOOTS_IRON), "minecraft:iron_boots 1"),
        priced(15, Items.BOW, 1, iron(MatchRules.BOW_IRON), "minecraft:bow 1"),
        priced(16, Items.ARROW, MatchRules.ARROW_COUNT, iron(MatchRules.ARROW_IRON), "minecraft:arrow ${MatchRules.ARROW_COUNT}"),
        priced(19, Items.COOKED_BEEF, MatchRules.BEEF_COUNT, iron(MatchRules.BEEF_IRON), "minecraft:cooked_beef ${MatchRules.BEEF_COUNT}"),
        priced(20, Items.DIAMOND_SWORD, 1, diamond(MatchRules.DIAMOND_SWORD_COST), "minecraft:diamond_sword 1"),
    )

    private fun cores(): List<ShopItemWithIndex> = listOf(
        give(10, ModItems.CORE_REACTOR, iron(MatchRules.REACTOR_PRICE_IRON)),
        give(12, ModItems.CORE_WALL, iron(MatchRules.WALL_PRICE_IRON)),
        give(14, ModItems.CORE_TOWER, iron(MatchRules.TOWER_PRICE_IRON)),
        give(16, ModItems.CORE_BARRACKS, iron(MatchRules.BARRACKS_PRICE_IRON)),
    )

    private fun summons(): List<ShopItemWithIndex> = listOf(
        give(10, ModItems.EGG_ZOMBIE, costOf(Monsters.ZOMBIE)),
        give(12, ModItems.EGG_SKELETON, costOf(Monsters.SKELETON)),
        give(14, ModItems.EGG_GOLEM, costOf(Monsters.GOLEM)),
    )

    private fun modules(): List<ShopItemWithIndex> = listOf(
        give(10, ModItems.REINFORCE, lapis(MatchRules.MODULE_LAPIS)),
        give(12, ModItems.REINFORCE_TWO, lapis(MatchRules.MODULE_TWO_LAPIS)),
    )

    private fun costOf(monster: MonsterDefinition): Map<Identifier, Int> {
        val costs = HashMap<Identifier, Int>()
        for (cost in monster.cost) {
            val id = when (cost.item) {
                Items.IRON_INGOT -> WallwarMod.idOf("iron")
                Items.DIAMOND -> WallwarMod.idOf("diamond")
                Items.REDSTONE_BLOCK -> WallwarMod.idOf("redstone_block")
                Items.LAPIS_LAZULI -> WallwarMod.idOf("lapis")
                else -> continue
            }
            costs[id] = cost.count
        }
        return costs
    }

    private fun iron(count: Int) = mapOf(WallwarMod.idOf("iron") to count)

    private fun diamond(count: Int) = mapOf(WallwarMod.idOf("diamond") to count)

    private fun redstone(count: Int) = mapOf(WallwarMod.idOf("redstone_block") to count)

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
    private val heading: String,
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
            BaseShop.Page.EQUIPMENT -> Items.IRON_CHESTPLATE
            BaseShop.Page.CORE -> Items.BEACON
            BaseShop.Page.SUMMON -> Items.ZOMBIE_SPAWN_EGG
            BaseShop.Page.MODULE -> Items.LAPIS_LAZULI
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
