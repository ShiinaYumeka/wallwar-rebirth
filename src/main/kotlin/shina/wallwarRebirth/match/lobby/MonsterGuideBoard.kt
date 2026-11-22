package shina.wallwarRebirth.match.lobby

import eu.pb4.sgui.api.elements.GuiElement
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.ChatFormatting
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.Items
import shina.wallwarRebirth.monster.MonsterDefinition
import shina.wallwarRebirth.monster.MonsterSource
import shina.wallwarRebirth.monster.Monsters
import shina.wallwarRebirth.rules.MatchRules

object MonsterGuide {
    val purchasable: List<MonsterDefinition> = listOf(
        Monsters.ZOMBIE,
        Monsters.SKELETON,
        Monsters.SPIDER,
        Monsters.MINER,
        Monsters.HUSK,
        Monsters.ARMORED_HUSK,
        Monsters.DEATH_KNIGHT,
        Monsters.CREEPER,
        Monsters.WARRIOR,
        Monsters.GOLEM,
        Monsters.CHARGED_CREEPER,
        Monsters.PILLAGER,
        Monsters.ILLUSIONER,
        Monsters.VINDICATOR,
        Monsters.EVOKER,
        Monsters.RAVAGER,
    )
}

class MonsterHomeBoard(private val viewer: ServerPlayer) : SimpleGui(MenuType.GENERIC_9x6, viewer, false) {
    init {
        title = line("怪物", ChatFormatting.YELLOW)
        fillGlass(this)
        for ((index, definition) in MonsterGuide.purchasable.withIndex()) {
            val slot = 9 + index
            setSlot(
                slot,
                GuiElement(
                    named(definition.egg, definition.displayName, ChatFormatting.WHITE, listOf("点击查看数值")),
                ) { _, click, _, _ ->
                    if (!click.isLeft || click.shift) return@GuiElement
                    close()
                    MonsterDetailBoard(viewer, definition).open()
                },
            )
        }
        placeBack(this, viewer)
    }
}

private class MonsterDetailBoard(
    private val viewer: ServerPlayer,
    definition: MonsterDefinition,
) : SimpleGui(MenuType.GENERIC_9x6, viewer, false) {
    init {
        title = line("怪物 · ${definition.displayName}", ChatFormatting.YELLOW)
        fillGlass(this)
        setSlot(0, GuiElement(named(definition.egg, definition.displayName, ChatFormatting.WHITE)) { _, _, _, _ -> })
        val stats = listOf(
            Triple(Items.EXPERIENCE_BOTTLE, "等级 ${definition.rank}", definition.rank),
            Triple(Items.APPLE, "生命 ${definition.health.toInt()}", null),
            Triple(Items.IRON_CHESTPLATE, "盔甲 ${definition.armor.toInt()}", definition.armor.toInt().takeIf { it > 0 }),
            Triple(Items.IRON_SWORD, "对玩家攻击 ${definition.playerAttack.toInt()}", definition.playerAttack.toInt().takeIf { it > 0 }),
            Triple(Items.STONE_BRICKS, "建筑破坏力 ${definition.buildingDamage}", definition.buildingDamage.takeIf { it > 0 }),
            Triple(Items.CLOCK, "拆建筑间隔 ${MatchRules.secondsText(definition.buildingIntervalTicks)} 秒", wholeSeconds(definition.buildingIntervalTicks)),
            Triple(Items.ENDER_EYE, "攻击距离 ${definition.attackRange.toInt()}", definition.attackRange.toInt().takeIf { it > 0 }),
            Triple(Items.BOW, "弓射程 ${definition.bowRange?.toInt()?.toString() ?: "—"}", definition.bowRange?.toInt()?.takeIf { it > 0 }),
            Triple(Items.REDSTONE, "基础产出间隔 ${MatchRules.secondsText(definition.baseProductionTicks)} 秒", wholeSeconds(definition.baseProductionTicks)),
        )
        for ((index, stat) in stats.withIndex()) {
            val (item, name, count) = stat
            setSlot(9 + index, GuiElement(named(item, name, ChatFormatting.WHITE, count = count)) { _, _, _, _ -> })
        }
        var next = 18
        if (definition.cost.isEmpty()) {
            setSlot(next, GuiElement(named(Items.EMERALD, "购买消耗", ChatFormatting.WHITE, listOf("无"))) { _, _, _, _ -> })
            next++
        } else {
            for (part in definition.cost) {
                setSlot(
                    next,
                    GuiElement(
                        named(part.item, "购买消耗 ${part.name} ×${part.count}", ChatFormatting.WHITE, count = part.count),
                    ) { _, _, _, _ -> },
                )
                next++
            }
        }
        setSlot(
            next++,
            GuiElement(
                named(
                    if (definition.source == MonsterSource.TOWER) Items.WHITE_BANNER else Items.CRAFTER,
                    "来源",
                    ChatFormatting.WHITE,
                    listOf(if (definition.source == MonsterSource.TOWER) "掠夺者哨塔" else "兵营"),
                ),
            ) { _, _, _, _ -> },
        )
        setSlot(
            next++,
            GuiElement(
                named(Items.TARGET, "索敌", ChatFormatting.WHITE, seekLines(definition)),
            ) { _, _, _, _ -> },
        )
        val notes = supplement(definition)
        if (notes.isNotEmpty()) {
            setSlot(
                next,
                GuiElement(named(Items.BOOK, "补充说明", ChatFormatting.WHITE, notes)) { _, _, _, _ -> },
            )
        }
        setSlot(
            45,
            GuiElement(named(Items.OAK_DOOR, "返回", ChatFormatting.WHITE)) { _, click, _, _ ->
                if (!click.isLeft || click.shift) return@GuiElement
                close()
                MonsterHomeBoard(viewer).open()
            },
        )
    }
}

private fun seekLines(definition: MonsterDefinition): List<String> {
    val default = listOf(
        "默认索敌：受到伤害且当前追击半径内有敌方玩家或敌方唤灵时进入反击。",
        "否则在追击半径内按顺序索敌：① 敌方建筑核心；② 最近的敌方唤灵；③ 最近的敌方玩家；④ 都没有则改锁全图最近的敌方建筑核心；⑤ 全图没有敌方核心则停下。",
        "追击半径上限 16 格。不攻击我方玩家与我方建筑。创造和旁观模式的玩家不作为目标。",
    )
    return when {
        definition.miner -> listOf(
            "不走默认五步，不主动拆建筑或追击。只挖矿、回本队基地。",
            "受到伤害且追击半径内有敌方玩家或敌方唤灵时按现有反击近战，反击不拆建筑，结束后回到挖矿。",
        )
        definition.buildingsOnly && definition.source == MonsterSource.TOWER -> listOf(
            "掠夺模式下只索敌敌方建筑，不追玩家和唤灵，也不因受伤反击。",
            "由所属掠夺者哨塔放出且该塔处于固守时，改为固守索敌：以这座哨塔的固守地点为锚点，没改过时是核心，范围 32 格。",
            "无目标时在范围内巡逻，不主动拆敌方建筑，也不去全图锁敌方核心。有目标时只比较范围内，优先正在破坏己方建筑的敌人，否则最近的敌方唤灵，再否则最近的敌方玩家；不追出范围。",
            "手持刷怪蛋放下的劫掠兽不吃固守，仍为掠夺下的只拆建筑。",
        )
        definition.buildingsOnly -> listOf(
            "只索敌敌方建筑，不追玩家和唤灵，也不因受伤反击。",
        )
        definition.ignoresDefensive -> default + "不把城墙当作攻击目标。"
        definition.source == MonsterSource.TOWER -> default + listOf(
            "掠夺模式下为默认索敌。由所属掠夺者哨塔放出且该塔处于固守时，改为固守索敌：以这座哨塔的固守地点为锚点，没改过时是核心，范围 32 格。",
            "无目标时在范围内巡逻，不主动拆敌方建筑，也不去全图锁敌方核心。有目标时只比较范围内，优先正在破坏己方建筑的敌人，否则最近的敌方唤灵，再否则最近的敌方玩家；不追出范围。",
            "手持刷怪蛋放下的掠夺者系不吃固守，仍为默认索敌。",
        )
        else -> default
    }
}

private fun supplement(definition: MonsterDefinition): List<String> {
    val lines = ArrayList<String>()
    if (definition.explodes) {
        lines.add("爆炸中心伤害 ${definition.playerAttack.toInt()}，威力 ${MatchRules.CREEPER_POWER.toInt()}，进入 ${definition.attackRange.toInt()} 格后引信 1.5 秒。")
        lines.add("生命归零后会原地蓄力，引信结束后爆炸。")
    }
    if (definition.calmAboveHalf) {
        lines.add("生命高于一半时不索敌玩家和唤灵，只拆建筑。降到一半及以下才索敌玩家和唤灵。")
    }
    if (definition.miner) {
        lines.add("挖满 ${MatchRules.MINER_CARGO} 块矿后回本队基地。铁镐加成为对玩家攻击的一部分。交矿卡住时会走近一点，还卡住就换个位置。")
    }
    if (definition.sword) lines.add("手持铁剑近战，挥击间隔与僵尸相同，伤害不含铁剑的原版攻击。")
    if (definition.wornArmor && definition.sword) lines.add("一套铁甲提供护甲 ${definition.armor.toInt()}，铁甲和铁剑击杀不掉。")
    if (definition.requiresWorkshop) lines.add("本队要有已放下且仍存在的晶核工坊才能开始制作。已经放出来的不受工坊被拆影响。")
    if (definition.movementSpeedScale != 1.0) lines.add("基础移速是普通尸壳原速的 ${(definition.movementSpeedScale * 100).toInt()}%。")
    if (definition.copperGear) lines.add("铜甲和铜剑不掉，对玩家伤害不含铜剑的原版攻击。")
    if (definition.batch > 1) lines.add("一轮生产放出 ${definition.batch} 只。")
    if (definition.fangs) {
        lines.add("对玩家以法术尖牙结算伤害，并召唤恼鬼。尖牙距离 ${MatchRules.EVOKER_FANG_RANGE.toInt()} 格内有敌人时逃跑并施法：${MatchRules.EVOKER_FANG_MELEE.toInt()} 格内在身边放一圈，${MatchRules.EVOKER_FANG_LINE.toInt()} 格内向目标放一条直线，再远则在目标脚下放一个。没有敌人时走向最近的敌方建筑，走到 ${definition.attackRange.toInt()} 格后召唤恼鬼。恼鬼单独注册，按敌方建筑、敌方唤灵、敌方玩家的顺序自己索敌。活动原点跟着唤魔者，空闲时在唤魔者附近活动。")
    }
    if (definition.id == Monsters.ILLUSIONER.id) lines.add("失明法术与幻象分身按原版幻术师。")
    if (definition.axe) lines.add("手持铁斧近战。")
    return lines
}

private fun wholeSeconds(ticks: Int): Int? =
    if (ticks > 0 && ticks % MatchRules.TICKS_PER_SECOND == 0) ticks / MatchRules.TICKS_PER_SECOND else null
