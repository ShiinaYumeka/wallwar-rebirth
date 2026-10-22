package shina.wallwarRebirth.item.core

enum class PolymerInteractionType(
    val blockInteraction: Boolean,
    val entityInteraction: Boolean,
    val itemInteraction: Boolean,
    val blockInteractionPlaySound: Boolean,
    val itemInteractionPlaySound: Boolean,
) {
    /** 经典 polymer 行为：仅物品交互为 true，其余全部 false */
    CLASSIC(false, false, true, false, false),

    /** 禁用全部 polymer 交互干预，适合不需要特殊交互处理的物品 */
    DEFAULT(false, false, false, false, false),

    /** 方块交互模式：方块交互及其音效异常启用 */
    BLOCK(true, false, false, false, false),

    /** 全部交互由服务端处理，全部音效异常启用，适合需要完整服务端控制的物品 */
    SERVER(true, true, true, true, true),
}
