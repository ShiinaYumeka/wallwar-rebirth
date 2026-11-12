package shina.wallwarRebirth.profession

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.ItemTags
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import shina.wallwarRebirth.building.BuildingDefinition
import shina.wallwarRebirth.building.Buildings
import shina.wallwarRebirth.building.ItemAmount
import shina.wallwarRebirth.building.SiteCoreBlockEntity
import shina.wallwarRebirth.item.ModItems
import shina.wallwarRebirth.match.Match
import shina.wallwarRebirth.rules.MatchRules
import shina.wallwarRebirth.shop.ShopMaterialRegistry
import shina.wallwarRebirth.shop.element.BaseShopItem
import shina.wallwarRebirth.shop.match.TeamWarehouses

object ProfessionEffects {
    fun givePurchased(player: ServerPlayer, item: BaseShopItem): Boolean {
        if (!item.icon.`is`(ItemTags.SWORDS)) return false
        val stack = item.icon.copy()
        val member = Match.member(player.uuid)
        if (member != null && Classes.isSwordsman(member.classLocked, member.classId)) {
            Classes.applySmite(player, stack)
        }
        if (!player.inventory.add(stack)) player.drop(stack, false)
        return true
    }

    fun refundIfBastionWall(player: ServerPlayer, item: BaseShopItem) {
        if (!item.icon.`is`(ModItems.CORE_WALL)) return
        refund(player, amountsOf(player, item.costs))
    }

    fun refundWallUpgrade(player: ServerPlayer, costs: List<ItemAmount>) {
        refund(player, costs)
    }

    fun isBastion(player: ServerPlayer): Boolean {
        val member = Match.member(player.uuid) ?: return false
        return Classes.isBastion(member.classLocked, member.classId)
    }

    fun dropIfWrecker(site: SiteCoreBlockEntity, attacker: Player?) {
        val player = attacker as? ServerPlayer ?: return
        val member = Match.member(player.uuid) ?: return
        if (!Classes.isWrecker(member.classLocked, member.classId)) return
        if (site.team.isEmpty() || site.isAlly(player)) return
        val definition = Buildings.byId(site.buildingId) ?: return
        val world = site.level as? ServerLevel ?: return
        for (drop in share(spent(definition, site.buildingLevel), MatchRules.WRECKER_SALVAGE_NUM, MatchRules.WRECKER_SALVAGE_DEN)) {
            Block.popResource(world, site.blockPos, ItemStack(drop.item, drop.count))
        }
    }

    private fun refund(player: ServerPlayer, costs: List<ItemAmount>) {
        val member = Match.member(player.uuid) ?: return
        if (!Classes.isBastion(member.classLocked, member.classId)) return
        val given = share(costs, MatchRules.BASTION_REFUND_NUM, MatchRules.BASTION_REFUND_DEN)
        if (given.isEmpty()) return
        for (part in given) {
            val stack = ItemStack(part.item, part.count)
            if (stack.isEmpty) continue
            if (player.inventory.add(stack)) continue
            val leftover = TeamWarehouses.deposit(member.team, stack)
            leftover.count = 0
        }
    }

    private fun amountsOf(player: ServerPlayer, costs: Map<net.minecraft.resources.Identifier, Int>): List<ItemAmount> {
        val access = player.registryAccess()
        return costs.mapNotNull { (id, count) ->
            if (count <= 0) return@mapNotNull null
            val material = ShopMaterialRegistry.getMaterial(access, id) ?: return@mapNotNull null
            val stack = material.representativeStack
            if (stack.isEmpty || stack.`is`(Items.BARRIER)) return@mapNotNull null
            ItemAmount(stack.item, count, material.description.string)
        }
    }

    private fun spent(definition: BuildingDefinition, level: Int): List<ItemAmount> {
        val merged = LinkedHashMap<Item, ItemAmount>()
        fun add(list: List<ItemAmount>) {
            for (part in list) {
                if (part.count <= 0) continue
                val old = merged[part.item]
                merged[part.item] = if (old == null) part else ItemAmount(part.item, old.count + part.count, part.name)
            }
        }
        add(purchase(definition))
        for (upgrade in definition.upgrades) {
            if (upgrade.toLevel <= level) add(upgrade.costs)
        }
        return merged.values.toList()
    }

    private fun purchase(definition: BuildingDefinition): List<ItemAmount> = when (definition.id) {
        Buildings.WALL.id -> listOf(ingot(Items.COPPER_INGOT, MatchRules.WALL_PRICE_COPPER, "铜锭"))
        Buildings.TOWER.id -> listOf(ingot(Items.COPPER_INGOT, MatchRules.TOWER_PRICE_COPPER, "铜锭"))
        Buildings.TESLA.id -> listOf(ingot(Items.COPPER_INGOT, MatchRules.TESLA_PRICE_COPPER, "铜锭"))
        Buildings.BARRACKS.id -> listOf(ingot(Items.IRON_INGOT, MatchRules.BARRACKS_PRICE_IRON, "铁锭"))
        Buildings.CANNON.id -> listOf(
            ingot(Items.IRON_INGOT, MatchRules.CANNON_PRICE_IRON, "铁锭"),
            ingot(Items.COPPER_INGOT, MatchRules.CANNON_PRICE_COPPER, "铜锭"),
        )
        Buildings.RELAY.id -> listOf(
            ingot(Items.IRON_INGOT, MatchRules.RELAY_PRICE_IRON, "铁锭"),
            ingot(Items.COPPER_INGOT, MatchRules.RELAY_PRICE_COPPER, "铜锭"),
        )
        Buildings.FARMLAND.id -> listOf(
            ingot(Items.IRON_INGOT, MatchRules.FARMLAND_PRICE_IRON, "铁锭"),
            ingot(Items.COPPER_INGOT, MatchRules.FARMLAND_PRICE_COPPER, "铜锭"),
        )
        Buildings.PILLAGER.id -> listOf(ingot(Items.IRON_INGOT, MatchRules.PILLAGER_TOWER_PRICE_IRON, "铁锭"))
        else -> emptyList()
    }

    private fun ingot(item: Item, count: Int, name: String): ItemAmount = ItemAmount(item, count, name)

    private fun share(costs: List<ItemAmount>, num: Int, den: Int): List<ItemAmount> =
        costs.map { ItemAmount(it.item, (it.count * num + den - 1) / den, it.name) }.filter { it.count > 0 }

    fun boughtMessage(item: BaseShopItem, materialSuffix: Component?): Component {
        val msg = Component.literal("已购买 ").withStyle(ChatFormatting.YELLOW)
            .append(Component.empty().withStyle(ChatFormatting.WHITE).append(item.icon.hoverName))
        if (!materialSuffix?.string.isNullOrEmpty()) {
            msg.append("  ").append(materialSuffix)
        }
        return msg
    }
}
