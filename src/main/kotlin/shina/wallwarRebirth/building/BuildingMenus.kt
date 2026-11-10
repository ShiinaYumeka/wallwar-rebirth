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
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import shina.wallwarRebirth.gui.ItemStackBuilder
import shina.wallwarRebirth.match.BaseShop
import shina.wallwarRebirth.match.Match
import shina.wallwarRebirth.match.MatchRules
import shina.wallwarRebirth.monster.MonsterDefinition
import shina.wallwarRebirth.monster.Monsters
import shina.wallwarRebirth.shop.ShopSession
import shina.wallwarRebirth.shop.element.BaseShopItem
import shina.wallwarRebirth.shop.element.ShopItemWithIndex
import shina.wallwarRebirth.shop.element.ShopLayer
import shina.wallwarRebirth.shop.element.view.ShopItemViewModel
import shina.wallwarRebirth.shop.gui.ShopGui
import shina.wallwarRebirth.util.hideTooltip
import shina.wallwarRebirth.util.playNotifySound

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

    fun paintBaseShop(gui: ShopGui, site: SiteCoreBlockEntity) {
        val viewer = gui.player
        gui.setSlot(20, GuiElement(baseUpgrade(site)) { _, click, _, _ ->
            if (!click.isLeft || click.shift) return@GuiElement
            site.tryUpgrade(viewer)
            paintBaseShop(gui, site)
        })
        gui.setSlot(22, GuiElement(baseInfo(site), GuiElementInterface.EMPTY_CALLBACK))
        gui.setSlot(24, GuiElement(baseModule(site, ModuleSlot.PRIMARY, "强化槽")) { _, click, _, _ ->
            baseModuleClick(gui, site, ModuleSlot.PRIMARY, click)
        })
        gui.setSlot(26, GuiElement(baseModule(site, ModuleSlot.SECONDARY, "强化槽 II")) { _, click, _, _ ->
            baseModuleClick(gui, site, ModuleSlot.SECONDARY, click)
        })
        gui.setSlot(33, GuiElement(baseModule(site, ModuleSlot.DIFFUSION, "扩散槽")) { _, click, _, _ ->
            baseModuleClick(gui, site, ModuleSlot.DIFFUSION, click)
        })
    }

    private fun baseModuleClick(gui: ShopGui, site: SiteCoreBlockEntity, slot: ModuleSlot, click: ClickType) {
        val viewer = gui.player
        val cursor = viewer.containerMenu.carried
        if (click.shift || click.isRight) {
            if (!site.moduleStack(slot).isEmpty) viewer.sendSystemMessage(Component.literal("这个模块不能拆卸"))
            return
        }
        if (!click.isLeft || click.shift) return
        if (!cursor.isEmpty && !site.moduleStack(slot).isEmpty) {
            viewer.sendSystemMessage(Component.literal("这个模块不能拆卸"))
            return
        }
        if (cursor.isEmpty) {
            if (site.moduleStack(slot).isEmpty || slot != ModuleSlot.PRIMARY) return
            val choices = site.choicesFor(slot, Buildings.BASE)
            if (choices.isEmpty()) {
                viewer.sendSystemMessage(Component.literal("没有可以强化的矿物"))
                return
            }
            gui.close()
            viewer.level().server.execute { MineralPickerGui(viewer, site, Buildings.BASE, slot, choices).open() }
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
        paintBaseShop(gui, site)
    }

    private fun baseUpgrade(site: SiteCoreBlockEntity): ItemStack {
        val definition = Buildings.BASE
        val builder = ItemStackBuilder.of(Items.EXPERIENCE_BOTTLE)
            .setName(plain(if (site.buildingLevel >= definition.maxLevel) "已满级" else "升级", ChatFormatting.YELLOW))
        for (upgrade in definition.upgrades) {
            val color = if (upgrade.toLevel == site.buildingLevel + 1) ChatFormatting.YELLOW else ChatFormatting.GRAY
            builder.appendLore(plain(upgrade.describe(), color))
        }
        return builder.build()
    }

    private fun baseInfo(site: SiteCoreBlockEntity): ItemStack {
        val definition = Buildings.BASE
        val builder = ItemStackBuilder.of(definition.icon)
            .setName(plain("${definition.displayName}（${levelMark(site.buildingLevel)}级）", ChatFormatting.YELLOW))
        builder.appendLore(plain("类别：${definition.categoryText()}"))
        builder.appendLore(plain("等级：${site.buildingLevel}"))
        builder.appendLore(plain("生命：${site.health}/${definition.maxHealth(site.buildingLevel)}"))
        builder.appendLore(plain("队伍：${site.team.ifEmpty { "无" }}"))
        if (definition.summary.isNotEmpty()) builder.appendLore(plain(definition.summary))
        for ((text, active) in site.productionLines()) {
            builder.appendLore(plain(text, if (active) ChatFormatting.GREEN else ChatFormatting.GRAY))
        }
        return builder.build()
    }

    private fun baseModule(site: SiteCoreBlockEntity, slot: ModuleSlot, label: String): ItemStack {
        val filled = site.moduleStack(slot)
        val builder = if (filled.isEmpty) {
            ItemStackBuilder.of(emptyPane(slot))
        } else {
            ItemStackBuilder.of(filled)
        }
        builder.setName(plain(label, ChatFormatting.YELLOW))
        if (filled.isEmpty) builder.appendLore(plain("左键放入${moduleName(slot)}"))
        for (text in baseEffect(slot)) builder.appendLore(plain(text))
        if (slot == ModuleSlot.PRIMARY) {
            builder.appendLore(plain("放好后左键选择矿物"))
            builder.appendLore(plain("当前：${site.mineralLabel(slot)}"))
        }
        return builder.build()
    }

    private fun levelMark(level: Int): String = when (level) {
        1 -> "I"
        2 -> "II"
        3 -> "III"
        else -> level.toString()
    }

    private fun plain(text: String, color: ChatFormatting = ChatFormatting.GRAY): Component =
        Component.literal(text).withStyle { style -> style.withColor(color).withItalic(false) }
}

private fun emptyPane(slot: ModuleSlot): Item = when (slot) {
    ModuleSlot.PRIMARY -> Items.LIGHT_BLUE_STAINED_GLASS_PANE
    ModuleSlot.SECONDARY -> Items.BLUE_STAINED_GLASS_PANE
    ModuleSlot.DIFFUSION -> Items.PURPLE_STAINED_GLASS_PANE
}

private fun moduleName(slot: ModuleSlot): String = when (slot) {
    ModuleSlot.PRIMARY -> "强化模块"
    ModuleSlot.SECONDARY -> "强化模块 2"
    ModuleSlot.DIFFUSION -> "扩散模块"
}

private fun baseEffect(slot: ModuleSlot): List<String> = when (slot) {
    ModuleSlot.PRIMARY -> listOf("指定一种当前可生成的矿物，该矿物的生成速率乘以 2。")
    ModuleSlot.SECONDARY -> listOf("每 5 秒回复这座基地 1 点生命，满血跳过。不改变矿物。")
    ModuleSlot.DIFFUSION -> listOf("本队玩家得到便携终端，手持右键打开基地商店。")
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
                for (column in 0 until 4) {
                    val guiSlot = STORAGE_ORIGIN + row * 9 + column
                    setSlotRedirect(guiSlot, FilterSlot(site.barracksMaterials, row * 4 + column) { BarracksSlots.acceptsMaterial(it) })
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
        if (definition.id == Buildings.RELAY.id) {
            setSlot(0, GuiElement(openShopIcon()) { _, clickType, _, _ ->
                if (!clickType.isLeft || clickType.shift) return@GuiElement
                val world = site.level as? ServerLevel ?: return@GuiElement
                val base = Relay.teamBase(world, site.team)
                if (base == null) {
                    viewer.sendSystemMessage(Component.literal("基地不在"))
                    return@GuiElement
                }
                close()
                viewer.level().server.execute { BaseShop.open(viewer, base) }
            })
            setSlot(8, GuiElement(callIcon()) { _, clickType, _, _ ->
                if (!clickType.isLeft || clickType.shift) return@GuiElement
                val world = site.level as? ServerLevel
                if (world == null || !Relay.summon(site, world)) {
                    viewer.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1f, 1f)
                }
            })
            setSlot(INFO_SLOT, GuiElement(infoIcon(), GuiElementInterface.EMPTY_CALLBACK))
            setSlot(DEMOLISH_SLOT, GuiElement(demolishIcon()) { _, clickType, _, _ ->
                if (!clickType.isLeft || clickType.shift) return@GuiElement
                if (site.demolish(viewer)) close()
            })
            return
        }
        setSlot(UPGRADE_SLOT, GuiElement(upgradeIcon()) { _, clickType, _, _ ->
            if (!clickType.isLeft || clickType.shift) return@GuiElement
            close()
            viewer.level().server.execute { UpgradePanelGui(viewer, site, definition).open() }
        })
        setSlot(INFO_SLOT, GuiElement(infoIcon(), GuiElementInterface.EMPTY_CALLBACK))
        setSlot(DEMOLISH_SLOT, GuiElement(demolishIcon()) { _, clickType, _, _ ->
            if (!clickType.isLeft || clickType.shift) return@GuiElement
            if (site.demolish(viewer)) close()
        })
        if (barracks) {
            paintProduction()
            setSlot(MARCH_SLOT, GuiElement(marchIcon()) { _, clickType, _, _ ->
                if (!clickType.isLeft || clickType.shift) return@GuiElement
                site.marching = !site.marching
                site.setChanged()
                paintButtons()
            })
        }
    }

    private fun paintProduction() {
        val monsters = Monsters.selectable()
        monsters.forEachIndexed { index, monster ->
            val slot = PRODUCTION_SLOTS.getOrElse(index) { return@forEachIndexed }
            setSlot(slot, GuiElement(monsterIcon(monster)) { _, clickType, _, _ ->
                if (!clickType.isLeft || clickType.shift) return@GuiElement
                if (site.buildingLevel < monster.rank) {
                    viewer.sendSystemMessage(Component.literal("兵营等级不够"))
                    return@GuiElement
                }
                if (site.producedMonster != monster.id) {
                    site.producedMonster = monster.id
                    site.actionAge = 0
                    site.setChanged()
                    viewer.sendSystemMessage(Component.literal("已改为生产${monster.displayName}，这一轮重新计时"))
                }
                paintButtons()
            })
        }
    }

    private fun monsterIcon(monster: MonsterDefinition): ItemStack {
        val selected = site.producedMonster == monster.id
        val builder = ItemStackBuilder.of(monster.egg)
            .setName(line(monster.displayName, if (selected) ChatFormatting.GREEN else ChatFormatting.YELLOW))
        if (selected) builder.appendLore(line("当前生产", ChatFormatting.GREEN))
        if (site.buildingLevel < monster.rank) builder.appendLore(line("兵营等级不够"))
        for (text in monster.detailLines(site.buildingLevel)) builder.appendLore(line(text))
        val stack = builder.build()
        if (selected) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
        return stack
    }

    private fun openShopIcon(): ItemStack =
        ItemStackBuilder.of(Items.EMERALD)
            .setName(line("打开商店", ChatFormatting.GREEN))
            .appendLore(line("打开本队基地商店"))
            .build()

    private fun callIcon(): ItemStack =
        ItemStackBuilder.of(Items.BELL)
            .setName(line("召集", ChatFormatting.GOLD))
            .appendLore(line("召最近的我方矿工到这里"))
            .appendLore(line("赶到后交掉身上的矿，再去挖"))
            .build()

    private fun shopIcon(): ItemStack =
        ItemStackBuilder.of(Items.EMERALD)
            .setName(line("商店", ChatFormatting.GREEN))
            .appendLore(line("建筑核心、强化、装备"))
            .appendLore(line("只在战斗阶段可以购买"))
            .build()

    private fun infoIcon(): ItemStack {
        val builder = ItemStackBuilder.of(definition.icon)
            .setName(line("${definition.displayName}（${levelMark(site.buildingLevel)}级）", ChatFormatting.YELLOW))
        builder.appendLore(line("类别：${definition.categoryText()}"))
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
            builder.appendLore(line("当前间隔：${MatchRules.secondsText(monster.productionTicks(site.buildingLevel))} 秒"))
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

    private fun marchIcon(): ItemStack {
        val marching = site.marching
        return ItemStackBuilder.of(if (marching) Items.EMERALD else Items.REDSTONE)
            .setName(line(if (marching) "出征" else "休整", if (marching) ChatFormatting.GREEN else ChatFormatting.RED))
            .appendLore(line(if (marching) "正在出征，点击切换为休整" else "已停摆，点击切换为出征"))
            .build()
    }

    private fun line(text: String, color: ChatFormatting = ChatFormatting.GRAY): Component =
        Component.literal(text).withStyle { style -> style.withColor(color).withItalic(false) }

    companion object {
        const val UPGRADE_SLOT = 2
        const val INFO_SLOT = 4
        const val DEMOLISH_SLOT = 6
        const val STORAGE_ORIGIN = 18
        const val MARCH_SLOT = 53
        val PRODUCTION_SLOTS = intArrayOf(23, 24, 25, 26, 32, 33, 34, 35, 41)
    }
}

private class BuildingShopGui(
    session: ShopSession,
    player: ServerPlayer,
    layers: Collection<ShopLayer>,
    heading: String,
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
        if (definition.acceptsModules) {
            paintModule(1, ModuleSlot.PRIMARY, "强化槽")
            paintModule(2, ModuleSlot.SECONDARY, "强化槽 II")
        }
        if (definition.acceptsDiffusion) paintModule(3, ModuleSlot.DIFFUSION, "扩散槽")
    }

    private fun paintModule(index: Int, slot: ModuleSlot, label: String) {
        setSlot(index, GuiElement(moduleIcon(slot, label)) { _, clickType, _, _ ->
            handleModule(slot, clickType)
        })
    }

    private fun handleModule(slot: ModuleSlot, clickType: ClickType) {
        val cursor = viewer.containerMenu.carried
        if (clickType.shift || clickType.isRight) {
            if (!site.moduleStack(slot).isEmpty) viewer.sendSystemMessage(Component.literal("这个模块不能拆卸"))
            return
        }
        if (!clickType.isLeft || clickType.shift) return
        if (!cursor.isEmpty && !site.moduleStack(slot).isEmpty) {
            viewer.sendSystemMessage(Component.literal("这个模块不能拆卸"))
            return
        }
        if (cursor.isEmpty) {
            if (!definition.acceptsModules) {
                viewer.sendSystemMessage(Component.literal("这座建筑不能装模块"))
                return
            }
            if (site.moduleStack(slot).isEmpty || slot == ModuleSlot.DIFFUSION) return
            if (definition.id == Buildings.BASE.id && slot == ModuleSlot.PRIMARY) {
                val choices = site.choicesFor(slot, definition)
                if (choices.isEmpty()) {
                    viewer.sendSystemMessage(Component.literal("没有可以强化的矿物"))
                    return
                }
                close()
                viewer.level().server.execute { MineralPickerGui(viewer, site, definition, slot, choices).open() }
                return
            }
            if (definition.id == Buildings.BARRACKS.id && slot == ModuleSlot.SECONDARY) {
                if (!site.isAlly(viewer)) {
                    viewer.sendSystemMessage(Component.literal("敌方打不开"))
                    return
                }
                close()
                viewer.level().server.execute { ReleaseCountGui(viewer, site, definition).open() }
            }
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
            ItemStackBuilder.of(emptyPane(slot))
        } else {
            ItemStackBuilder.of(filled)
        }
        builder.setName(line(label, ChatFormatting.YELLOW))
        if (filled.isEmpty) builder.appendLore(line("左键放入${moduleName(slot)}"))
        for (text in effectLines(definition, slot)) builder.appendLore(line(text))
        if (definition.id == Buildings.BASE.id && slot == ModuleSlot.PRIMARY) {
            builder.appendLore(line("放好后左键选择矿物"))
            builder.appendLore(line("当前：${site.mineralLabel(slot)}"))
        }
        if (definition.id == Buildings.BARRACKS.id && slot == ModuleSlot.SECONDARY) {
            builder.appendLore(line("放好后左键选择只数"))
            val count = site.releaseCount
            val current = if (count in MatchRules.BARRACKS_RELEASE_MIN..MatchRules.BARRACKS_RELEASE_MAX) {
                count.toString()
            } else {
                "未选择，按 1 只"
            }
            builder.appendLore(line("当前：$current"))
        }
        return builder.build()
    }

    private fun effectLines(definition: BuildingDefinition, slot: ModuleSlot): List<String> = when (definition.id) {
        Buildings.BASE.id -> baseEffect(slot)
        Buildings.WALL.id -> when (slot) {
            ModuleSlot.PRIMARY -> listOf("没在挨打时，每 ${MatchRules.WALL_HEAL_TICKS / MatchRules.TICKS_PER_SECOND} 秒回复自己和 ${MatchRules.WALL_HEAL_RANGE.toInt()} 格内的友方建筑各 ${MatchRules.WALL_HEAL_AMOUNT} 点。同一座建筑每 5 秒只吃到一次。")
            ModuleSlot.SECONDARY -> listOf("被攻击时按原版荆棘反伤，固定 ${MatchRules.WALL_THORNS_DAMAGE} 点。")
            ModuleSlot.DIFFUSION -> listOf("32 格内的其他友方城墙视为装上这座城墙槽里已有的强化模块，已装的不叠加，扩散本身不外传。")
        }
        Buildings.TOWER.id -> when (slot) {
            ModuleSlot.PRIMARY -> listOf("一支箭最多命中 ${MatchRules.TOWER_PIERCE_HITS} 个敌对目标，命中后继续飞。")
            ModuleSlot.SECONDARY -> listOf("射击间隔减少 20%，变为 ${towerIntervals()} 秒。")
            ModuleSlot.DIFFUSION -> listOf("一次射出三支箭，左右各偏 ${MatchRules.TOWER_SPREAD_DEGREES.toInt()} 度。")
        }
        Buildings.BARRACKS.id -> when (slot) {
            ModuleSlot.PRIMARY -> listOf("放出的怪物自带速度 I，直到消失。")
            ModuleSlot.SECONDARY -> listOf(
                "凑满选定只数乘以每轮只数再一起放出。选择仍是 1 到 5。",
                "骷髅死士按 3、6、9、12、15 放出。没选时按 1 轮。",
            )
            ModuleSlot.DIFFUSION -> listOf("32 格内的友方唤灵每 5 秒回复 1 点，满血跳过。多座各自计时。")
        }
        Buildings.CANNON.id -> when (slot) {
            ModuleSlot.PRIMARY -> listOf("爆炸半径扩大 50%。威力半径 ${MatchRules.CANNON_POWER_WIDE.toInt()} 格，生物伤害在 ${MatchRules.CANNON_FALLOFF_WIDE.toInt()} 格归零。")
            ModuleSlot.SECONDARY -> listOf("发射间隔减少 20%，变为 ${MatchRules.CANNON_INTERVAL * 4 / 5 / MatchRules.TICKS_PER_SECOND} 秒。")
            ModuleSlot.DIFFUSION -> listOf("本队生存玩家潜行右键进入。有目标时蓄力完成后沿视线发射载人 TNT，撞上或 4 秒后爆炸。")
        }
        else -> emptyList()
    }

    private fun towerIntervals(): String = MatchRules.TOWER_INTERVAL.joinToString(" / ") { ticks ->
        MatchRules.secondsText(ticks * 4 / 5)
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

    private fun line(text: String, color: ChatFormatting = ChatFormatting.GRAY): Component =
        Component.literal(text).withStyle { style -> style.withColor(color).withItalic(false) }
}

private class MineralPickerGui(
    private val viewer: ServerPlayer,
    private val site: SiteCoreBlockEntity,
    private val definition: BuildingDefinition,
    private val slot: ModuleSlot,
    choices: List<MineralOutput>,
) : SimpleGui(MenuType.GENERIC_9x1, viewer, false) {
    init {
        title = Component.literal("选择强化的矿物")
        val glass = ItemStack(Items.GRAY_STAINED_GLASS_PANE).hideTooltip()
        for (index in 0 until size) setSlot(index, GuiElement(glass, GuiElementInterface.EMPTY_CALLBACK))
        choices.forEachIndexed { index, mineral ->
            if (index >= size) return@forEachIndexed
            setSlot(index, GuiElement(choiceIcon(mineral)) { _, clickType, _, _ ->
                if (!clickType.isLeft || clickType.shift) return@GuiElement
                site.setMineral(slot, mineral.item)
                close()
                viewer.level().server.execute {
                    if (definition.id == Buildings.BASE.id) BaseShop.open(viewer, site, BaseShop.Page.BASE)
                    else UpgradePanelGui(viewer, site, definition).open()
                }
            })
        }
    }

    override fun onTick() {
        super.onTick()
        val world = site.level
        if (world == null || world.getBlockEntity(site.blockPos) !== site) close()
    }

    private fun choiceIcon(mineral: MineralOutput): ItemStack {
        val hint = "这种矿物的间隔减半"
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

private class ReleaseCountGui(
    private val viewer: ServerPlayer,
    private val site: SiteCoreBlockEntity,
    private val definition: BuildingDefinition,
) : SimpleGui(MenuType.GENERIC_9x1, viewer, false) {
    init {
        title = Component.literal("选择放出只数")
        val glass = ItemStack(Items.GRAY_STAINED_GLASS_PANE).hideTooltip()
        for (index in 0 until size) setSlot(index, GuiElement(glass, GuiElementInterface.EMPTY_CALLBACK))
        for (count in MatchRules.BARRACKS_RELEASE_MIN..MatchRules.BARRACKS_RELEASE_MAX) {
            setSlot(count - MatchRules.BARRACKS_RELEASE_MIN, GuiElement(choiceIcon(count)) { _, clickType, _, _ ->
                if (!clickType.isLeft || clickType.shift) return@GuiElement
                if (!site.isAlly(viewer)) {
                    viewer.sendSystemMessage(Component.literal("敌方打不开"))
                    return@GuiElement
                }
                site.chooseReleaseCount(count)
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

    private fun choiceIcon(count: Int): ItemStack {
        val selected = site.releaseCount == count
        val stack = ItemStackBuilder.of(Items.PAPER)
            .setName(Component.literal("$count").withStyle { style ->
                style.withColor(ChatFormatting.YELLOW).withItalic(false)
            })
            .appendLore(Component.literal(if (selected) "当前选中" else "点击选定").withStyle { style ->
                style.withColor(ChatFormatting.GRAY).withItalic(false)
            })
            .build()
        if (selected) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
        return stack
    }
}
