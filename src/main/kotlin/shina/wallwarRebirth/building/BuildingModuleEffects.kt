package shina.wallwarRebirth.building

object BuildingModuleEffects {
    fun lines(definition: BuildingDefinition, slot: ModuleSlot): List<String> = when (definition.id) {
        Buildings.BASE.id -> when (slot) {
            ModuleSlot.PRIMARY -> listOf("所有矿物的产出速度增加40%")
            ModuleSlot.SECONDARY -> listOf("每 5 秒回复 1 点生命。")
            ModuleSlot.DIFFUSION -> listOf("解锁移动终端的购买功能。")
        }
        Buildings.MINERAL_REACTOR.id -> when (slot) {
            ModuleSlot.PRIMARY -> listOf("所有矿物的产出间隔从 7 秒变为 5 秒。")
            ModuleSlot.SECONDARY -> listOf("每 5 秒回复这座反应堆 1 点生命。满血时跳过。")
            ModuleSlot.DIFFUSION -> emptyList()
        }
        Buildings.WALL.id -> when (slot) {
            ModuleSlot.PRIMARY -> listOf("未被攻击时每 2 秒回复 1 点生命。")
            ModuleSlot.SECONDARY -> listOf("被攻击时反伤 4 点。")
            ModuleSlot.DIFFUSION -> listOf("32 格内的其他友方城墙，视为装上了这座城墙已有的强化模块（无法叠加且本身不外传）。")
        }
        Buildings.TOWER.id -> when (slot) {
            ModuleSlot.PRIMARY -> listOf("一支箭最多穿透 3 个敌对目标。")
            ModuleSlot.SECONDARY -> listOf("射击间隔减少 40%，变为 1.2 秒。")
            ModuleSlot.DIFFUSION -> listOf("一次射出三支箭。")
        }
        Buildings.BARRACKS.id -> when (slot) {
            ModuleSlot.PRIMARY -> listOf("放出的唤灵自带速度 I，直到消失。")
            ModuleSlot.SECONDARY -> listOf("选定只数后，凑满再一起放出。选择 1 到 5。骷髅死士按 3/6/9/12/15 放出。没选时按 1 轮。")
            ModuleSlot.DIFFUSION -> listOf("给同队玩家发放指挥物品。兵营拆掉后收回。")
        }
        Buildings.PILLAGER.id -> when (slot) {
            ModuleSlot.PRIMARY -> listOf("这座哨塔之后生成的唤灵拥有 5 点基础护甲。已经在场的不变。")
            ModuleSlot.SECONDARY -> listOf("附近 32 格内的我方唤灵获得抗性提升 I。离开后消失。")
            ModuleSlot.DIFFUSION -> listOf("给同队玩家发放掠夺之旗。右键方块，可把这座哨塔的固守地点改到那里。")
        }
        Buildings.TESLA.id -> when (slot) {
            ModuleSlot.PRIMARY -> listOf("攻击范围扩大 50%。")
            ModuleSlot.SECONDARY -> listOf("命中给予的挖掘疲劳和虚弱都从 I 提升到 II。")
            ModuleSlot.DIFFUSION -> listOf("每次攻击时，对射程内已经有蓄风的敌方唤灵再造成一次本次的闪电伤害。")
        }
        Buildings.QUAKE.id -> when (slot) {
            ModuleSlot.PRIMARY -> listOf("溅射半径从 3 格变为 4.5 格。")
            ModuleSlot.SECONDARY -> listOf("易伤每次改为 +2，数值上限 60。层数上限仍是 30。")
            ModuleSlot.DIFFUSION -> listOf("自身 24 格内没有敌人时，把这个距离内的同队晶震装置当作射程延伸，攻击那些塔范围内的敌人。占用本塔的攻击间隔，不改那座塔自己的间隔。")
        }
        Buildings.CANNON.id -> when (slot) {
            ModuleSlot.PRIMARY -> listOf("爆炸半径扩大 50%。威力半径 6 格，生物伤害在 12 格归零。")
            ModuleSlot.SECONDARY -> listOf("发射间隔减少 30%，变为 7 秒。")
            ModuleSlot.DIFFUSION -> listOf("本队生存玩家潜行右键进入，沿视线发射载人 TNT。撞上实心方块、敌方玩家或敌方唤灵时立刻爆炸，穿过友方不爆，满 5 秒仍会爆炸。爆炸伤害提升 25%，并拆掉威力半径内的非建筑木板，掉落在地。")
        }
        else -> emptyList()
    }
}
