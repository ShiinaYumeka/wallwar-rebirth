package shina.wallwarRebirth.profession

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.ItemTags
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Block
import net.minecraft.world.phys.HitResult
import shina.wallwarRebirth.WallwarMod
import shina.wallwarRebirth.building.BuildingDefinition
import shina.wallwarRebirth.building.Buildings
import shina.wallwarRebirth.building.ItemAmount
import shina.wallwarRebirth.building.SiteCoreBlockEntity
import shina.wallwarRebirth.item.ModItems
import shina.wallwarRebirth.match.Match
import shina.wallwarRebirth.match.MatchPersistence
import shina.wallwarRebirth.rules.MatchRules
import shina.wallwarRebirth.shop.ShopMaterialRegistry
import shina.wallwarRebirth.shop.element.BaseShopItem
import shina.wallwarRebirth.shop.match.TeamWarehouses

object ProfessionEffects {
    private val championHealth = AttributeModifier(
        WallwarMod.idOf("champion_health"),
        MatchRules.CHAMPION_BONUS_HEALTH,
        AttributeModifier.Operation.ADD_VALUE,
    )
    private val championAttack = AttributeModifier(
        WallwarMod.idOf("champion_attack"),
        MatchRules.CHAMPION_BONUS_ATTACK,
        AttributeModifier.Operation.ADD_VALUE,
    )

    fun givePurchased(player: ServerPlayer, item: BaseShopItem): Boolean {
        if (!item.icon.`is`(ItemTags.SWORDS)) return false
        val stack = item.icon.copy()
        val member = Match.member(player.uuid)
        if (member != null && Classes.isSwordsman(member.classLocked, member.classId)) {
            val now = player.level().server.overworld().gameTime
            if (member.swordPassiveReadyAt <= now) {
                Classes.applySwordEnchants(player, stack)
                member.swordPassiveReadyAt = now + MatchRules.SWORDSMAN_PASSIVE_COOLDOWN_TICKS
                val seconds = MatchRules.SWORDSMAN_PASSIVE_COOLDOWN_TICKS / MatchRules.TICKS_PER_SECOND
                player.sendSystemMessage(
                    Component.literal("这把剑带有亡灵杀手 II 和节肢杀手 II，被动冷却 $seconds 秒"),
                )
                MatchPersistence.save(player.level().server)
            } else {
                player.sendSystemMessage(
                    Component.literal("${Classes.swordPassiveStatus(member.swordPassiveReadyAt, now)}，这把剑没有附魔"),
                )
            }
        }
        if (!player.inventory.add(stack)) player.drop(stack, false)
        return true
    }

    fun tickSwordsmanPassive(server: net.minecraft.server.MinecraftServer) {
        if (!Match.isFight()) return
        val now = server.overworld().gameTime
        var dirty = false
        for (player in server.playerList.players) {
            val member = Match.member(player.uuid) ?: continue
            if (!Classes.isSwordsman(member.classLocked, member.classId)) continue
            if (member.swordPassiveReadyAt <= 0L || now < member.swordPassiveReadyAt) continue
            member.swordPassiveReadyAt = 0
            dirty = true
            player.sendSystemMessage(Component.literal("被动技能已就绪"))
        }
        if (dirty) MatchPersistence.save(server)
    }

    fun refundIfBastionWall(player: ServerPlayer, item: BaseShopItem) {
        if (!item.icon.`is`(ModItems.CORE_WALL)) return
        refund(player, amountsOf(player, item.costs))
    }

    fun refundWallUpgrade(player: ServerPlayer, costs: List<ItemAmount>) {
        refund(player, costs)
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

    fun rewardConqueror(site: SiteCoreBlockEntity, attacker: Player?) {
        val player = attacker as? ServerPlayer ?: return
        val member = Match.member(player.uuid) ?: return
        if (!Classes.isConqueror(member.classLocked, member.classId)) return
        if (site.team.isEmpty() || site.isAlly(player)) return
        var lapis = 0
        if (!site.reinforce.isEmpty) lapis += moduleLapis(MatchRules.MODULE_LAPIS)
        if (!site.reinforceTwo.isEmpty) lapis += moduleLapis(MatchRules.MODULE_TWO_LAPIS)
        if (lapis <= 0) return
        val stack = ItemStack(Items.LAPIS_LAZULI, lapis)
        if (!player.inventory.add(stack)) {
            val leftover = TeamWarehouses.deposit(member.team, stack)
            leftover.count = 0
        }
        player.sendSystemMessage(Component.literal("获得青金石 ×$lapis"))
    }

    fun facingDefense(player: ServerPlayer, context: UseOnContext?): SiteCoreBlockEntity? {
        val clicked = (context?.level as? ServerLevel)?.getBlockEntity(context.clickedPos) as? SiteCoreBlockEntity
        val site = clicked ?: lookedCore(player) ?: return null
        if (site.isRemoved || site.health <= 0) return null
        val definition = Buildings.byId(site.buildingId) ?: return null
        if (!definition.defensive && !Buildings.isCombatDefense(site.buildingId)) return null
        if (site.team.isEmpty() || site.isAlly(player)) return null
        return site
    }

    fun championArmed(player: ServerPlayer): Boolean {
        if (!Match.isFight() || player.isSpectator) return false
        val member = Match.member(player.uuid) ?: return false
        if (!Classes.isChampion(member.classLocked, member.classId)) return false
        return player.getItemBySlot(EquipmentSlot.HEAD).`is`(Items.DIAMOND_HELMET) &&
            player.getItemBySlot(EquipmentSlot.CHEST).`is`(Items.DIAMOND_CHESTPLATE) &&
            player.getItemBySlot(EquipmentSlot.LEGS).`is`(Items.DIAMOND_LEGGINGS) &&
            player.getItemBySlot(EquipmentSlot.FEET).`is`(Items.DIAMOND_BOOTS)
    }

    fun syncChampion(player: ServerPlayer) {
        val health = player.getAttribute(Attributes.MAX_HEALTH)
        val attack = player.getAttribute(Attributes.ATTACK_DAMAGE)
        if (championArmed(player)) {
            health?.addOrUpdateTransientModifier(championHealth)
            attack?.addOrUpdateTransientModifier(championAttack)
            return
        }
        health?.removeModifier(championHealth.id)
        attack?.removeModifier(championAttack.id)
        if (player.health > player.maxHealth) player.health = player.maxHealth
    }

    fun clearHarmful(player: ServerPlayer) {
        val effects = player.activeEffects.map { it.effect }.toList()
        for (effect in effects) {
            if (effect.value().category == MobEffectCategory.HARMFUL) player.removeEffect(effect)
        }
    }

    private fun moduleLapis(price: Int): Int =
        (price * MatchRules.CONQUEROR_LAPIS_NUM + MatchRules.CONQUEROR_LAPIS_DEN - 1) / MatchRules.CONQUEROR_LAPIS_DEN

    private fun lookedCore(player: ServerPlayer): SiteCoreBlockEntity? {
        val world = player.level()
        val from = player.eyePosition
        val to = from.add(player.lookAngle.scale(MatchRules.CONQUEROR_REACH))
        val hit = world.clip(ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player))
        if (hit.type != HitResult.Type.BLOCK) return null
        return world.getBlockEntity(hit.blockPos) as? SiteCoreBlockEntity
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
        Buildings.WORKSHOP.id -> listOf(
            ingot(Items.DIAMOND, MatchRules.WORKSHOP_PRICE_DIAMOND, "钻石"),
            ingot(Items.LAPIS_LAZULI, MatchRules.WORKSHOP_PRICE_LAPIS, "青金石"),
        )
        Buildings.GUIDED_CANNON.id -> listOf(
            ingot(Items.COPPER_INGOT, MatchRules.GUIDED_CANNON_PRICE_COPPER, "铜锭"),
            ingot(Items.IRON_INGOT, MatchRules.GUIDED_CANNON_PRICE_IRON, "铁锭"),
            ingot(Items.DIAMOND, MatchRules.GUIDED_CANNON_PRICE_DIAMOND, "钻石"),
        )
        Buildings.TELEPORT_SPIRE.id -> listOf(
            ingot(Items.IRON_INGOT, MatchRules.TELEPORT_SPIRE_PRICE_IRON, "铁锭"),
            ingot(Items.COPPER_INGOT, MatchRules.TELEPORT_SPIRE_PRICE_COPPER, "铜锭"),
            ingot(Items.DIAMOND, MatchRules.TELEPORT_SPIRE_PRICE_DIAMOND, "钻石"),
        )
        Buildings.QUAKE.id -> listOf(ingot(Items.DIAMOND, MatchRules.QUAKE_PRICE_DIAMOND, "钻石"))
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
