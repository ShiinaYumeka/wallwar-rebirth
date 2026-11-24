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
                        "准备和取消准备会显示（确定人数/队伍人数）",
                        "大厅终端不能丢出，死亡时也会收回",
                    ),
                ),
                UpdateEntry(
                    Items.SPYGLASS,
                    "观察",
                    listOf(
                        "观察阶段开始时，已选择旁观的人会和队员一起进入场地",
                        "地图尚未生成时点旁观，会等到观察开始再进入",
                        "队员传送到本队出生点，旁观者到场地中间",
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
                        "未组队旁观者该界面尚未开放；不能丢出，死亡不掉落",
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
                        "同意、撤回和投降图标都会显示（确定人数/队伍人数）",
                    ),
                ),
                UpdateEntry(
                    Items.OAK_DOOR,
                    "返回大厅",
                    listOf(
                        "观察和战斗中，未组队旁观者拿到橡木门，点击后回大厅，对局继续",
                        "离开会记下，重进不会再被送回旁观",
                        "有队伍的人不会拿到；复活倒计时中的旁观也不能靠它离开；死亡不掉落",
                        "结算阶段这扇门改为同意重置，不是离开旁观",
                    ),
                ),
                UpdateEntry(
                    Items.MAP,
                    "结算",
                    listOf(
                        "身在竞技场或本局中的在线玩家点一次同意，再点一次取消",
                        "达到半数即重置，奇数向上取整：3 人要 2 人，4 人要 2 人",
                        "动作栏显示当前同意人数",
                        "重置送回主世界大厅，清空队伍、状态、出生点和地上掉落物，并关掉飞行、飞行许可和无敌",
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
                    listOf(
                        "仍在本局的队员重新加入后留在对局里",
                        "已选择旁观的人重新加入后仍进入场地旁观",
                        "未参加本局的玩家若进入战场，会被送回主世界大厅，出生点也改回大厅",
                    ),
                ),
                UpdateEntry(
                    Items.COMMAND_BLOCK,
                    "指令",
                    listOf(
                        "调试指令改到 /ww test，仅管理员可用",
                        "/ww match 仍可打开界面，管理员重置仍为 /ww match reset",
                    ),
                ),
            ),
        ),
        UpdateNotice(
            version = "1.0.1",
            entries = listOf(
                UpdateEntry(
                    Items.SHIELD,
                    "友军伤害",
                    listOf(
                        "红队和蓝队关闭友军伤害",
                        "同队玩家和同队唤灵互相攻击不掉血，包括近战、箭、尖牙和爆炸",
                    ),
                ),
                UpdateEntry(
                    Items.LIME_DYE,
                    "准备",
                    listOf(
                        "准备人数两队合计显示",
                        "有人准备或取消时，所有人都会看到是谁，并显示（已准备人数/总人数）",
                        "全员准备后倒数 5 秒，期间取消会打断；再次全员准备仍从 5 秒开始",
                        "倒数结束后再按原流程准备地图并进入观察",
                    ),
                ),
                UpdateEntry(
                    Items.BUNDLE,
                    "绑定物品",
                    listOf(
                        "职业技能、大厅终端、局内终端、指挥物品和掠夺之旗不能丢出，也不能放到副手、盔甲栏、工作台、箱子或团队仓库",
                        "可在快捷栏和背包里调整位置",
                        "死亡不掉落，复活后仍在原来的栏位",
                        "局内终端改为只能右键打开，背包里左键点击不会打开商店",
                        "丢出绑定物品时会留在原栏位，快捷栏不再看起来像消失",
                    ),
                ),
                UpdateEntry(
                    Items.GOAT_HORN,
                    "指挥",
                    listOf(
                        "我方兵营装上扩散模块后才发放指挥物品（山羊角），名称显示当前是跟随还是出征，默认跟随",
                        "选中时按副手键切换模式。右键只指挥面向的一只同队唤灵，蹲下右键才指挥 8 格内的全部",
                        "新召唤的唤灵默认为出征，沿用各自原有索敌",
                        "跟随优先玩家，其次敌方唤灵，再是建筑；只拆建筑的单位仍只打建筑，蜘蛛选建筑时仍会跳过城墙",
                        "离跟随的玩家超过 16 格会先回去，期间被打不反击；回到 8 格内再按跟随索敌",
                        "矿工跟随期间暂停挖矿和回库；村民跟随只跟随，出征后恢复原行为",
                        "固守中的单位可被跟随拉走；再对它们出征时，即使所属哨塔仍在固守也直接出征，除非该塔之后重新开启固守",
                        "被跟随的玩家死亡、进入旁观、离队、断开或对局结束，这些唤灵立即改为出征",
                    ),
                ),
                UpdateEntry(
                    Items.LIGHTNING_ROD,
                    "电塔",
                    listOf(
                        "射程固定 16 格，伤害改为 6、10、14",
                        "攻击间隔改为 10、8、6 秒",
                        "攻击没有蓄风的敌方玩家和唤灵，优先玩家",
                        "命中给予蓄风 20 秒，不随等级变化，玩家时间减半；只作为电塔计时显示，死亡不爆炸",
                        "命中给予失明 1 秒、减速 V 1 秒、挖掘疲劳 I 10 秒、虚弱 5 秒；玩家身上出现灵魂火焰",
                        "命中时在核心上方约 7 格放出一圈粒子",
                        "强化 I：射程扩大 50%，变为 24 格",
                        "强化 II：给予的挖掘疲劳从 I 提升到 II",
                        "扩散：每次攻击时，对射程内已有蓄风的敌方唤灵再造成一次闪电伤害",
                    ),
                ),
                UpdateEntry(
                    Items.LIGHTNING_ROD,
                    "电塔与农田",
                    listOf(
                        "怪物攻击电塔底层楼梯或农田耕地，会扣该建筑生命",
                        "玩家仍然不能破坏这些部件",
                    ),
                ),
                UpdateEntry(
                    Items.IRON_SWORD,
                    "怪物索敌",
                    listOf(
                        "城墙等防御性建筑，以及城墙荆棘，都不会触发怪物反击",
                        "贴近建筑水平 8 格内几乎不动并持续 3 秒仍未能打中，会放弃该目标并改索敌；没有其他敌方建筑时继续打这一座",
                        "被攻击后，若当前目标更近，且攻击者不在本单位攻击范围内，不会转入反击",
                    ),
                ),
                UpdateEntry(
                    Items.COMPASS,
                    "索敌节奏",
                    listOf(
                        "已经锁住、并且仍在范围内的目标不会每刻丢掉重选",
                        "半径内的唤灵和玩家大约每 0.5 秒重选一次，各唤灵错开，不挤在同一刻",
                        "建筑大约每 2 秒重选一次；全图名单每刻只整理一份",
                        "走向建筑时，这段路走完再接下一段，不会走到一半停住",
                    ),
                ),
                UpdateEntry(
                    Items.RECOVERY_COMPASS,
                    "复活追踪",
                    listOf(
                        "立即重生后，移动不再因为区块追踪丢失而报错",
                        "已经死亡的玩家不会再被当成还在场上的人同步给其他人",
                    ),
                ),
                UpdateEntry(
                    Items.BARRIER,
                    "中途加入",
                    listOf(
                        "观察、战斗或结算开始后，不能再加入红队或蓝队",
                        "已经在局里的玩家重连仍留在原来的队伍",
                    ),
                ),
                UpdateEntry(
                    Items.CROSSBOW,
                    "弩架",
                    listOf(
                        "购买价格改为铜锭 ×24",
                        "1、2、3 级射击间隔都是 1 秒",
                        "实心方块挡住的目标不索敌，改选下一个看得到的",
                        "箭的出手位置再向上 0.2 格，从核心顶面往上 0.4 格射出",
                    ),
                ),
                UpdateEntry(
                    Items.EVOKER_SPAWN_EGG,
                    "唤魔者",
                    listOf(
                        "索敌半径 32 格；敌方建筑进入 32 格后停下并召唤恼鬼，不近战",
                        "尖牙距离 20 格内有敌人时逃跑并施法：3 格内在身边放一圈，10 格内向目标放一条直线，再远则在目标脚下放一个",
                        "尖牙距离内没有敌人时，走向最近的敌方建筑",
                        "优先攻击弩架、特斯拉塔和炮台，其次其他建筑",
                        "恼鬼单独注册，不进入生产；生命 14，对玩家攻击 4，拆建筑伤害与僵尸相同",
                        "恼鬼飞向唤魔者正在进攻的建筑并攻击；唤魔者没有建筑目标时，再按敌方建筑、敌方唤灵、敌方玩家自己索敌，半径 32 格",
                        "恼鬼的活动原点跟着唤魔者，空闲时在唤魔者附近活动，不回到哨塔",
                    ),
                ),
                UpdateEntry(
                    Items.TOTEM_OF_UNDYING,
                    "恼鬼进攻",
                    listOf(
                        "恼鬼飞向唤魔者正在进攻的建筑，进入 2 格后攻击",
                        "唤魔者没有建筑目标时，仍按敌方建筑、敌方唤灵、敌方玩家自己索敌",
                        "恼鬼直接飞过去，不再对建筑寻路",
                        "恼鬼和卫道士不再每刻重扫全图建筑，避免它们在场时变卡",
                    ),
                ),
                UpdateEntry(
                    Items.IRON_PICKAXE,
                    "矿工交矿",
                    listOf(
                        "交矿时约 1 秒水平移动不到 0.4 格、还进不了交矿距离，会先朝目的地走近 1.5 格",
                        "还是卡住就换到旁边再靠近；重新走动后仍直接走向核心",
                    ),
                ),
                UpdateEntry(
                    Items.OAK_PLANKS,
                    "唤灵脱困",
                    listOf(
                        "地面唤灵离目标还远、约 1 秒水平移动不到 0.4 格时会尝试脱困",
                        "只拆目标主轴正前方挡住身体的木板；这一格是空的才看另一轴。每秒一块，先脚后头，会掉落",
                        "坑沿比脚底至少高 2 格、头顶能跳时才垫橡木木板，最多 4 格；一格高的坎和悬崖不垫",
                        "前一格或后一格没有挡住身体，或者只差一格高，不会停下来垫板",
                        "建筑方块不拆也不垫；已经贴到建筑攻击距离时不脱困",
                        "恼鬼会飞，不垫板也不拆板",
                    ),
                ),
                UpdateEntry(
                    Items.BRICKS,
                    "建筑放置",
                    listOf(
                        "不再限制只能放在己方半场",
                        "附近 16 格内需要有己方建筑才能放置，基地也算",
                    ),
                ),
                UpdateEntry(
                    Items.HOPPER,
                    "传输装置",
                    listOf("放置后自带的矿工僵尸生成在装置旁边，不再出现在内部"),
                ),
                UpdateEntry(
                    Items.WHEAT,
                    "农田",
                    listOf(
                        "农民挖掘小麦和等待时间改为 3 秒",
                        "农民最多带 20 个小麦，满了先去交，再继续收割",
                        "小麦改为每 20 秒生长一个阶段",
                    ),
                ),
                UpdateEntry(
                    Items.VILLAGER_SPAWN_EGG,
                    "村民",
                    listOf("我方农田没有农民在干活时，无职业村民会自己走过去成为农民"),
                ),
                UpdateEntry(
                    Items.BEACON,
                    "基地",
                    listOf(
                        "强化 1 改为：装上后所有矿物产出间隔从 7 秒变为 5 秒，不再选择单一矿物",
                        "钻石生成概率改为 10%",
                    ),
                ),
                UpdateEntry(
                    Items.WHITE_BANNER,
                    "劫掠哨站",
                    listOf(
                        "所有怪物红石消耗改为 1",
                        "小麦：卫道士 4、唤魔者 8、幻术师 5、劫掠兽 32",
                        "劫掠兽不再消耗钻石",
                        "当前生产的刷怪蛋会用物品数量显示剩余生成秒数",
                        "放出落点与兵营相同，按怪物体积占格，放不下时寻位范围扩到 5 格",
                        "卫道士改为 2 级，幻术师改为 1 级",
                        "固守默认在固守地点附近 32 格内巡逻，没改过时地点是哨塔",
                        "强化 I：之后生成的唤灵拥有 5 点基础护甲",
                        "强化 II：附近 32 格内的我方唤灵获得抗性提升 I，离开后消失",
                        "扩散：发放掠夺之旗，右键方块更改这座哨塔的固守地点",
                    ),
                ),
                UpdateEntry(
                    Items.NAME_TAG,
                    "建筑商店",
                    listOf("购买页不再用命名牌标注每一排类别，仍按资源、生产、防御分类摆放"),
                ),
                UpdateEntry(
                    Items.CREEPER_SPAWN_EGG,
                    "苦力怕",
                    listOf(
                        "生产消耗改为红石 ×2",
                        "生命高于一半时不索敌玩家和唤灵，只拆建筑",
                        "生命归零后会原地蓄力并爆炸",
                    ),
                ),
                UpdateEntry(
                    Items.CREEPER_SPAWN_EGG,
                    "高压苦力怕",
                    listOf(
                        "3 级，生产消耗红石 ×5，生命 100",
                        "只攻击建筑，不追玩家和唤灵",
                        "爆炸伤害是苦力怕的两倍：中心 80，建筑 20",
                        "生命归零后同样蓄力并爆炸，其余与苦力怕相同",
                    ),
                ),
                UpdateEntry(
                    Items.SPAWNER,
                    "兵营",
                    listOf(
                        "当前生产的刷怪蛋会用物品数量显示剩余生成秒数",
                        "二级、三级缩短后的间隔也会反映在倒计时上",
                        "放出时按怪物碰撞箱占格，不再只看脚和头这一列",
                        "附近 3 格放不下时，寻位范围扩到 5 格",
                        "扩散模块改为发放指挥物品，不再治疗附近唤灵",
                    ),
                ),
                UpdateEntry(
                    Items.SKELETON_SPAWN_EGG,
                    "骷髅",
                    listOf("生产和商店的红石消耗改为 2"),
                ),
                UpdateEntry(
                    Items.COMMAND_BLOCK,
                    "测试对局",
                    listOf(
                        "管理员可在大厅使用 /ww test game 开启单人测试对局",
                        "流程与正式对局相同，允许只有一队开局",
                        "购买和升级不消耗资源；测试玩家额外获得绑定的「换队伍」物品，右键在红蓝之间切换",
                        "重置对局后测试状态清除",
                    ),
                ),
            ),
        ),
        UpdateNotice(
            version = "1.0.2",
            entries = listOf(
                UpdateEntry(
                    Items.SKELETON_SPAWN_EGG,
                    "骷髅",
                    listOf(
                        "红石消耗改回 1",
                        "生命提升至 40",
                        "基础生产时间改为 30 秒，二级 20 秒、三级 15 秒",
                    ),
                ),
                UpdateEntry(
                    Items.IRON_PICKAXE,
                    "矿工僵尸",
                    listOf(
                        "红石消耗降低为 1",
                        "基础生产时间降低为 60 秒，二级 40 秒、三级 30 秒",
                    ),
                ),
                UpdateEntry(
                    Items.WITHER_SKELETON_SPAWN_EGG,
                    "骷髅死士",
                    listOf("红石消耗降低为 2"),
                ),
                UpdateEntry(
                    Items.IRON_GOLEM_SPAWN_EGG,
                    "铁傀儡",
                    listOf(
                        "红石消耗降低为 4",
                        "盔甲增加到 15",
                    ),
                ),
                UpdateEntry(
                    Items.CROSSBOW,
                    "弩架",
                    listOf(
                        "三级射程都改为 16 格",
                        "箭矢速度 2 / 3 / 4，基础伤害 2",
                        "射击间隔改为 2 秒",
                        "购买和升 2、3 级都改为铜锭 ×16",
                        "强化模块 2 的间隔缩短提升至 25%，装上后变为 1.5 秒",
                    ),
                ),
                UpdateEntry(
                    Items.BOW,
                    "幻术师",
                    listOf(
                        "提升为 2 级怪",
                        "小麦消耗提升至 8",
                    ),
                ),
                UpdateEntry(
                    Items.EVOKER_SPAWN_EGG,
                    "唤魔者",
                    listOf(
                        "提升为 3 级怪",
                        "小麦消耗提升至 16",
                    ),
                ),
                UpdateEntry(
                    Items.COMPOSTER,
                    "农田",
                    listOf(
                        "放置后不再送一个农民",
                        "价格降低为铁锭 ×24、铜锭 ×24",
                    ),
                ),
                UpdateEntry(
                    Items.FEATHER,
                    "驱魔人",
                    listOf(
                        "商店购买的剑改为亡灵杀手 II，并加上节肢杀手 II，不另收费",
                        "每次买剑生效后冷却 180 秒，冷却中再买不加附魔",
                        "释放急速冲刺时会显示被动剩余冷却，冷却转好会提示「被动技能已就绪」",
                    ),
                ),
                UpdateEntry(
                    Items.HUSK_SPAWN_EGG,
                    "尸壳",
                    listOf("对建筑伤害削弱回 1"),
                ),
                UpdateEntry(
                    Items.EXPERIENCE_BOTTLE,
                    "破坏手",
                    listOf(
                        "释放娴熟技巧时会清除自身的挖掘疲劳和蓄风",
                        "附近 8 格内有敌方唤灵时才会隐身 10 秒",
                    ),
                ),
                UpdateEntry(
                    Items.BOOK,
                    "图鉴",
                    listOf(
                        "建筑的强化、强化 2、扩散描述改为和局内真实效果一致",
                        "表示数值的选项会同时用物品数量显示，血量除外",
                        "怪物图鉴的数值格子也按同样规则处理",
                    ),
                ),
            ),
        ),
        UpdateNotice(
            version = "1.1.0",
            entries = listOf(
                UpdateEntry(
                    Items.BLAST_FURNACE,
                    "晶核工坊",
                    listOf(
                        "购买改为钻石 ×8、青金石 ×64",
                        "本队有工坊时，基地产出钻石的概率从 10% 提升到 12%",
                        "需要有一座已放置且仍在的晶核工坊，才能购买晶化反应炉、晶核制导炮、传送尖塔和晶震装置",
                        "核心下一格的火改成深板岩瓦台阶，再下一格的地狱岩改成点燃的营火",
                    ),
                ),
                UpdateEntry(
                    Items.FURNACE,
                    "晶化反应炉",
                    listOf(
                        "新增生产建筑，需要已放置且仍在的工坊才能买，不限数量，没有模块槽",
                        "购买铁锭 ×48、铜锭 ×48；1 级升 2 级为铁锭 ×64、铜锭 ×64；2 级升 3 级再加钻石 ×8",
                        "生命三级都是 40，外形共用，核心是熔炉。商店和图鉴图标也是熔炉",
                        "战斗中开启后，从团队箱一次扣 10 个铜锭或铁锭：两边都够时各 5 个，否则尽量均分再由有存货的一边补齐；不够 10 个就等待",
                        "20 秒后按当时等级的成功率结算，1 / 2 / 3 级为 20% / 30% / 40%。成功进 1 个钻石，失败各 50% 进 4 个红石或 4 个青金石，都进团队箱，放不下就作废",
                        "关闭后当前这一批会做完；拆掉、打掉或进入结算时，进行中的原料掉在核心，不给产物",
                        "观察和结算阶段不能开启；敌方打不开面板。工坊被拆后，已经放下的炉仍可继续工作",
                        "开面板请右键核心熔炉；右键营火、岩浆只会挡住原版界面",
                    ),
                ),
                UpdateEntry(
                    Items.TNT,
                    "晶核制导炮",
                    listOf(
                        "超级武器，每队只能有一座（含还没放下的核心）",
                        "购买铜锭 ×64、铁锭 ×64、钻石 ×4；每级升级再加铜锭 ×64、铁锭 ×64、钻石 ×4",
                        "冷却只在战斗阶段计时，1 / 2 / 3 级为 600 / 500 / 400 秒，放下时开始，开炮后重新计时，侧边栏全员可见",
                        "「开炮」进入旁观选点，潜行取消不进冷却；确认后 TNT 从建筑最高处升空再砸向目标",
                        "爆炸不破坏、不引燃方块，不伤害友方、无队伍者和自身；对玩家和唤灵中心伤害 120、半径 16，32 格外为 0；半径 16 内建筑固定受伤 30",
                        "爆炸伤害只按距离计算，不再被方块挡住；击退仍看遮挡",
                        "威力半径内的木板会被拆掉并掉落；建筑上的木板不拆",
                        "怪物只打核心，打零件不扣血",
                        "开炮或取消后关闭飞行，不能接着飞",
                        "升空时全图听到信标音，并看到「晶核制导炮 / 正在升空」",
                        "升空和坠落时 TNT 附近有螺旋紫色粒子，落地爆炸粒子铺满威力半径",
                    ),
                ),
                UpdateEntry(
                    Items.AMETHYST_SHARD,
                    "传送尖塔",
                    listOf(
                        "同样需要工坊，每队限一座",
                        "购买铁锭 ×48、铜锭 ×48、钻石 ×4；每级升级再加铁锭 ×48、铜锭 ×48、钻石 ×4",
                        "冷却 480 / 400 / 320 秒",
                        "「传送」选点后玩家传到目标地表，正在跟随的同队唤灵一起走，没有落点的留下",
                        "怪物只打核心，打零件不扣血",
                        "传送或取消后关闭飞行，不能接着飞",
                        "落地时出现大量传送粒子；全图听到传送音，标题显示传送尖塔和抵达的玩家",
                    ),
                ),
                UpdateEntry(
                    Items.LODESTONE,
                    "晶震装置",
                    listOf(
                        "新增防御建筑，需要工坊，不限数量，有三个模块槽",
                        "购买和每级升级都是钻石 ×2，生命 20，射程 24，溅射 3，伤害 5，间隔 3 / 2 / 1 秒",
                        "不需要视线，只打敌对玩家和唤灵；唤灵的叠加伤害最高 30 点，每次仍加 1 点",
                        "强化 2 每次加 2 点，上限 60 点。打玩家只造成基础伤害，不叠层",
                        "商店、图鉴和建筑面板的图标改为磁石",
                        "攻击时从中间紫水晶连出紫色粒子，命中区域有白色和蓝色粒子",
                        "扩散：自身范围内没人时，把 24 格内的同队晶震装置当作射程延伸",
                        "延伸攻击的粒子只连到那座塔，不连到怪物",
                    ),
                ),
                UpdateEntry(
                    Items.BLAST_FURNACE,
                    "矿物反应堆",
                    listOf(
                        "两侧角矿靠边界各生成一座 3 级、生命 50、开局无主的矿物反应堆，核心是高炉",
                        "产出与 3 级基地相同，进入团队箱；可装强化 1、强化 2，不能升级",
                        "有跟随的无职业村民时打开面板点占领，村民走到后立刻换主，可被对方再占领；打掉后本局消失",
                    ),
                ),
                UpdateEntry(
                    Items.MAP,
                    "地图与矿物",
                    listOf(
                        "默认边长改为 160 格，可在 120 到 300 之间调整",
                        "散矿每处最多 32 块；中间矿的铁、铜各改为 128 块",
                        "每座基地后方、树的更外侧固定再刷一脉最多 32 块的铁矿或铜矿",
                    ),
                ),
                UpdateEntry(
                    Items.CHEST,
                    "商店",
                    listOf(
                        "建筑栏改为单页分列：资源、生产、防御、超级武器、模块",
                        "未满足工坊前置，或已经拥有该超级武器时，对应格子显示为屏障",
                        "铁矛价格改为铁锭 ×4",
                    ),
                ),
                UpdateEntry(
                    Items.BOOK,
                    "图鉴",
                    listOf(
                        "建筑图鉴加入晶核工坊、晶化反应炉、晶核制导炮、传送尖塔和晶震装置",
                        "消耗用 1、2、3 个金锭表示等级，该级的全部材料写在描述里",
                    ),
                ),
                UpdateEntry(
                    Items.CRAFTING_TABLE,
                    "建筑交互",
                    listOf(
                        "右键建筑部件会打开该建筑面板，不再打开原版界面",
                        "晶核制导炮模型上的 TNT 不会被引燃或炸毁",
                    ),
                ),
                UpdateEntry(
                    Items.CLOCK,
                    "超级武器冷却",
                    listOf(
                        "侧边栏名称用显示名，颜色跟队伍",
                        "冷却中右侧直接是剩余秒数；就绪用淡绿色「就绪」",
                        "升级后，当前冷却减去新旧两级冷却的差",
                    ),
                ),
                UpdateEntry(
                    Items.BEACON,
                    "基地",
                    listOf(
                        "怪物只打核心，打零件不扣血",
                        "本队有晶核工坊时，钻石产出概率从 10% 变为 12%",
                    ),
                ),
                UpdateEntry(
                    Items.BRICKS,
                    "建筑放置",
                    listOf(
                        "核心高于底部的建筑会向上抬，让最底下的方块落在点选高度",
                        "当时在建筑里的玩家一起向上移动；还卡在里面就传到旁边能站住的位置",
                        "升级不再检查挡路方块",
                        "拆除或被打掉时，农田小麦、工坊紫水晶这些结构方块不掉落",
                        "被打掉时不再掉落强化和扩散模块；从面板手动拆除仍会掉落",
                    ),
                ),
                UpdateEntry(
                    Items.TNT,
                    "超级武器瞄准",
                    listOf(
                        "瞄准时从高空向下看，隐身且不会被索敌，右键可以选定和确认",
                        "第一次选定后，目标范围有只有友方看得见的粒子，再选一次才释放",
                        "制导炮从建筑最高处发射",
                        "传送时正在跟随的同队唤灵一起走，瞄准期间不会解除跟随",
                    ),
                ),
                UpdateEntry(
                    Items.DISPENSER,
                    "炮台",
                    listOf(
                        "购买不再消耗铁锭，只花铜锭 ×32",
                        "2 级升级改为铜锭 ×48，3 级升级改为铜锭 ×64，都不再消耗铁锭",
                    ),
                ),
                UpdateEntry(
                    Items.WHITE_BANNER,
                    "掠夺者哨塔",
                    listOf(
                        "购买改为铁锭 ×32",
                        "2 级升级改为铁锭 ×48",
                        "3 级升级改为铁锭 ×48、钻石 ×2",
                    ),
                ),
                UpdateEntry(
                    Items.HOPPER,
                    "传输装置",
                    listOf(
                        "购买改为铁锭 ×48、铜锭 ×24",
                    ),
                ),
                UpdateEntry(
                    Items.CREEPER_HEAD,
                    "高压苦力怕",
                    listOf(
                        "基础生产间隔改为 30 秒；3 级兵营生产时按原有加速变为 15 秒",
                        "索敌改为和苦力怕一样：生命高于一半时只拆建筑，降到一半及以下会追击和反击",
                    ),
                ),
                UpdateEntry(
                    Items.IRON_AXE,
                    "征服者",
                    listOf(
                        "新职业。主动叫镇压：对面向 4 格内的敌方防御建筑核心造成 8 点伤害，冷却 90 秒",
                        "被动：自己拆掉带强化模块的敌方建筑时，按该模块购买价的 75% 获得青金石，向上取整",
                        "强化和强化 2 各算各的，现在每种是青金石 ×24；扩散模块不算",
                    ),
                ),
                UpdateEntry(
                    Items.GOLDEN_APPLE,
                    "冠军",
                    listOf(
                        "新职业。主动叫加冕：恢复 2 点生命，冷却 60 秒",
                        "穿着一整套钻石护甲时，最大生命 +10，攻击力和对建筑伤害各 +1",
                        "这时加冕改为恢复 8 点生命，并清除身上的负面效果",
                    ),
                ),
                UpdateEntry(
                    Items.GOAT_HORN,
                    "指挥官",
                    listOf(
                        "新职业。开局发 64 铁、64 铜和一座 3 级兵营；不能再购买或放置新的兵营",
                        "放下这座兵营时，对方会收到标题和坐标提醒",
                        "打到这座兵营的伤害会转到我方基地，兵营本身不扣血",
                        "主动叫调度：满血时回收并可以换位置再放，冷却 30 秒",
                    ),
                ),
                UpdateEntry(
                    Items.SPIDER_SPAWN_EGG,
                    "索敌",
                    listOf(
                        "矿物反应堆被占领之后，怪物仍然不索敌、不主动攻击",
                        "蜘蛛只跳过城墙，会攻击晶震装置",
                    ),
                ),
            ),
        ),
        UpdateNotice(
            version = "1.1.1",
            entries = listOf(
                UpdateEntry(
                    Items.TNT,
                    "超级武器瞄准",
                    listOf("晶核制导炮和传送尖塔统一用左键选点，再按左键确认。空手或持物都能操作，潜行取消"),
                ),
                UpdateEntry(
                    Items.IRON_AXE,
                    "职业技能",
                    listOf("手持镇压或强力粘合剂右键建筑核心时，优先处理技能"),
                ),
                UpdateEntry(
                    Items.WHEAT,
                    "建筑拆除",
                    listOf("移除农田、晶核工坊等建筑时，结构中的小麦和紫水晶等方块不再因支撑消失而掉落"),
                ),
                UpdateEntry(
                    Items.IRON_PICKAXE,
                    "矿工僵尸",
                    listOf("不再选中建筑结构里的矿石，包括边角矿物反应堆里的矿石"),
                ),
                UpdateEntry(
                    Items.CHEST,
                    "商店",
                    listOf(
                        "下架铁长矛和钻石铲",
                        "钻石矛造价改为钻石 ×4",
                        "新增 TNT，售价钻石 ×1、红石 ×4；需要本队已放下且仍在的晶核工坊才能购买",
                        "战斗阶段右键投出点燃的 TNT，固定引信 4 秒，碰到方块不会立即爆炸",
                        "爆炸伤害与 2 级炮台相同：中心 30，威力半径 4，8 格外为 0；建筑按四分之一计算；不破坏、不引燃方块，不伤害友方和己方建筑",
                    ),
                ),
                UpdateEntry(
                    Items.HUSK_SPAWN_EGG,
                    "尸壳",
                    listOf(
                        "基础移动速度降低 25%",
                    ),
                ),
                UpdateEntry(
                    Items.IRON_CHESTPLATE,
                    "重甲尸壳",
                    listOf(
                        "新增兵种，售价铁锭 ×8、红石 ×4",
                        "需要本队已放下且仍在的晶核工坊才能开始制作；已经放出来的不受工坊被拆影响",
                        "80 生命，一套铁甲提供 15 护甲，铁剑，对玩家伤害 8",
                        "移速与削弱后的尸壳相同",
                        "索敌优先攻击建筑，但受伤会反击",
                        "生产间隔、对建筑伤害等其余规则与尸壳相同",
                        "现已属于亡灵，亡灵杀手等效果按亡灵结算",
                    ),
                ),
                UpdateEntry(
                    Items.CREEPER_HEAD,
                    "高压苦力怕",
                    listOf(
                        "3 级兵营现在可以生产；面板补上原先缺少的第 12 格",
                        "最大生命由 100 改为 40",
                    ),
                ),
                UpdateEntry(
                    Items.IRON_GOLEM_SPAWN_EGG,
                    "铁傀儡",
                    listOf("用铁锭修复一次恢复由 25 改为 8"),
                ),
                UpdateEntry(
                    Items.IRON_AXE,
                    "征服者",
                    listOf(
                        "用炮台扩散的载人 TNT 炸毁敌方建筑时，被动也会返还青金石",
                        "扩散模块本身也会返还青金石",
                    ),
                ),
                UpdateEntry(
                    Items.LIGHTNING_ROD,
                    "特斯拉塔",
                    listOf(
                        "购买改为铜锭 ×24",
                        "命中给予的蓄风改为唤灵 10 秒、玩家 5 秒",
                        "强化 2 除了把挖掘疲劳提升到 II，也会把虚弱提升到 II",
                        "基础攻击间隔由 10 / 8 / 6 秒改为 5 / 4 / 3 秒",
                    ),
                ),
                UpdateEntry(
                    Items.CROSSBOW,
                    "弩架",
                    listOf(
                        "强化 2 的射击间隔减少由 25% 提升到 40%，间隔变为 1.2 秒",
                    ),
                ),
                UpdateEntry(
                    Items.DISPENSER,
                    "炮台",
                    listOf(
                        "强化 2 的发射间隔减少由 20% 提升到 30%，间隔变为 7 秒",
                        "扩散的载人 TNT 碰到实心方块或敌人会立刻爆炸，穿过友方不爆，空中满 5 秒仍会爆炸；爆炸伤害提升 25%",
                        "载人 TNT 会拆掉威力半径内的木板并掉落；建筑上的木板不拆，也不引燃方块",
                    ),
                ),
                UpdateEntry(
                    Items.ANVIL,
                    "建筑",
                    listOf(
                        "升级后生命回满，变为新等级的上限",
                    ),
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
