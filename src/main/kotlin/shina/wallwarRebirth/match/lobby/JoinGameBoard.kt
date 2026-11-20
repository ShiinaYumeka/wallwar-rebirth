package shina.wallwarRebirth.match.lobby

import eu.pb4.sgui.api.elements.GuiElement
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.ChatFormatting
import net.minecraft.commands.Commands
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.Items
import shina.wallwarRebirth.contract.WallwarTeams
import shina.wallwarRebirth.match.Match
import shina.wallwarRebirth.profession.Classes
import shina.wallwarRebirth.rules.MatchRules

class JoinGameBoard(private val viewer: ServerPlayer) : SimpleGui(MenuType.GENERIC_9x6, viewer, false) {
    init {
        title = line("大厅终端 · 加入游戏", ChatFormatting.YELLOW)
        paint()
    }

    override fun onTick() {
        super.onTick()
        paint()
    }

    private fun paint() {
        fillGlass(this)
        setSlot(13, GuiElement(status()) { _, _, _, _ -> })
        setSlot(22, GuiElement(classInfo()) { _, click, _, _ ->
            if (!click.isLeft || click.shift) return@GuiElement
            close()
            LobbyTerminal.open(viewer, LobbyTab.PROFESSION)
        })
        setSlot(29, GuiElement(teamButton(WallwarTeams.RED, Items.RED_WOOL, ChatFormatting.RED, "红队")) { _, click, _, _ ->
            if (!click.isLeft || click.shift) return@GuiElement
            Match.toggleTeam(viewer, WallwarTeams.RED)
        })
        setSlot(30, GuiElement(teamButton(WallwarTeams.BLUE, Items.BLUE_WOOL, ChatFormatting.BLUE, "蓝队")) { _, click, _, _ ->
            if (!click.isLeft || click.shift) return@GuiElement
            Match.toggleTeam(viewer, WallwarTeams.BLUE)
        })
        setSlot(32, GuiElement(readyButton()) { _, click, _, _ ->
            if (!click.isLeft || click.shift) return@GuiElement
            Match.toggleReady(viewer)
        })
        setSlot(33, GuiElement(spectateButton()) { _, click, _, _ ->
            if (!click.isLeft || click.shift) return@GuiElement
            Match.leaveAndSpectate(viewer)
        })
        setSlot(40, GuiElement(sizeButton()) { _, click, _, _ ->
            if (click.shift) return@GuiElement
            val delta = when {
                click.isLeft -> -MatchRules.MAP_SIZE_STEP
                click.isRight -> MatchRules.MAP_SIZE_STEP
                else -> return@GuiElement
            }
            Match.adjustMapSize(viewer, delta)
        })
        if (Commands.LEVEL_GAMEMASTERS.check(viewer.permissions())) {
            setSlot(42, GuiElement(named(Items.BARRIER, "重置对局", ChatFormatting.RED, listOf("管理员：清空当前对局并返回大厅"))) { _, click, _, _ ->
                if (!click.isLeft || click.shift) return@GuiElement
                Match.reset(viewer)
            })
        }
        placeBack(this, viewer)
    }

    private fun status(): net.minecraft.world.item.ItemStack {
        val lines = mutableListOf(
            "队伍：${Match.member(viewer.uuid)?.team?.ifEmpty { "无" } ?: "无"}",
            "职业：${Classes.name(Match.member(viewer.uuid)?.classId ?: Classes.MINER)}",
            "准备（${readyText()}）",
        )
        if (Match.testMode) lines.add("测试对局：购买和升级不消耗资源，可换队伍")
        val seconds = Match.readyCountdownSeconds(viewer.level().server)
        if (seconds != null) lines.add("全员已准备，$seconds 秒后开始")
        return named(Items.BOOK, phaseName(), ChatFormatting.YELLOW, lines)
    }

    private fun classInfo(): net.minecraft.world.item.ItemStack {
        val spec = Classes.spec(Match.member(viewer.uuid)?.classId ?: Classes.MINER)
        return named(
            spec.icon,
            "职业：${spec.displayName}",
            ChatFormatting.AQUA,
            listOf("被动：${spec.passive}", "主动：${spec.active}", "点击打开职业板块"),
        )
    }

    private fun teamButton(team: String, item: net.minecraft.world.item.Item, color: ChatFormatting, label: String) =
        named(
            item,
            label,
            color,
            listOf(
                when {
                    Match.member(viewer.uuid)?.team == team -> "当前队伍，再次点击退出"
                    Match.phase != Match.Phase.LOBBY -> "对局已经开始，不能中途加入"
                    else -> "点击加入"
                },
            ),
        )

    private fun readyButton(): net.minecraft.world.item.ItemStack {
        val member = Match.member(viewer.uuid)
        val ready = member?.ready == true
        val title = (if (ready) "已准备" else "未准备") + "（${readyText()}）"
        val counting = Match.readyCountdownSeconds(viewer.level().server) != null
        return named(
            if (ready) Items.LIME_DYE else Items.GRAY_DYE,
            title,
            if (ready) ChatFormatting.GREEN else ChatFormatting.GRAY,
            listOf(
                if (Match.phase != Match.Phase.LOBBY) phaseName()
                else if (counting && ready) "再次点击取消准备，会打断倒计时"
                else if (counting) "点击准备；全员再次准备后会从五秒重新倒数"
                else if (ready) "再次点击取消准备"
                else if (member == null || member.team.isEmpty()) "先加入队伍再准备"
                else "点击准备；两队在线成员全部确认后会倒数五秒，倒数结束才开始准备地图",
            ),
        )
    }

    private fun spectateButton() = named(
        Items.SPYGLASS,
        "旁观",
        ChatFormatting.AQUA,
        listOf(
            if (Match.worldReady) "点击进入旁观，会退出当前队伍和准备" else "地图尚未生成，点击后将在观察阶段开始时进入场地",
            "在局内面板可以返回大厅",
        ),
    )

    private fun sizeButton() = named(
        Items.MAP,
        "边长 ${Match.mapSize}",
        ChatFormatting.GOLD,
        listOf(
            if (Match.phase == Match.Phase.LOBBY) "左键减小 20 格，右键增大 20 格" else "地图已经生成",
            "范围 ${MatchRules.minimumMapSize()} 到 ${MatchRules.maximumMapSize()}",
        ),
    )

    private fun readyText(): String {
        val counts = Match.readyCounts(viewer.level().server)
        return "${counts.first}/${counts.second}"
    }

    private fun phaseName(): String = when (Match.phase) {
        Match.Phase.LOBBY -> "组队与准备"
        Match.Phase.WATCH -> "观察"
        Match.Phase.FIGHT -> "战斗"
        Match.Phase.SETTLE -> "结算"
    }
}
