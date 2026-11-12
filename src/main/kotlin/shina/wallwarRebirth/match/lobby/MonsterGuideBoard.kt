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
        Monsters.DEATH_KNIGHT,
        Monsters.CREEPER,
        Monsters.WARRIOR,
        Monsters.GOLEM,
        Monsters.PILLAGER,
        Monsters.VINDICATOR,
        Monsters.EVOKER,
        Monsters.ILLUSIONER,
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
            Triple(Items.EXPERIENCE_BOTTLE, "等级", definition.rank.toString()),
            Triple(Items.APPLE, "生命", definition.health.toInt().toString()),
            Triple(Items.IRON_CHESTPLATE, "盔甲", definition.armor.toInt().toString()),
            Triple(Items.IRON_SWORD, "对玩家攻击", definition.playerAttack.toInt().toString()),
            Triple(Items.STONE_BRICKS, "建筑破坏力", definition.buildingDamage.toString()),
            Triple(Items.CLOCK, "拆建筑间隔", MatchRules.secondsText(definition.buildingIntervalTicks) + " 秒"),
            Triple(Items.ENDER_EYE, "攻击距离", definition.attackRange.toInt().toString()),
            Triple(Items.BOW, "弓射程", definition.bowRange?.toInt()?.toString() ?: "—"),
            Triple(Items.REDSTONE, "基础产出间隔", MatchRules.secondsText(definition.baseProductionTicks) + " 秒"),
        )
        for ((index, stat) in stats.withIndex()) {
            val (item, label, value) = stat
            setSlot(9 + index, GuiElement(named(item, "$label $value", ChatFormatting.WHITE)) { _, _, _, _ -> })
        }
        setSlot(
            18,
            GuiElement(
                named(Items.EMERALD, "购买消耗", ChatFormatting.WHITE, listOf(definition.costText())),
            ) { _, _, _, _ -> },
        )
        setSlot(
            19,
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
            20,
            GuiElement(
                named(Items.TARGET, "索敌", ChatFormatting.WHITE, seekLines(definition)),
            ) { _, _, _, _ -> },
        )
        val notes = supplement(definition)
        if (notes.isNotEmpty()) {
            setSlot(
                21,
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
            "由所属掠夺者哨塔放出且该塔处于固守时，改为固守索敌：以所属哨塔核心为锚点，范围 16 格。",
            "无目标时在范围内巡逻，不主动拆敌方建筑，也不去全图锁敌方核心。有目标时只比较范围内，优先正在破坏己方建筑的敌人，否则最近的敌方唤灵，再否则最近的敌方玩家；不追出范围。",
            "手持刷怪蛋放下的劫掠兽不吃固守，仍为掠夺下的只拆建筑。",
        )
        definition.buildingsOnly -> listOf(
            "只索敌敌方建筑，不追玩家和唤灵，也不因受伤反击。",
        )
        definition.ignoresDefensive -> default + "不把城墙当作攻击目标。"
        definition.source == MonsterSource.TOWER -> default + listOf(
            "掠夺模式下为默认索敌。由所属掠夺者哨塔放出且该塔处于固守时，改为固守索敌：以所属哨塔核心为锚点，范围 16 格。",
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
    }
    if (definition.miner) {
        lines.add("挖满 ${MatchRules.MINER_CARGO} 块矿后回本队基地。铁镐加成为对玩家攻击的一部分。")
    }
    if (definition.sword) lines.add("手持铁剑近战，挥击间隔与僵尸相同，伤害不含铁剑的原版攻击。")
    if (definition.copperGear) lines.add("铜甲和铜剑不掉，对玩家伤害不含铜剑的原版攻击。")
    if (definition.batch > 1) lines.add("一轮生产放出 ${definition.batch} 只。")
    if (definition.fangs) lines.add("对玩家以法术尖牙结算伤害，并按原版唤魔者召唤恼鬼。固守下召唤的恼鬼跟随召唤者的固守索敌。")
    if (definition.id == Monsters.ILLUSIONER.id) lines.add("失明法术与幻象分身按原版幻术师。")
    if (definition.axe) lines.add("手持铁斧近战。")
    return lines
}
