package shina.wallwarRebirth.match

import eu.pb4.sgui.api.elements.GuiElement
import eu.pb4.sgui.api.elements.GuiElementInterface
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import shina.wallwarRebirth.gui.ItemStackBuilder
import shina.wallwarRebirth.monster.TestTeams
import shina.wallwarRebirth.util.hideTooltip

object MatchGui {
    fun open(player: ServerPlayer) {
        Board(player).open()
    }
}

private class Board(private val viewer: ServerPlayer) : SimpleGui(MenuType.GENERIC_9x3, viewer, false) {
    init {
        title = Component.literal("对局")
        paint()
    }

    override fun onTick() {
        super.onTick()
        paint()
    }

    private fun paint() {
        val glass = ItemStack(Items.GRAY_STAINED_GLASS_PANE).hideTooltip()
        for (slot in 0 until size) setSlot(slot, GuiElement(glass, GuiElementInterface.EMPTY_CALLBACK))
        setSlot(13, GuiElement(status(), GuiElementInterface.EMPTY_CALLBACK))
        setSlot(20, GuiElement(teamButton(TestTeams.RED, Items.RED_WOOL, ChatFormatting.RED)) { _, click, _, _ ->
            if (!click.isLeft || click.shift) return@GuiElement
            Match.assign(viewer, TestTeams.RED)
        })
        setSlot(21, GuiElement(teamButton(TestTeams.BLUE, Items.BLUE_WOOL, ChatFormatting.BLUE)) { _, click, _, _ ->
            if (!click.isLeft || click.shift) return@GuiElement
            Match.assign(viewer, TestTeams.BLUE)
        })
        setSlot(22, GuiElement(actionButton()) { _, click, _, _ ->
            if (!click.isLeft || click.shift) return@GuiElement
            when (Match.phase) {
                Match.Phase.LOBBY -> Match.confirmReady(viewer)
                Match.Phase.WATCH -> Match.confirmStart(viewer)
                else -> viewer.sendSystemMessage(Component.literal(phaseName()))
            }
            paint()
        })
        setSlot(23, GuiElement(classButton()) { _, click, _, _ ->
            if (!click.isLeft || click.shift) return@GuiElement
            close()
            ClassBoard(viewer).open()
        })
    }

    private fun status(): ItemStack {
        val server = viewer.level().server
        val readyRed = Match.counts(server, TestTeams.RED, readyFlag = true)
        val readyBlue = Match.counts(server, TestTeams.BLUE, readyFlag = true)
        val startRed = Match.counts(server, TestTeams.RED, readyFlag = false)
        val startBlue = Match.counts(server, TestTeams.BLUE, readyFlag = false)
        val member = Match.member(viewer.uuid)
        return ItemStackBuilder.of(Items.BOOK)
            .setName(line(phaseName(), ChatFormatting.YELLOW))
            .appendLore(line("队伍：${member?.team?.ifEmpty { "无" } ?: "无"}"))
            .appendLore(line("职业：${Classes.name(member?.classId ?: Classes.MINER)}"))
            .appendLore(line("红队准备 ${readyRed.first}/${readyRed.second}，开始 ${startRed.first}/${startRed.second}"))
            .appendLore(line("蓝队准备 ${readyBlue.first}/${readyBlue.second}，开始 ${startBlue.first}/${startBlue.second}"))
            .build()
    }

    private fun teamButton(team: String, item: net.minecraft.world.item.Item, color: ChatFormatting): ItemStack {
        val joined = Match.member(viewer.uuid)?.team == team
        return ItemStackBuilder.of(item)
            .setName(line(team, color))
            .appendLore(line(if (joined) "当前队伍" else "点击加入"))
            .build()
    }

    private fun classButton(): ItemStack {
        val member = Match.member(viewer.uuid)
        val name = Classes.name(member?.classId ?: Classes.MINER)
        return ItemStackBuilder.of(Items.IRON_PICKAXE)
            .setName(line("职业：$name", ChatFormatting.AQUA))
            .appendLore(line("点击选择"))
            .build()
    }

    private fun actionButton(): ItemStack {
        val member = Match.member(viewer.uuid)
        val name = when (Match.phase) {
            Match.Phase.LOBBY -> if (member?.ready == true) "已准备" else "未准备"
            Match.Phase.WATCH -> if (member?.started == true) "已确认开始" else "开始"
            Match.Phase.FIGHT -> "战斗中"
            Match.Phase.SETTLE -> "已结算"
        }
        return ItemStackBuilder.of(Items.LIME_DYE)
            .setName(line(name, ChatFormatting.GREEN))
            .appendLore(line("该队当前在线成员全部确认后，这一队才算完成"))
            .build()
    }

    private fun phaseName(): String = when (Match.phase) {
        Match.Phase.LOBBY -> "组队与准备"
        Match.Phase.WATCH -> "观察"
        Match.Phase.FIGHT -> "战斗"
        Match.Phase.SETTLE -> "结算"
    }
}

private class ClassBoard(private val viewer: ServerPlayer) : SimpleGui(MenuType.GENERIC_9x3, viewer, false) {
    init {
        title = Component.literal("选择职业")
        val glass = ItemStack(Items.GRAY_STAINED_GLASS_PANE).hideTooltip()
        for (slot in 0 until size) setSlot(slot, GuiElement(glass, GuiElementInterface.EMPTY_CALLBACK))
        setSlot(13, GuiElement(miner()) { _, click, _, _ ->
            if (!click.isLeft || click.shift) return@GuiElement
            Match.chooseClass(viewer, Classes.MINER)
            close()
            Board(viewer).open()
        })
        setSlot(22, GuiElement(back()) { _, click, _, _ ->
            if (!click.isLeft || click.shift) return@GuiElement
            close()
            Board(viewer).open()
        })
    }

    private fun miner(): ItemStack {
        val current = Match.member(viewer.uuid)?.classId == Classes.MINER
        return ItemStackBuilder.of(Items.IRON_PICKAXE)
            .setName(line("矿工", ChatFormatting.AQUA))
            .appendLore(line(if (current) "当前职业" else "点击选择"))
            .appendLore(line("被动：挖掘矿石时 ${(MatchRules.MINER_BONUS_CHANCE * 100).toInt()}% 概率多掉一份"))
            .appendLore(line("主动：消耗 ${MatchRules.MINER_LAPIS_BLOCKS} 个青金石块，${MatchRules.MINER_ACTIVE_TICKS / MatchRules.TICKS_PER_SECOND} 秒内挖建筑核心和结构零件伤害为 ${MatchRules.MINER_DIG_DAMAGE}"))
            .appendLore(line("战斗中手持青金石块右键释放，持续期间不能再次释放"))
            .build()
    }

    private fun back(): ItemStack =
        ItemStackBuilder.of(Items.ARROW)
            .setName(line("返回", ChatFormatting.WHITE))
            .build()
}

private fun line(text: String, color: ChatFormatting = ChatFormatting.GRAY): Component =
    Component.literal(text).withStyle { style -> style.withColor(color).withItalic(false) }
