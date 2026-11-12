package shina.wallwarRebirth.match.lobby

import eu.pb4.sgui.api.elements.GuiElement
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.ChatFormatting
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items

data class UpdateEntry(val item: Item, val title: String, val lore: List<String> = emptyList())

data class UpdateNotice(val version: String, val entries: List<UpdateEntry>)

object UpdateNotices {
    val all: List<UpdateNotice> = listOf(
        UpdateNotice(
            version = "1.0.0",
            entries = listOf(
                UpdateEntry(
                    Items.COMPASS,
                    "大厅终端",
                    listOf(
                        "大厅指南针改为大厅终端",
                        "主界面只放五个入口：中间加入游戏，右上角更新公告，下方横排怪物、职业、建筑",
                        "加入游戏可再点取消分队或准备，旁观也在该界面",
                    ),
                ),
                UpdateEntry(
                    Items.RECOVERY_COMPASS,
                    "局内终端",
                    listOf(
                        "便携终端和局内菜单合并为一只追溯指针外观的「局内终端」",
                        "进大厅改发大厅终端；进观察、对局或旁观则发局内终端",
                        "组队后右键打开商店，默认团队箱",
                        "未装扩散模块时只能用团队箱和基地总览，右键己方核心或中继仍是完整商店；装上后其余栏解锁",
                        "未组队旁观者该界面尚未开放；死亡不掉落",
                    ),
                ),
                UpdateEntry(
                    Items.CHEST,
                    "商店",
                    listOf(
                        "页签为团队箱、建筑、武器、护甲和基地总览",
                        "团队箱两页共 72 格，两页都可放方块",
                        "建筑栏两页：第一页按资源、生产、防御分类，第二页是三个模块",
                        "基地总览可查看信息、升级、矿物产出和模块槽",
                    ),
                ),
                UpdateEntry(
                    Items.RED_BANNER,
                    "投降",
                    listOf(
                        "仅战斗阶段、本队基地仍在时可投",
                        "本队在线成员（含复活倒计时旁观）全部同意即对方获胜，可再点撤回",
                        "观察和结算不能投；未组队旁观者和离线队友不计入",
                    ),
                ),
                UpdateEntry(
                    Items.OAK_DOOR,
                    "返回大厅",
                    listOf(
                        "未组队旁观者获得一只不能放置的橡木门「返回大厅」，点击后回大厅，对局继续",
                        "有队伍的人不会拿到；死亡不掉落",
                    ),
                ),
                UpdateEntry(
                    Items.MAP,
                    "结算",
                    listOf(
                        "结算后地图保留 5 秒供观看，动作栏显示倒计时，随后自动重置回大厅",
                        "管理员可提前重置",
                    ),
                ),
                UpdateEntry(
                    Items.CLOCK,
                    "矿工",
                    listOf(
                        "被动：挖掘矿石且镐的等级够时，50% 概率把这一次的掉落再掉一份；身上有急迫时必定触发",
                        "主动「黄金时刻」获得急迫 I，持续 30 秒，冷却 60 秒",
                    ),
                ),
                UpdateEntry(
                    Items.EXPERIENCE_BOTTLE,
                    "破坏手",
                    listOf(
                        "被动：自己把敌方建筑生命扣到 0 并摧毁时，在核心位置掉落该建筑购买与升级费用合计的 10%，每种资源向上取整",
                        "主动「娴熟技巧」之后 30 秒内挖掘建筑伤害翻倍，冷却 60 秒",
                    ),
                ),
                UpdateEntry(
                    Items.SLIME_BALL,
                    "堡垒",
                    listOf(
                        "被动：购买或升级城墙并完成扣费后，按该次费用返还 25%，每种资源向上取整",
                        "主动「强力粘合剂」右键己方城墙核心，恢复当前生命上限的 50%，冷却 60 秒",
                    ),
                ),
                UpdateEntry(
                    Items.FEATHER,
                    "驱魔人",
                    listOf(
                        "被动：在商店购买的剑带有亡灵杀手 III，不另收费",
                        "主动「急速冲刺」获得速度 II，持续 5 秒，冷却 30 秒",
                    ),
                ),
                UpdateEntry(
                    Items.STONE_BRICKS,
                    "城墙强化模块",
                    listOf("只对安装该模块的城墙自身回血，每 2 秒 1 点"),
                ),
                UpdateEntry(
                    Items.COPPER_INGOT,
                    "僵尸战士",
                    listOf("购买改为铜锭 ×8、红石 ×3"),
                ),
                UpdateEntry(
                    Items.HUSK_SPAWN_EGG,
                    "尸壳",
                    listOf("只拆建筑，不主动攻击玩家和唤灵"),
                ),
                UpdateEntry(
                    Items.RAVAGER_SPAWN_EGG,
                    "劫掠兽",
                    listOf("对建筑伤害改为 5；掠夺下只拆建筑，不主动攻击玩家和唤灵；固守下仍留在塔边按常规索敌"),
                ),
                UpdateEntry(
                    Items.BOW,
                    "箭矢击退",
                    listOf("玩家弓、弩架、冲击附魔和唤灵射出的箭，击退均为原版的 25%"),
                ),
                UpdateEntry(
                    Items.SHORT_GRASS,
                    "地图草丛",
                    listOf("地图生成时把高草改成短草"),
                ),
                UpdateEntry(
                    Items.ARROW,
                    "箭的显示",
                    listOf("唤灵和弩架射出的箭，名称显示为「箭」"),
                ),
                UpdateEntry(
                    Items.LIGHTNING_ROD,
                    "电塔",
                    listOf("最底层四只抛光闪长岩楼梯改为真正的楼梯，可以按楼梯碰撞上下"),
                ),
                UpdateEntry(
                    Items.BARRIER,
                    "传输装置",
                    listOf("核心正上方两格空气改为屏障"),
                ),
                UpdateEntry(
                    Items.ENDER_PEARL,
                    "重新加入",
                    listOf("仍在本局的队员重新加入后留在对局里；未参加本局的玩家若进入战场，会被送回大厅"),
                ),
            ),
        ),
    )

    fun latestIndex(): Int = all.lastIndex
}

class UpdateNoticeBoard(
    private val viewer: ServerPlayer,
    private val noticeIndex: Int,
    private val page: Int,
) : SimpleGui(MenuType.GENERIC_9x6, viewer, false) {
    init {
        paint()
    }

    private fun paint() {
        val notice = UpdateNotices.all.getOrNull(noticeIndex)
        title = line(
            if (notice == null) "更新公告" else "更新公告 · ${notice.version}",
            ChatFormatting.YELLOW,
        )
        fillGlass(this)
        if (notice == null) {
            setSlot(
                22,
                GuiElement(
                    named(Items.BOOK, "暂无更新公告", ChatFormatting.WHITE, listOf("维护时会在这里追加新版本")),
                ) { _, _, _, _ -> },
            )
            placeBack(this, viewer, 53)
            return
        }
        val contentSlots = (9..44).toList()
        val pages = notice.entries.chunked(contentSlots.size).ifEmpty { listOf(emptyList()) }
        val index = page.mod(pages.size)
        for ((slot, entry) in contentSlots.zip(pages[index])) {
            setSlot(
                slot,
                GuiElement(
                    named(entry.item, entry.title, ChatFormatting.WHITE, entry.lore),
                ) { _, _, _, _ -> },
            )
        }
        setSlot(
            45,
            GuiElement(
                named(Items.BOOKSHELF, "历次更新公告", ChatFormatting.YELLOW, listOf("从左到右、从新到旧查看")),
            ) { _, click, _, _ ->
                if (!click.isLeft || click.shift) return@GuiElement
                close()
                UpdateHistoryBoard(viewer).open()
            },
        )
        if (pages.size > 1) {
            setSlot(
                50,
                GuiElement(
                    named(Items.ARROW, "下一页", ChatFormatting.WHITE, listOf("第 ${index + 1}/${pages.size} 页，点击循环切换")),
                ) { _, click, _, _ ->
                    if (!click.isLeft || click.shift) return@GuiElement
                    close()
                    UpdateNoticeBoard(viewer, noticeIndex, index + 1).open()
                },
            )
        }
        placeBack(this, viewer, 53)
    }
}

class UpdateHistoryBoard(private val viewer: ServerPlayer) : SimpleGui(MenuType.GENERIC_9x6, viewer, false) {
    init {
        title = line("历次更新公告", ChatFormatting.YELLOW)
        fillGlass(this)
        val notices = UpdateNotices.all.asReversed()
        for ((index, notice) in notices.withIndex()) {
            val slot = 9 + index
            if (slot >= 45) break
            val original = UpdateNotices.all.lastIndex - index
            setSlot(
                slot,
                GuiElement(
                    named(Items.KNOWLEDGE_BOOK, "更新公告 · ${notice.version}", ChatFormatting.WHITE, listOf("点击打开")),
                ) { _, click, _, _ ->
                    if (!click.isLeft || click.shift) return@GuiElement
                    close()
                    UpdateNoticeBoard(viewer, original, 0).open()
                },
            )
        }
        setSlot(
            45,
            GuiElement(named(Items.OAK_DOOR, "返回", ChatFormatting.WHITE)) { _, click, _, _ ->
                if (!click.isLeft || click.shift) return@GuiElement
                close()
                UpdateNoticeBoard(viewer, UpdateNotices.latestIndex(), 0).open()
            },
        )
    }
}
