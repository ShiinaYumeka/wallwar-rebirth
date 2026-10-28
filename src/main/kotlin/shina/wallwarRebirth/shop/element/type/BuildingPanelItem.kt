package shina.wallwarRebirth.shop.element.type

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import eu.pb4.sgui.api.ClickType
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import shina.wallwarRebirth.building.BuildingDefinition
import shina.wallwarRebirth.building.Buildings
import shina.wallwarRebirth.building.InventoryCosts
import shina.wallwarRebirth.building.SiteChestGui
import shina.wallwarRebirth.building.SiteCoreBlockEntity
import shina.wallwarRebirth.gui.ItemStackBuilder
import shina.wallwarRebirth.shop.element.BaseShopItem
import shina.wallwarRebirth.shop.element.view.ShopItemViewModel
import shina.wallwarRebirth.shop.gui.ShopGui

class BuildingPanelItem(
    index: Int,
    val role: Role,
    val site: SiteCoreBlockEntity?,
) : TypedShopItem(index) {
    enum class Role {
        STORAGE,
        INFO,
        UPGRADE,
    }

    override fun type(): Type = Type.BUILDING

    override fun getDefaultIcon(): ItemStack = icon(null)

    override fun getIcon(gui: ShopGui, viewModel: ShopItemViewModel): ItemStack = icon(gui.player)

    override fun getBaseItem(gui: ShopGui, clickType: ClickType, viewModel: ShopItemViewModel): BaseShopItem? {
        if (role == Role.INFO) return null
        val shown = icon(gui.player)
        val lock = if (role == Role.UPGRADE && isLocked(gui, null)) {
            if ((site?.buildingLevel ?: 0) >= (definition()?.maxLevel ?: 0)) {
                Component.literal("已满级")
            } else {
                Component.literal("材料不足")
            }
        } else {
            null
        }
        return BaseShopItem(shown, emptyMap(), emptyList(), false, 0, true, lock)
    }

    override fun isLocked(gui: ShopGui, item: BaseShopItem?): Boolean {
        if (role != Role.UPGRADE) return false
        val definition = definition() ?: return true
        val site = site ?: return true
        if (site.buildingLevel >= definition.maxLevel) return true
        val next = definition.upgradeTo(site.buildingLevel + 1) ?: return true
        return !InventoryCosts.has(gui.player, next.costs)
    }

    override fun updateView(gui: ShopGui, viewModel: ShopItemViewModel) {
        val site = site
        val world = site?.level
        gui.session.valid = site != null && world != null && world.getBlockEntity(site.blockPos) === site
    }

    override fun executeBuying(
        item: BaseShopItem,
        gui: ShopGui,
        clickType: ClickType,
        materialSuffix: Component?,
    ) {
        if (role != Role.STORAGE) return
        val site = site ?: return
        val player = gui.player
        gui.close()
        player.level().server.execute { SiteChestGui(player, site).open() }
    }

    private fun definition(): BuildingDefinition? {
        val site = site ?: return null
        return Buildings.byId(site.buildingId)
    }

    private fun icon(player: ServerPlayer?): ItemStack {
        val definition = definition()
        return when (role) {
            Role.STORAGE -> named(Items.CHEST, "存储箱", listOf("打开这座核心绑定的箱子"))
            Role.INFO -> {
                val lines = ArrayList<String>()
                if (definition == null || site == null) {
                    lines += "这座核心还没有选定建筑"
                } else {
                    lines += "类别：${definition.category.label}"
                    val active = definition.activeProduction(site.buildingLevel)
                    if (active.isEmpty()) {
                        lines += "当前没有产出"
                    } else {
                        for (rule in active) {
                            val seconds = rule.everyTicks / 20
                            val parts = rule.outputs.joinToString("、") { "${it.count} ${it.name}" }
                            lines += "每 $seconds 秒 $parts"
                        }
                    }
                }
                named(Items.BOOK, definition?.displayName ?: "信息", lines)
            }
            Role.UPGRADE -> upgradeIcon(definition, player)
        }
    }

    private fun upgradeIcon(definition: BuildingDefinition?, player: ServerPlayer?): ItemStack {
        val site = site
        if (definition == null || site == null) return named(Items.ANVIL, "升级", listOf("无法升级"))
        if (site.buildingLevel >= definition.maxLevel) {
            return named(Items.ANVIL, "已满级", listOf("当前等级：${site.buildingLevel}"))
        }
        val next = definition.upgradeTo(site.buildingLevel + 1)
            ?: return named(Items.ANVIL, "已满级", listOf("当前等级：${site.buildingLevel}"))
        val lines = ArrayList<String>()
        lines += "当前等级：${site.buildingLevel}"
        lines += "升到 ${next.toLevel} 级："
        var enough = true
        for (cost in next.costs) {
            val owned = if (player == null) 0 else InventoryCosts.count(player, cost.item)
            if (owned < cost.count) enough = false
            lines += "${cost.name} $owned/${cost.count}"
        }
        lines += if (player == null) "正在检查背包" else if (enough) "背包足够" else "背包不足"
        return named(Items.ANVIL, "升级", lines)
    }

    private fun named(item: net.minecraft.world.item.Item, title: String, lines: List<String>): ItemStack {
        val builder = ItemStackBuilder.of(item).setName(line(title, ChatFormatting.YELLOW))
        for (text in lines) builder.appendLore(line(text))
        return builder.build()
    }

    companion object {
        val CODEC: MapCodec<BuildingPanelItem> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                Codec.intRange(0, 53).fieldOf("index").forGetter(BuildingPanelItem::index),
                Codec.STRING.fieldOf("role").forGetter { it.role.name },
            ).apply(instance) { index, role ->
                BuildingPanelItem(index, Role.valueOf(role), null)
            }
        }

        private fun line(text: String, color: ChatFormatting = ChatFormatting.GRAY): Component =
            Component.literal(text).withStyle { style -> style.withColor(color).withItalic(false) }
    }
}
