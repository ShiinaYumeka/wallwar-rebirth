package shina.wallwarRebirth.shop.gui

import eu.pb4.sgui.api.ClickType
import eu.pb4.sgui.api.elements.GuiElement
import eu.pb4.sgui.api.elements.GuiElementInterface
import eu.pb4.sgui.api.gui.layered.Layer
import eu.pb4.sgui.api.gui.layered.LayeredGui
import eu.pb4.sgui.virtual.inventory.VirtualScreenHandler
import shina.wallwarRebirth.gui.ItemStackBuilder
import shina.wallwarRebirth.shop.ShopSession
import shina.wallwarRebirth.shop.ShopHooks
import shina.wallwarRebirth.shop.TeamStores
import shina.wallwarRebirth.shop.element.ShopLayer
import shina.wallwarRebirth.util.ContainerMenuUtil.getMenuType
import shina.wallwarRebirth.util.hideTooltip
import shina.wallwarRebirth.util.playNotifySound
import shina.wallwarRebirth.util.text.TextStyles
import shina.wallwarRebirth.util.text.component
import shina.wallwarRebirth.util.text.style
import shina.wallwarRebirth.util.toColoredGlassPaneItem
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.inventory.ClickType as MinecraftClickType

open class ShopGui(
    val session: ShopSession,
    player: ServerPlayer,
    shopLayers: Collection<ShopLayer>,
    menuRows: Int = shopLayers.maxOfOrNull { it.rows } ?: 6
) : LayeredGui(getMenuType(menuRows), player, false) {
    private val furnaceLayer = bindLayer(shopLayers, menuRows, this)
    private var prevTickCount = 0

    companion object {
        private fun bindLayer(layers: Collection<ShopLayer>, rows: Int, shopGui: ShopGui): Layer {
            assert(rows in 1..6)
            val instance = Layer(rows, 9)
            layers.forEach { layer ->
                layer.items.forEach { itemWithIndex ->
                    if (!itemWithIndex.shouldSkipAdd(gui = shopGui)) {
                        instance.setSlot(
                            itemWithIndex.index(),
                            ShopGuiEntry(itemWithIndex, shopGui)
                        )
                    }
                }
            }
            return instance
        }

        private val TEAM_CHEST_ICON by lazy {
            ItemStackBuilder.of(Items.SPRUCE_CHEST_BOAT)
                .setName(
                    component {
                        translatable("text.hgwar.shop_layer.team_repository.name") provide style {
                            color(ChatFormatting.YELLOW)
                            bold(true)
                            italic(false)
                        }
                        translatable("text.hgwar.shop_layer.team_repository.click") provide style {
                            color(ChatFormatting.GREEN)
                            bold(false)
                            italic(false)
                        }
                    }
                ).appendLore(
                    component {
                        translatable("text.hgwar.shop_layer.team_repository.lore.1") provide style {
                            color(ChatFormatting.GRAY)
                            italic(false)
                        }
                    }
                ).build()
        }
    }

    init {
        val glassPane = (player.team?.color ?: ChatFormatting.RESET)
            .toColoredGlassPaneItem()
            .defaultInstance
            .hideTooltip()
        repeat(this.size) { index ->
            setSlot(index, GuiElement(glassPane, GuiElementInterface.EMPTY_CALLBACK))
        }
        if (this.size > 45) {
            setSlot(45, TEAM_CHEST_ICON) { _, clickType, _ ->
                if (clickType.isLeft) {
                    val team = player.team ?: return@setSlot
                    TeamStores.find(player, team)?.open(player)
                }
            }
        }
        if (session.canAcceptSnow) {
            setSlot(53, SnowBlockIndicator(session, player))
        }
        applyShopLayer()
    }

    fun getFurnaceLevel() = session.furnaceLevel

    override fun onAnyClick(index: Int, type: ClickType, action: MinecraftClickType): Boolean {
        if (index >= getSize() && type == ClickType.MOUSE_LEFT_SHIFT) {
            // 团队箱子快速移动
            handleQuickMove(index)
        }
        return super.onAnyClick(index, type, action)
    }

    override fun onTick() {
        super.onTick()
        val currentTickCount = player.tickCount
        if (prevTickCount == currentTickCount) {
            return
        } else {
            prevTickCount = currentTickCount
        }

        // 自动关闭
        if (currentTickCount % 4 == 0 && !session.isValid()) {
            player.sendSystemMessage(
                Component.translatable("text.hgwar.shop.closed").withStyle(ChatFormatting.RED)
            )
            player.playNotifySound(SoundEvents.CHEST_CLOSE, SoundSource.UI, 0.5f, 0.95f)
            close()
            return
        }

        // 每tick: 主动更新所有ShopGuiEntry
        for (i in 0 until this.furnaceLayer.size) {
            val element = this.furnaceLayer.getSlot(i)
            if (element is ShopGuiEntry) {
                element.tick()
            }
        }
    }

    override fun canSpectatorsClick(): Boolean = false

    private fun handleQuickMove(index: Int) {
        val playerTeam = player.team ?: return
        if (index < getSize()) return

        val container = player.containerMenu
        if (container is VirtualScreenHandler && index < container.slots.size) {
            val slot = container.slots[index]
            val itemStack = slot.item
            if (itemStack.isEmpty || !slot.mayPlace(itemStack)) {
                return
            }
            if (session.canAcceptSnow && session.snowCount < 64 && itemStack.`is`(Items.SNOW_BLOCK)) {
                val add = Mth.clamp(itemStack.count, 0, 64 - session.snowCount)
                session.snowCount += add
                itemStack.shrink(add)
                player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1f, 1f)
                val message = component {
                    translatable(
                        "text.hgwar.shop_layer.snow.put",
                        session.subject?.displayName ?: Component.literal("snow"),
                        add,
                    ) formatted ChatFormatting.GREEN
                }
                player.sendSystemMessage(message)
            } else {
                val repository = TeamStores.find(player, playerTeam) ?: return
                val countBefore = itemStack.count
                val remained = repository.quickPut(itemStack)
                val deposited = countBefore - remained.count
                slot.set(remained)
                player.playNotifySound(SoundEvents.CHEST_CLOSE, SoundSource.UI, 0.5f, 0.95f)
                player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.UI, 0.6f, 1.0f)
                if (deposited > 0) {
                    val movedDisplay = itemStack.copy()
                    movedDisplay.count = deposited
                    ShopHooks.onDeposit(player, movedDisplay, deposited)
                }
            }
        }
    }

    private fun applyShopLayer() {
        this.title = Component.translatable("gui.hgwar.shop.title")
        addLayer(furnaceLayer, 0, 0)
    }

    // 左键放入全部雪块, 右键放入1个雪块
    private class SnowBlockIndicator(val session: ShopSession, val player: ServerPlayer) : GuiElementInterface {
        override fun getGuiCallback(): GuiElementInterface.ClickCallback {
            return GuiElementInterface.ClickCallback { _, clickType, _, _ ->
                if (clickType != ClickType.MOUSE_LEFT && clickType != ClickType.MOUSE_RIGHT) return@ClickCallback
                if (!session.canAcceptSnow || session.snowCount >= 64) return@ClickCallback

                val cursorStack = player.containerMenu.carried
                if (!cursorStack.`is`(Items.SNOW_BLOCK)) return@ClickCallback

                val maxAdd = 64 - session.snowCount
                val add = if (clickType == ClickType.MOUSE_LEFT) {
                    Mth.clamp(cursorStack.count, 0, maxAdd)
                } else {
                    1.coerceAtMost(maxAdd)
                }
                session.snowCount += add
                cursorStack.shrink(add)
            }
        }

        override fun getItemStack(): ItemStack = if (session.snowCount == 0) {
            ItemStack.EMPTY
        } else {
            ItemStackBuilder.of(Items.SNOW_BLOCK)
                .setCount(session.snowCount)
                .appendLore(
                    Component.translatable("gui.hgwar.shop.snow_block_indicator.line1")
                        .withStyle(TextStyles.GRAY_ITEM_LORE),
                    Component.translatable("gui.hgwar.shop.snow_block_indicator.line2")
                        .withStyle(TextStyles.GRAY_ITEM_LORE)
                )
                .build()
        }
    }
}
