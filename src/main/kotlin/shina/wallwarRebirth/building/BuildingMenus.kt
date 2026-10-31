package shina.wallwarRebirth.building

import eu.pb4.sgui.api.ClickType
import eu.pb4.sgui.api.elements.GuiElement
import eu.pb4.sgui.api.elements.GuiElementInterface
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import shina.wallwarRebirth.gui.ItemStackBuilder
import shina.wallwarRebirth.match.BaseShop
import shina.wallwarRebirth.match.Match
import shina.wallwarRebirth.monster.MonsterDefinition
import shina.wallwarRebirth.monster.Monsters
import shina.wallwarRebirth.shop.ShopSession
import shina.wallwarRebirth.shop.element.BaseShopItem
import shina.wallwarRebirth.shop.element.ShopItemWithIndex
import shina.wallwarRebirth.shop.element.ShopLayer
import shina.wallwarRebirth.shop.element.view.ShopItemViewModel
import shina.wallwarRebirth.shop.gui.ShopGui
import shina.wallwarRebirth.util.hideTooltip

object BuildingMenus {
    private val pickerSlots = intArrayOf(1, 3, 5, 7)

    fun open(player: ServerPlayer, site: SiteCoreBlockEntity) {
        val definition = Buildings.byId(site.buildingId)
        if (definition == null) {
            val world = site.level
            if (world != null && Match.isArena(world)) {
                player.sendSystemMessage(Component.literal("对局里要购买建筑核心再放置"))
                return
            }
            openPicker(player, site, replace = false)
        } else {
            BuildingPanelGui(player, site, definition).open()
        }
    }

    fun openPicker(player: ServerPlayer, site: SiteCoreBlockEntity, replace: Boolean) {
        val layer = ShopLayer(
            1,
            Buildings.ofCategory(BuildingCategory.NORMAL).mapIndexed { index, definition ->
                SelectBuildingItem(pickerSlots.getOrElse(index) { index }, site, definition, replace)
            },
        )
        BuildingShopGui(ShopSession(), player, listOf(layer), "选择建筑").open()
    }
}

class BuildingPanelGui(
    private val viewer: ServerPlayer,
    private val site: SiteCoreBlockEntity,
    private val definition: BuildingDefinition,
) : SimpleGui(
    if (definition.showsContainer || definition.id == Buildings.BARRACKS.id) MenuType.GENERIC_9x6 else MenuType.GENERIC_9x2,
    viewer,
    false,
) {
    private val barracks = definition.id == Buildings.BARRACKS.id

    init {
        title = Component.literal(definition.displayName)
        val glass = ItemStack(Items.GRAY_STAINED_GLASS_PANE).hideTooltip()
        val decorated = when {
            barracks -> size
            definition.showsContainer -> STORAGE_ORIGIN
            else -> size
        }
        for (slot in 0 until decorated) {
            setSlot(slot, GuiElement(glass, GuiElementInterface.EMPTY_CALLBACK))
        }
        if (definition.showsContainer) {
            for (slot in 0 until site.storage.containerSize) {
                setSlotRedirect(STORAGE_ORIGIN + slot, Slot(site.storage, slot, 0, 0))
            }
        }
        if (barracks) {
            for (row in 0 until 4) {
                for (column in 0 until 5) {
                    val guiSlot = STORAGE_ORIGIN + row * 9 + column
                    setSlotRedirect(guiSlot, FilterSlot(site.barracksMaterials, row * 5 + column) { BarracksSlots.acceptsMaterial(it) })
                }
                for (column in 6 until 9) {
                    val guiSlot = STORAGE_ORIGIN + row * 9 + column
                    setSlotRedirect(guiSlot, FilterSlot(site.barracksEggs, row * 3 + (column - 6)) { BarracksSlots.acceptsEgg(it) })
                }
            }
        }
        paintButtons()
    }

    override fun onTick() {
        super.onTick()
        val world = site.level
        if (world == null || world.getBlockEntity(site.blockPos) !== site) {
            close()
            return
        }
        paintButtons()
    }

    private fun paintButtons() {
        if (definition.category == BuildingCategory.CORE) {
            setSlot(UPGRADE_SLOT, GuiElement(shopIcon()) { _, clickType, _, _ ->
                if (!clickType.isLeft || clickType.shift) return@GuiElement
                close()
                viewer.level().server.execute { BaseShop.open(viewer, site) }
            })
            setSlot(INFO_SLOT, GuiElement(infoIcon(), GuiElementInterface.EMPTY_CALLBACK))
            return
        }
        setSlot(UPGRADE_SLOT, GuiElement(upgradeIcon()) { _, clickType, _, _ ->
            if (!clickType.isLeft || clickType.shift) return@GuiElement
            close()
            viewer.level().server.execute { UpgradePanelGui(viewer, site, definition).open() }
        })
        setSlot(INFO_SLOT, GuiElement(infoIcon()) { _, clickType, _, _ ->
            if (!barracks || !clickType.isLeft || clickType.shift) return@GuiElement
            close()
            viewer.level().server.execute { BarracksPageGui(viewer, site).open() }
        })
        setSlot(DEMOLISH_SLOT, GuiElement(demolishIcon()) { _, clickType, _, _ ->
            if (!clickType.isLeft || clickType.shift) return@GuiElement
            if (site.demolish(viewer)) close()
        })
    }

    private fun shopIcon(): ItemStack =
        ItemStackBuilder.of(Items.EMERALD)
            .setName(line("商店", ChatFormatting.GREEN))
            .appendLore(line("装备、建筑核心、唤灵、强化"))
            .appendLore(line("只在战斗阶段可以购买"))
            .build()

    private fun infoIcon(): ItemStack {
        val builder = ItemStackBuilder.of(definition.icon)
            .setName(line("${definition.displayName}（${levelMark(site.buildingLevel)}级）", ChatFormatting.YELLOW))
        builder.appendLore(line("类别：${definition.category.label}"))
        builder.appendLore(line("等级：${site.buildingLevel}"))
        builder.appendLore(line("生命：${site.health}/${definition.maxHealth(site.buildingLevel)}"))
        builder.appendLore(line("队伍：${site.team.ifEmpty { "无" }}"))
        if (definition.summary.isNotEmpty()) builder.appendLore(line(definition.summary))
        val minerals = site.productionLines()
        if (minerals.isEmpty()) {
            for (rule in definition.production) {
                val color = if (site.buildingLevel >= rule.minLevel) ChatFormatting.GREEN else ChatFormatting.GRAY
                builder.appendLore(line(rule.describe(), color))
            }
        } else {
            for ((text, active) in minerals) {
                builder.appendLore(line(text, if (active) ChatFormatting.GREEN else ChatFormatting.GRAY))
            }
        }
        for (detail in definition.details) {
            val color = if (site.buildingLevel >= detail.minLevel) ChatFormatting.GREEN else ChatFormatting.GRAY
            builder.appendLore(line(detail.text, color))
        }
        if (barracks) {
            val monster = Monsters.byId(site.producedMonster) ?: Monsters.ZOMBIE
            builder.appendLore(line("当前生产：${monster.displayName}"))
            builder.appendLore(line(if (site.marching) "出怪：出征" else "出怪：休整"))
            builder.appendLore(line("当前间隔：${monster.productionTicks(site.buildingLevel) / 20} 秒"))
            builder.appendLore(line("左键打开生产页"))
        }
        return builder.build()
    }

    private fun upgradeIcon(): ItemStack {
        val builder = ItemStackBuilder.of(Items.EXPERIENCE_BOTTLE)
            .setName(line(if (site.buildingLevel >= definition.maxLevel) "已满级" else "升级", ChatFormatting.YELLOW))
        for (upgrade in definition.upgrades) {
            val color = if (upgrade.toLevel == site.buildingLevel + 1) ChatFormatting.YELLOW else ChatFormatting.GRAY
            builder.appendLore(line(upgrade.describe(), color))
        }
        return builder.build()
    }

    private fun demolishIcon(): ItemStack =
        ItemStackBuilder.of(Items.TNT)
            .setName(line("拆除", ChatFormatting.RED))
            .appendLore(line("不返还购买或升级花掉的材料，容器物品掉在地上"))
            .build()

    private fun line(text: String, color: ChatFormatting = ChatFormatting.GRAY): Component =
        Component.literal(text).withStyle { style -> style.withColor(color).withItalic(false) }

    companion object {
        const val UPGRADE_SLOT = 2
        const val INFO_SLOT = 4
        const val DEMOLISH_SLOT = 6
        const val STORAGE_ORIGIN = 18
    }
}

private class BuildingShopGui(
    session: ShopSession,
    player: ServerPlayer,
    layers: Collection<ShopLayer>,
    private val heading: String,
) : ShopGui(session, player, layers, 1) {
    init {
        title = Component.literal(heading)
    }
}

private class SelectBuildingItem(
    private val slot: Int,
    private val site: SiteCoreBlockEntity,
    private val definition: BuildingDefinition,
    private val replace: Boolean,
) : ShopItemWithIndex {
    override fun index(): Int = slot

    override fun getDefaultIcon(): ItemStack = icon()

    override fun getIcon(gui: ShopGui, viewModel: ShopItemViewModel): ItemStack = icon()

    override fun getBaseItem(gui: ShopGui, clickType: ClickType, viewModel: ShopItemViewModel): BaseShopItem =
        BaseShopItem(icon(), emptyMap(), emptyList(), false, 0, true, null)

    override fun updateView(gui: ShopGui, viewModel: ShopItemViewModel) {
        val world = site.level
        val present = world != null && world.getBlockEntity(site.blockPos) === site
        gui.session.valid = present && (replace || site.buildingId.isEmpty())
    }

    override fun executeBuying(
        item: BaseShopItem,
        gui: ShopGui,
        clickType: ClickType,
        materialSuffix: Component?,
    ) {
        val error = site.assign(definition, replace, gui.player.team?.name ?: "", gui.player.direction)
        if (error != null) {
            gui.player.reportBuilding(error)
            return
        }
        gui.player.sendSystemMessage(Component.literal("已选定${definition.displayName}"))
        gui.close()
    }

    private fun icon(): ItemStack {
        val builder = ItemStackBuilder.of(definition.icon)
            .setName(Component.literal(definition.displayName).withStyle { style ->
                style.withColor(ChatFormatting.YELLOW).withItalic(false)
            })
        builder.appendLore(Component.literal(definition.category.label).withStyle { style ->
            style.withColor(ChatFormatting.GRAY).withItalic(false)
        })
        return builder.build()
    }
}

private class UpgradePanelGui(
    private val viewer: ServerPlayer,
    private val site: SiteCoreBlockEntity,
    private val definition: BuildingDefinition,
) : SimpleGui(MenuType.GENERIC_9x1, viewer, false) {
    init {
        title = Component.literal("${definition.displayName}升级")
        paint()
    }

    override fun onTick() {
        super.onTick()
        val world = site.level
        if (world == null || world.getBlockEntity(site.blockPos) !== site) close()
    }

    private fun paint() {
        val glass = ItemStack(Items.GRAY_STAINED_GLASS_PANE).hideTooltip()
        for (slot in 0 until size) setSlot(slot, GuiElement(glass, GuiElementInterface.EMPTY_CALLBACK))
        setSlot(0, GuiElement(upgradeIcon()) { _, clickType, _, _ ->
            if (!clickType.isLeft || clickType.shift) return@GuiElement
            if (site.tryUpgrade(viewer)) paint()
        })
        paintModule(1, ModuleSlot.PRIMARY, "强化槽")
        paintModule(2, ModuleSlot.SECONDARY, "强化槽 II")
        setSlot(3, GuiElement(spreadIcon()) { _, _, _, _ ->
            viewer.sendSystemMessage(Component.literal("这个槽还没有效果"))
        })
    }

    private fun paintModule(index: Int, slot: ModuleSlot, label: String) {
        setSlot(index, GuiElement(moduleIcon(slot, label)) { _, clickType, _, _ ->
            handleModule(slot, clickType)
        })
    }

    private fun handleModule(slot: ModuleSlot, clickType: ClickType) {
        val cursor = viewer.containerMenu.carried
        if (clickType.shift || clickType.isRight) {
            val error = site.takeModule(slot, viewer)
            if (error != null) viewer.sendSystemMessage(Component.literal(error))
            paint()
            return
        }
        if (!clickType.isLeft || clickType.shift) return
        if (cursor.isEmpty) {
            if (!definition.acceptsModules) {
                viewer.sendSystemMessage(Component.literal("这座建筑不能装模块"))
                return
            }
            if (site.moduleStack(slot).isEmpty) return
            val choices = site.choicesFor(slot, definition)
            if (choices.isEmpty()) {
                val message = if (slot == ModuleSlot.SECONDARY) "只剩一种矿物，不能停止" else "没有可以强化的矿物"
                viewer.sendSystemMessage(Component.literal(message))
                return
            }
            close()
            viewer.level().server.execute { MineralPickerGui(viewer, site, definition, slot, choices).open() }
            return
        }
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

    private fun moduleIcon(slot: ModuleSlot, label: String): ItemStack {
        val filled = site.moduleStack(slot)
        val builder = if (filled.isEmpty) {
            ItemStackBuilder.of(if (slot == ModuleSlot.PRIMARY) Items.LIGHT_BLUE_STAINED_GLASS_PANE else Items.BLUE_STAINED_GLASS_PANE)
        } else {
            ItemStackBuilder.of(filled)
        }
        builder.setName(line(label, ChatFormatting.YELLOW))
        builder.appendLore(line("左键放入${if (slot == ModuleSlot.PRIMARY) "强化模块" else "强化模块 2"}"))
        builder.appendLore(line("放好后左键选择矿物"))
        builder.appendLore(line("Shift 或右键取出"))
        builder.appendLore(line("当前：${site.mineralLabel(slot)}"))
        return builder.build()
    }

    private fun spreadIcon(): ItemStack =
        ItemStackBuilder.of(Items.AMETHYST_SHARD)
            .setName(line("扩散槽", ChatFormatting.YELLOW))
            .appendLore(line("暂无效果"))
            .build()

    private fun upgradeIcon(): ItemStack {
        val builder = ItemStackBuilder.of(Items.EXPERIENCE_BOTTLE)
            .setName(line(if (site.buildingLevel >= definition.maxLevel) "已满级" else "升级", ChatFormatting.YELLOW))
        for (upgrade in definition.upgrades) {
            val color = if (upgrade.toLevel == site.buildingLevel + 1) ChatFormatting.YELLOW else ChatFormatting.GRAY
            builder.appendLore(line(upgrade.describe(), color))
        }
        return builder.build()
    }

    private fun line(text: String, color: ChatFormatting = ChatFormatting.GRAY): Component =
        Component.literal(text).withStyle { style -> style.withColor(color).withItalic(false) }
}

private class MineralPickerGui(
    private val viewer: ServerPlayer,
    private val site: SiteCoreBlockEntity,
    private val definition: BuildingDefinition,
    private val slot: ModuleSlot,
    private val choices: List<MineralOutput>,
) : SimpleGui(MenuType.GENERIC_9x1, viewer, false) {
    init {
        title = Component.literal(if (slot == ModuleSlot.SECONDARY) "选择停止生成的矿物" else "选择强化的矿物")
        val glass = ItemStack(Items.GRAY_STAINED_GLASS_PANE).hideTooltip()
        for (index in 0 until size) setSlot(index, GuiElement(glass, GuiElementInterface.EMPTY_CALLBACK))
        choices.forEachIndexed { index, mineral ->
            if (index >= size) return@forEachIndexed
            setSlot(index, GuiElement(choiceIcon(mineral)) { _, clickType, _, _ ->
                if (!clickType.isLeft || clickType.shift) return@GuiElement
                site.setMineral(slot, mineral.item)
                close()
                viewer.level().server.execute { UpgradePanelGui(viewer, site, definition).open() }
            })
        }
    }

    override fun onTick() {
        super.onTick()
        val world = site.level
        if (world == null || world.getBlockEntity(site.blockPos) !== site) close()
    }

    private fun choiceIcon(mineral: MineralOutput): ItemStack {
        val hint = if (slot == ModuleSlot.SECONDARY) "停止生成，其余矿物间隔减半" else "这种矿物的间隔减半"
        return ItemStackBuilder.of(mineral.item)
            .setName(Component.literal(mineral.name).withStyle { style ->
                style.withColor(ChatFormatting.YELLOW).withItalic(false)
            })
            .appendLore(Component.literal(hint).withStyle { style ->
                style.withColor(ChatFormatting.GRAY).withItalic(false)
            })
            .build()
    }
}

private class BarracksPageGui(
    private val viewer: ServerPlayer,
    private val site: SiteCoreBlockEntity,
) : SimpleGui(MenuType.GENERIC_9x3, viewer, false) {
    init {
        title = Component.literal("兵营生产")
        paint()
    }

    override fun onTick() {
        super.onTick()
        val world = site.level
        if (world == null || world.getBlockEntity(site.blockPos) !== site) {
            close()
            return
        }
        paint()
    }

    private fun paint() {
        val glass = ItemStack(Items.GRAY_STAINED_GLASS_PANE).hideTooltip()
        for (slot in 0 until size) setSlot(slot, GuiElement(glass, GuiElementInterface.EMPTY_CALLBACK))
        val monsters = Monsters.selectable()
        val slots = intArrayOf(2, 4, 6)
        monsters.forEachIndexed { index, monster ->
            val slot = slots.getOrElse(index) { return@forEachIndexed }
            setSlot(slot, GuiElement(monsterIcon(monster)) { _, clickType, _, _ ->
                if (!clickType.isLeft || clickType.shift) return@GuiElement
                if (site.buildingLevel < monster.minBarracksLevel) {
                    viewer.sendSystemMessage(Component.literal("兵营等级不够"))
                    return@GuiElement
                }
                if (site.producedMonster != monster.id) {
                    site.producedMonster = monster.id
                    site.actionAge = 0
                    site.setChanged()
                    viewer.sendSystemMessage(Component.literal("已改为生产${monster.displayName}，这一轮重新计时"))
                }
                paint()
            })
        }
        setSlot(18, GuiElement(backIcon()) { _, clickType, _, _ ->
            if (!clickType.isLeft || clickType.shift) return@GuiElement
            close()
            viewer.level().server.execute { BuildingPanelGui(viewer, site, Buildings.BARRACKS).open() }
        })
        setSlot(26, GuiElement(modeIcon()) { _, clickType, _, _ ->
            if (!clickType.isLeft || clickType.shift) return@GuiElement
            site.marching = !site.marching
            site.setChanged()
            val world = site.level as? ServerLevel
            if (site.marching && world != null && !BarracksWork.releaseNow(site, world) && BarracksWork.hasEggs(site)) {
                viewer.sendSystemMessage(Component.literal("附近没有可放置的位置，刷怪蛋会留到有位置再放"))
            }
            viewer.sendSystemMessage(Component.literal(if (site.marching) "已切换为出征" else "已切换为休整"))
            paint()
        })
    }

    private fun monsterIcon(monster: MonsterDefinition): ItemStack {
        val selected = site.producedMonster == monster.id
        val builder = ItemStackBuilder.of(monster.egg)
            .setName(line(monster.displayName, if (selected) ChatFormatting.GREEN else ChatFormatting.YELLOW))
        if (selected) builder.appendLore(line("当前生产", ChatFormatting.GREEN))
        for (text in monster.detailLines(site.buildingLevel)) builder.appendLore(line(text))
        val stack = builder.build()
        if (selected) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
        return stack
    }

    private fun modeIcon(): ItemStack {
        val marching = site.marching
        return ItemStackBuilder.of(if (marching) Items.LIME_DYE else Items.GRAY_DYE)
            .setName(line(if (marching) "出征" else "休整", if (marching) ChatFormatting.GREEN else ChatFormatting.GRAY))
            .appendLore(line(if (marching) "生产的怪物立刻放出。已有刷怪蛋每秒放一个。" else "生产的怪物留在右侧，成为刷怪蛋。"))
            .appendLore(line("左键切换"))
            .build()
    }

    private fun backIcon(): ItemStack =
        ItemStackBuilder.of(Items.ARROW)
            .setName(line("返回", ChatFormatting.YELLOW))
            .build()

    private fun line(text: String, color: ChatFormatting = ChatFormatting.GRAY): Component =
        Component.literal(text).withStyle { style -> style.withColor(color).withItalic(false) }
}
