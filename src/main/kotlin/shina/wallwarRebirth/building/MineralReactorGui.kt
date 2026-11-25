package shina.wallwarRebirth.building

import eu.pb4.sgui.api.ClickType
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
import shina.wallwarRebirth.util.hideTooltip

class MineralReactorGui(
    private val viewer: ServerPlayer,
    private val site: SiteCoreBlockEntity,
    private val definition: BuildingDefinition,
) : SimpleGui(MenuType.GENERIC_9x3, viewer, false) {
    init {
        title = Component.literal(definition.displayName)
        paint()
    }

    override fun onTick() {
        super.onTick()
        val world = site.level
        if (world == null || world.getBlockEntity(site.blockPos) !== site || site.health <= 0) {
            close()
            return
        }
        val refusal = MineralReactorCapture.openRefusal(viewer)
        if (refusal != null) {
            close()
            return
        }
        paint()
    }

    private fun paint() {
        val glass = ItemStack(Items.GRAY_STAINED_GLASS_PANE).hideTooltip()
        for (slot in 0 until size) setSlot(slot, GuiElement(glass, GuiElementInterface.EMPTY_CALLBACK))
        val ally = site.isAlly(viewer)
        if (MineralReactorCapture.canOccupy(site, viewer)) {
            setSlot(0, GuiElement(occupyIcon()) { _, click, _, _ ->
                if (!click.isLeft || click.shift) return@GuiElement
                val message = MineralReactorCapture.tryStart(site, viewer)
                viewer.sendSystemMessage(Component.literal(message))
                if (message == "已占领矿物反应堆") paint()
            })
        }
        setSlot(4, GuiElement(infoIcon(), GuiElementInterface.EMPTY_CALLBACK))
        if (ally) {
            setSlot(8, GuiElement(demolishIcon()) { _, click, _, _ ->
                if (!click.isLeft || click.shift) return@GuiElement
                if (site.demolish(viewer)) close()
            })
            setSlot(18, GuiElement(moduleIcon(ModuleSlot.PRIMARY, "强化槽")) { _, click, _, _ ->
                handleModule(ModuleSlot.PRIMARY, click)
            })
            setSlot(19, GuiElement(moduleIcon(ModuleSlot.SECONDARY, "强化槽 II")) { _, click, _, _ ->
                handleModule(ModuleSlot.SECONDARY, click)
            })
        }
        definition.minerals().forEachIndexed { index, mineral ->
            if (index >= 5) return@forEachIndexed
            setSlot(9 + index, GuiElement(mineralIcon(mineral), GuiElementInterface.EMPTY_CALLBACK))
        }
    }

    private fun occupyIcon(): ItemStack {
        val builder = ItemStackBuilder.of(Items.EMERALD)
            .setName(line("占领", ChatFormatting.GREEN))
            .appendLore(line("让最近的无职业村民走过来，到达后村民消失"))
        if (site.team.isEmpty()) builder.appendLore(line("当前无主"))
        else builder.appendLore(line("当前属于：${site.team}"))
        return builder.build()
    }

    private fun infoIcon(): ItemStack {
        val builder = ItemStackBuilder.of(definition.icon)
            .setName(line("${definition.displayName}（三级）", ChatFormatting.YELLOW))
        builder.appendLore(line("类别：${definition.categoryText()}"))
        builder.appendLore(line("等级：${site.buildingLevel}"))
        builder.appendLore(line("生命：${site.health}/${definition.maxHealth(site.buildingLevel)}"))
        builder.appendLore(line("队伍：${site.team.ifEmpty { "无" }}"))
        if (definition.summary.isNotEmpty()) builder.appendLore(line(definition.summary))
        if (site.team.isEmpty()) builder.appendLore(line("未占领，不产出"))
        else builder.appendLore(line("占领后在战斗阶段产出，进入本队团队仓库"))
        return builder.build()
    }

    private fun mineralIcon(mineral: MineralOutput): ItemStack {
        val occupied = site.team.isNotEmpty()
        val interval = site.intervalOf(mineral)
        val builder = ItemStackBuilder.of(mineral.item)
            .setName(line(mineral.name, if (occupied) ChatFormatting.YELLOW else ChatFormatting.GRAY))
        when {
            !occupied -> builder.appendLore(line("未占领，不产出"))
            interval == null -> builder.appendLore(line("不生成"))
            else -> builder.appendLore(line(mineral.describe(interval)))
        }
        return builder.build()
    }

    private fun demolishIcon(): ItemStack =
        ItemStackBuilder.of(Items.TNT)
            .setName(line("拆除", ChatFormatting.RED))
            .appendLore(line("打掉后本局消失，槽里的模块掉在核心处"))
            .build()

    private fun moduleIcon(slot: ModuleSlot, label: String): ItemStack {
        val filled = site.moduleStack(slot)
        val builder = if (filled.isEmpty) {
            ItemStackBuilder.of(emptyReactorPane(slot))
        } else {
            ItemStackBuilder.of(filled)
        }
        builder.setName(line(label, ChatFormatting.YELLOW))
        if (filled.isEmpty) builder.appendLore(line("左键放入${reactorModuleName(slot)}"))
        for (text in BuildingModuleEffects.lines(definition, slot)) builder.appendLore(line(text))
        return builder.build()
    }

    private fun handleModule(slot: ModuleSlot, click: ClickType) {
        if (!site.isAlly(viewer)) {
            viewer.sendSystemMessage(Component.literal("只有占领方可以装模块"))
            return
        }
        val cursor = viewer.containerMenu.carried
        if (click.shift || click.isRight) {
            if (!site.moduleStack(slot).isEmpty) viewer.sendSystemMessage(Component.literal("这个模块不能拆卸"))
            return
        }
        if (!click.isLeft) return
        if (!cursor.isEmpty && !site.moduleStack(slot).isEmpty) {
            viewer.sendSystemMessage(Component.literal("这个模块不能拆卸"))
            return
        }
        if (cursor.isEmpty) return
        val refusal = site.moduleRefusal(slot, cursor)
        if (refusal != null) {
            viewer.sendSystemMessage(Component.literal(refusal))
            return
        }
        val previous = site.exchangeModule(slot, cursor)
        viewer.containerMenu.carried = previous
        viewer.containerMenu.broadcastChanges()
        paint()
    }

    private fun line(text: String, color: ChatFormatting = ChatFormatting.GRAY): Component =
        Component.literal(text).withStyle { style -> style.withColor(color).withItalic(false) }
}

private fun emptyReactorPane(slot: ModuleSlot) = when (slot) {
    ModuleSlot.PRIMARY -> Items.LIGHT_BLUE_STAINED_GLASS_PANE
    ModuleSlot.SECONDARY -> Items.BLUE_STAINED_GLASS_PANE
    ModuleSlot.DIFFUSION -> Items.PURPLE_STAINED_GLASS_PANE
}

private fun reactorModuleName(slot: ModuleSlot) = when (slot) {
    ModuleSlot.PRIMARY -> "强化模块"
    ModuleSlot.SECONDARY -> "强化模块 2"
    ModuleSlot.DIFFUSION -> "扩散模块"
}
