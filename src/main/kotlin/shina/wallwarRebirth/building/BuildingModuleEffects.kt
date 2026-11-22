package shina.wallwarRebirth.building

import shina.wallwarRebirth.rules.MatchRules

object BuildingModuleEffects {
    fun lines(definition: BuildingDefinition, slot: ModuleSlot): List<String> = when (definition.id) {
        Buildings.BASE.id, Buildings.MINERAL_REACTOR.id -> base(slot)
        Buildings.WALL.id -> when (slot) {
            ModuleSlot.PRIMARY -> listOf(
                "没在挨打时，每 ${MatchRules.WALL_HEAL_TICKS / MatchRules.TICKS_PER_SECOND} 秒回复自己 ${MatchRules.WALL_HEAL_AMOUNT} 点。",
            )
            ModuleSlot.SECONDARY -> listOf("被攻击时按原版荆棘反伤，固定 ${MatchRules.WALL_THORNS_DAMAGE} 点。")
            ModuleSlot.DIFFUSION -> listOf(
                "${MatchRules.DIFFUSION_RANGE.toInt()} 格内的其他友方城墙视为装上这座城墙槽里已有的强化模块，已装的不叠加，扩散本身不外传。",
            )
        }
        Buildings.TOWER.id -> when (slot) {
            ModuleSlot.PRIMARY -> listOf("一支箭最多命中 ${MatchRules.TOWER_PIERCE_HITS} 个敌对目标，命中后继续飞。")
            ModuleSlot.SECONDARY -> listOf("射击间隔减少 40%，变为 ${towerIntervals()} 秒。")
            ModuleSlot.DIFFUSION -> listOf("一次射出三支箭，左右各偏 ${MatchRules.TOWER_SPREAD_DEGREES.toInt()} 度。")
        }
        Buildings.BARRACKS.id -> when (slot) {
            ModuleSlot.PRIMARY -> listOf("放出的怪物自带速度 I，直到消失。")
            ModuleSlot.SECONDARY -> listOf(
                "凑满选定只数乘以每轮只数再一起放出。选择仍是 ${MatchRules.BARRACKS_RELEASE_MIN} 到 ${MatchRules.BARRACKS_RELEASE_MAX}。",
                "骷髅死士按 3、6、9、12、15 放出。没选时按 1 轮。",
            )
            ModuleSlot.DIFFUSION -> listOf("装上后给同队玩家发放指挥物品。兵营拆掉后收回。")
        }
        Buildings.PILLAGER.id -> when (slot) {
            ModuleSlot.PRIMARY -> listOf(
                "这座哨塔之后生成的唤灵拥有 ${MatchRules.PILLAGER_REINFORCE_ARMOR.toInt()} 点基础护甲。已经在场的不变。",
            )
            ModuleSlot.SECONDARY -> listOf(
                "附近 ${MatchRules.DIFFUSION_RANGE.toInt()} 格内的我方唤灵获得抗性提升 I，离开后消失。",
            )
            ModuleSlot.DIFFUSION -> listOf("给同队玩家发放掠夺之旗。右键方块可把这座哨塔的固守地点改到那里。")
        }
        Buildings.TESLA.id -> when (slot) {
            ModuleSlot.PRIMARY -> listOf(
                "攻击范围扩大 50%，${MatchRules.TESLA_RANGE.toInt()} 格变为 ${(MatchRules.TESLA_RANGE * MatchRules.TESLA_RANGE_SCALE).toInt()} 格。",
            )
            ModuleSlot.SECONDARY -> listOf(
                "命中给予的挖掘疲劳和虚弱都从 I 提升到 II。挖掘疲劳仍持续 ${MatchRules.TESLA_HIT_FATIGUE_TICKS / MatchRules.TICKS_PER_SECOND} 秒，虚弱仍持续 ${MatchRules.TESLA_HIT_WEAKNESS_TICKS / MatchRules.TICKS_PER_SECOND} 秒。",
            )
            ModuleSlot.DIFFUSION -> listOf("每次攻击时，对射程内已经有蓄风的敌方唤灵再造成一次本次的闪电伤害。")
        }
        Buildings.QUAKE.id -> when (slot) {
            ModuleSlot.PRIMARY -> listOf("溅射半径从 ${MatchRules.QUAKE_SPLASH} 格变为 ${MatchRules.QUAKE_SPLASH_REINFORCED} 格。")
            ModuleSlot.SECONDARY -> listOf(
                "易伤每次增加与上限变为 ${MatchRules.QUAKE_STACK_INCREMENT_II} / ${MatchRules.QUAKE_STACK_CAP_II}，层数上限仍是 ${MatchRules.QUAKE_STACK_MAX}。",
            )
            ModuleSlot.DIFFUSION -> listOf(
                "自身 ${MatchRules.QUAKE_RANGE.toInt()} 格内没有敌人时，把这个距离内的同队晶震装置当作射程延伸，攻击那些塔范围内的敌人。",
                "粒子只从本塔连到那座塔，不连到敌人。占用本塔的攻击间隔，不改那座塔自己的间隔。",
            )
        }
        Buildings.CANNON.id -> when (slot) {
            ModuleSlot.PRIMARY -> listOf(
                "爆炸半径扩大 50%。威力半径 ${MatchRules.CANNON_POWER_WIDE.toInt()} 格，生物伤害在 ${MatchRules.CANNON_FALLOFF_WIDE.toInt()} 格归零。",
            )
            ModuleSlot.SECONDARY -> listOf(
                "发射间隔减少 30%，变为 ${MatchRules.secondsText(MatchRules.CANNON_INTERVAL * 7 / 10)} 秒。",
            )
            ModuleSlot.DIFFUSION -> listOf(
                "本队生存玩家潜行右键进入。有目标时蓄力完成后沿视线发射载人 TNT，碰到方块不会立即爆炸，固定 ${MatchRules.CANNON_MANNED_TICKS / MatchRules.TICKS_PER_SECOND} 秒后爆炸，爆炸伤害提升 25%。",
            )
        }
        else -> emptyList()
    }

    private fun base(slot: ModuleSlot): List<String> = when (slot) {
        ModuleSlot.PRIMARY -> listOf(
            "装上后，所有矿物的产出间隔从 ${MatchRules.secondsText(MatchRules.BASE_IRON_TICKS)} 秒变为 ${MatchRules.secondsText(MatchRules.BASE_BOOSTED_TICKS)} 秒。",
        )
        ModuleSlot.SECONDARY -> listOf(
            "每 ${MatchRules.BASE_REGEN_TICKS / MatchRules.TICKS_PER_SECOND} 秒回复这座基地 ${MatchRules.BASE_REGEN_AMOUNT} 点生命，满血跳过。不改变矿物。",
        )
        ModuleSlot.DIFFUSION -> listOf("装上后，本队局内终端可使用建筑、武器和护甲栏，用法与右键己方基地核心相同。")
    }

    private fun towerIntervals(): String = MatchRules.TOWER_INTERVAL.map { ticks ->
        MatchRules.secondsText(ticks * 3 / 5)
    }.distinct().joinToString(" / ")
}
