package shina.wallwarRebirth.shop

import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import shina.wallwarRebirth.shop.element.BaseShopItem
import shina.wallwarRebirth.shop.element.type.DynamicItemForPlayer
import shina.wallwarRebirth.shop.element.type.TypedShopItem
import java.util.concurrent.CopyOnWriteArrayList

fun interface DynamicPlayerModifier {
    fun modify(
        session: ShopSession,
        player: ServerPlayer,
        item: DynamicItemForPlayer,
        matched: BaseShopItem?,
    ): BaseShopItem?
}

fun interface ShopBuyListener {
    fun onBuy(
        session: ShopSession,
        player: ServerPlayer,
        shopItem: TypedShopItem,
        item: BaseShopItem,
        fromPlayerInventory: Map<Identifier, Int>,
        fromTeamRepo: Map<Identifier, Int>,
        fromTeamRepoInvalid: Map<Identifier, Int>,
    )
}

fun interface ShopDepositListener {
    fun onDeposit(player: ServerPlayer, stack: ItemStack, amount: Int)
}

object ShopHooks {
    val dynamicPlayerModifiers = CopyOnWriteArrayList<DynamicPlayerModifier>()
    val buyListeners = CopyOnWriteArrayList<ShopBuyListener>()
    val depositListeners = CopyOnWriteArrayList<ShopDepositListener>()

    fun modifyDynamicForPlayer(
        session: ShopSession,
        player: ServerPlayer,
        item: DynamicItemForPlayer,
        matched: BaseShopItem?,
    ): BaseShopItem? {
        var current = matched
        for (modifier in dynamicPlayerModifiers) {
            current = modifier.modify(session, player, item, current)
        }
        return current
    }

    fun onBought(
        session: ShopSession,
        player: ServerPlayer,
        shopItem: TypedShopItem,
        item: BaseShopItem,
        fromPlayerInventory: Map<Identifier, Int>,
        fromTeamRepo: Map<Identifier, Int>,
        fromTeamRepoInvalid: Map<Identifier, Int>,
    ) {
        for (listener in buyListeners) {
            listener.onBuy(
                session,
                player,
                shopItem,
                item,
                fromPlayerInventory,
                fromTeamRepo,
                fromTeamRepoInvalid,
            )
        }
    }

    fun onDeposit(player: ServerPlayer, stack: ItemStack, amount: Int) {
        if (amount <= 0 || stack.isEmpty) return
        for (listener in depositListeners) {
            listener.onDeposit(player, stack, amount)
        }
    }
}
