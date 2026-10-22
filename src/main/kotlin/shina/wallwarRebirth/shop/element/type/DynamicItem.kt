package shina.wallwarRebirth.shop.element.type

import com.mojang.brigadier.CommandDispatcher
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import eu.pb4.sgui.api.ClickType
import shina.wallwarRebirth.WallwarMod
import shina.wallwarRebirth.gui.ItemStackBuilder
import shina.wallwarRebirth.shop.branch.BranchGroupRegistry
import shina.wallwarRebirth.shop.element.BaseShopItem
import shina.wallwarRebirth.shop.element.view.ShopItemViewModel
import shina.wallwarRebirth.shop.gui.ShopGui
import net.minecraft.commands.CommandResultCallback
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.execution.ExecutionContext
import net.minecraft.commands.functions.CommandFunction
import net.minecraft.commands.functions.PlainTextFunction
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.server.permissions.PermissionSet
import net.minecraft.world.item.ItemStack
import java.util.*
import kotlin.jvm.optionals.getOrNull

sealed class DynamicItem(
    index: Int,
    val valueCommand: String,
    val entries: List<DynamicItemEntry>,
    val fallback: BaseShopItem?
) : TypedShopItem(index) {
    private val commandId = commandIdCount++

    protected var triedParse = false
    protected var commandParseResult:
        Pair<CommandDispatcher<CommandSourceStack>, PlainTextFunction<CommandSourceStack>>? = null
    protected val entriesByValue: Map<Int, DynamicItemEntry> = entries.associateBy { it.value }
    protected var currentBranchGroup: Identifier? = null
        private set

    override fun getBranchGroupId(): Identifier? = currentBranchGroup

    override fun getDefaultIcon(): ItemStack = if (entries.isNotEmpty()) {
        entries[0].item.icon.copy()
    } else {
        fallback?.icon?.copy() ?: ItemStack.EMPTY
    }

    override fun getIcon(gui: ShopGui, viewModel: ShopItemViewModel): ItemStack {
        val matched = resolveCurrentEntry(gui, viewModel.cachedDynamicIndex) ?: return ItemStack.EMPTY
        val canUpgrade = currentBranchGroup?.let {
            BranchGroupRegistry.get(it)?.hasPurchasableBranch(gui.player)
        }
        return ItemStackBuilder.of(matched.icon)
            .addCostInfo(gui.player, matched, isLocked(gui, matched), canUpgrade)
            .build()
    }

    override fun getBaseItem(gui: ShopGui, clickType: ClickType, viewModel: ShopItemViewModel): BaseShopItem? {
        if (clickType != ClickType.MOUSE_LEFT && clickType != ClickType.MOUSE_LEFT_SHIFT) return null
        return resolveCurrentEntry(gui, viewModel.cachedDynamicIndex)
    }

    override fun updateView(gui: ShopGui, viewModel: ShopItemViewModel) {
        executeValueCommand(gui, viewModel)
        val entry = viewModel.cachedDynamicIndex?.let { entriesByValue[it] }
        currentBranchGroup = entry?.branchGroup
    }

    protected fun Pair<CommandDispatcher<CommandSourceStack>, CommandFunction<CommandSourceStack>>.executeFor(
        source: CommandSourceStack,
        callback: CommandResultCallback
    ) {
        val inst = this.second.instantiate(null, this.first)
        Commands.executeCommandInContext(source) { context ->
            ExecutionContext.queueInitialFunctionCall(
                context,
                inst,
                source.withCallback(callback),
                CommandResultCallback.EMPTY
            )
        }
    }

    protected fun tryParseCommand(
        server: MinecraftServer,
        list: List<String>
    ): Pair<CommandDispatcher<CommandSourceStack>, PlainTextFunction<CommandSourceStack>> {
        val dispatcher = server.commands.dispatcher
        return dispatcher to CommandFunction.fromLines(
            WallwarMod.idOf("value_command_dynamic_item_$commandId"),
            dispatcher,
            server.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS),
            list
        ) as PlainTextFunction<CommandSourceStack>
    }

    protected abstract fun resolveCurrentEntry(gui: ShopGui, dynamicIndex: Int?): BaseShopItem?
    protected abstract fun executeValueCommand(gui: ShopGui, viewModel: ShopItemViewModel)

    data class DynamicItemEntry(
        val value: Int,
        val item: BaseShopItem,
        val branchGroup: Identifier? = null
    )

    companion object {
        private var commandIdCount = 0

        val ENTRY_CODEC: Codec<DynamicItemEntry> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.INT.fieldOf("value")
                    .forGetter(DynamicItemEntry::value),
                BaseShopItem.CODEC.fieldOf("item")
                    .forGetter(DynamicItemEntry::item),
                Identifier.CODEC.optionalFieldOf("branch_group")
                    .forGetter { Optional.ofNullable(it.branchGroup) }
            ).apply(instance) { value, item, branchGroupOpt ->
                DynamicItemEntry(value, item, branchGroupOpt.getOrNull())
            }
        }
    }
}
