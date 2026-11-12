package shina.wallwarRebirth.match.lobby

import eu.pb4.sgui.api.elements.GuiElement
import eu.pb4.sgui.api.elements.GuiElementInterface
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import shina.wallwarRebirth.gui.ItemStackBuilder
import shina.wallwarRebirth.rules.MatchRules
import shina.wallwarRebirth.shop.match.PortableShop
import shina.wallwarRebirth.util.hideTooltip

enum class LobbyTab(val title: String, val icon: Item) {
    UPDATES("更新公告", Items.KNOWLEDGE_BOOK),
    JOIN("加入游戏", Items.COMPASS),
    PROFESSION("职业", Items.IRON_SWORD),
    MONSTERS("怪物", Items.ZOMBIE_SPAWN_EGG),
    BUILDINGS("建筑", Items.BRICKS),
}

object LobbyTerminal {
    fun isLobby(player: ServerPlayer): Boolean =
        player.level().dimension() != MatchRules.DIMENSION

    fun open(player: ServerPlayer, tab: LobbyTab? = null) {
        if (!isLobby(player)) {
            PortableShop.open(player)
            return
        }
        when (tab) {
            null -> LobbyHomeBoard(player).open()
            LobbyTab.UPDATES -> UpdateNoticeBoard(player, UpdateNotices.latestIndex(), 0).open()
            LobbyTab.JOIN -> JoinGameBoard(player).open()
            LobbyTab.PROFESSION -> ProfessionBoard(player).open()
            LobbyTab.MONSTERS -> MonsterHomeBoard(player).open()
            LobbyTab.BUILDINGS -> BuildingHomeBoard(player).open()
        }
    }
}

class LobbyHomeBoard(private val viewer: ServerPlayer) : SimpleGui(MenuType.GENERIC_9x6, viewer, false) {
    init {
        title = line("大厅终端", ChatFormatting.YELLOW)
        fillGlass(this)
        setSlot(8, button(LobbyTab.UPDATES, "查看本版本与历次更新"))
        setSlot(22, button(LobbyTab.JOIN, "分队、准备和旁观", glow = true))
        setSlot(38, button(LobbyTab.MONSTERS, "查看可购买的唤灵"))
        setSlot(40, button(LobbyTab.PROFESSION, "查看并选择职业"))
        setSlot(42, button(LobbyTab.BUILDINGS, "查看建筑说明"))
    }

    private fun button(tab: LobbyTab, hint: String, glow: Boolean = false) = GuiElement(
        named(tab.icon, tab.title, ChatFormatting.YELLOW, listOf(hint), glow = glow),
    ) { _, click, _, _ ->
        if (!click.isLeft || click.shift) return@GuiElement
        close()
        LobbyTerminal.open(viewer, tab)
    }
}

internal fun line(text: String, color: ChatFormatting = ChatFormatting.GRAY): Component =
    Component.literal(text).withStyle { style -> style.withColor(color).withItalic(false) }

internal fun fillGlass(gui: SimpleGui) {
    val glass = ItemStack(Items.GRAY_STAINED_GLASS_PANE).hideTooltip()
    for (slot in 0 until gui.size) {
        gui.setSlot(slot, GuiElement(glass, GuiElementInterface.EMPTY_CALLBACK))
    }
}

internal fun named(
    item: Item,
    name: String,
    color: ChatFormatting,
    lore: List<String> = emptyList(),
    glow: Boolean = false,
    stack: ItemStack = ItemStack(item),
): ItemStack {
    val builder = ItemStackBuilder.of(stack).setName(line(name, color))
    for (row in lore) builder.appendLore(line(row))
    val built = builder.build()
    if (glow) built[DataComponents.ENCHANTMENT_GLINT_OVERRIDE] = true
    return built
}

internal fun placeBack(gui: SimpleGui, player: ServerPlayer, slot: Int = 45) {
    gui.setSlot(
        slot,
        GuiElement(named(Items.OAK_DOOR, "返回", ChatFormatting.WHITE, listOf("返回大厅终端"))) { _, click, _, _ ->
            if (!click.isLeft || click.shift) return@GuiElement
            gui.close()
            LobbyHomeBoard(player).open()
        },
    )
}
