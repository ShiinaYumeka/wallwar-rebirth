package shina.wallwarRebirth.shop.gui

import shina.wallwarRebirth.shop.PlayerShopSettings
import shina.wallwarRebirth.shop.ShopMaterialRegistry
import shina.wallwarRebirth.shop.TeamStores
import shina.wallwarRebirth.shop.element.Material
import shina.wallwarRebirth.util.playNotifySound
import shina.wallwarRebirth.util.text.ItemTextComponents
import shina.wallwarRebirth.util.text.component
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.ItemStack

interface MaterialConsumeAccess {
    data class DeductBreakdown(
        val fromPlayerInventory: Map<Identifier, Int>,
        val fromTeamRepo: Map<Identifier, Int>,
        /** Over-cap portion of [fromTeamRepo] (invalid withdraw counts). */
        val fromTeamRepoInvalid: Map<Identifier, Int> = emptyMap(),
    )

    sealed interface DeductResult {
        /** [teamRepoSuffix] 为消耗了团队箱材料时的列表后缀，未消耗时为 null */
        data class Success(
            val teamRepoSuffix: Component?,
            val breakdown: DeductBreakdown?,
        ) : DeductResult

        object InsufficientMaterials : DeductResult
    }

    fun shouldConsiderTeamRepo(): Boolean =
        PlayerShopSettings.of(getPlayer()).shopTeamRepoEnabled

    fun getPlayer(): ServerPlayer

    fun playFailSound() {
        getPlayer().playNotifySound(SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.PLAYERS, 1.4f, 0.95f)
    }

    fun tryDeduct(costs: Map<Identifier, Int>): DeductResult {
        val player = this.getPlayer()
        if (!player.isCreative) {
            if (!hasEnoughMaterials(costs)) {
                notifyNotEnoughMaterial(costs)
                return DeductResult.InsufficientMaterials
            }
        }
        val deductResult = if (player.isCreative) {
            DeductResult.Success(null, null)
        } else {
            deductMaterials(costs)
        }
        if (deductResult !is DeductResult.Success) {
            playFailSound()
        }
        return deductResult
    }

    /** 检查材料是否足够 */
    fun hasEnoughMaterials(costs: Map<Identifier, Int>): Boolean = hasEnoughMaterials(
        player = getPlayer(),
        costs = costs,
        shouldConsiderTeamRepo = shouldConsiderTeamRepo()
    )

    /** 计算材料差额 */
    fun countDeltaForMaterial(material: Material, requirement: Int): Int = countDeltaForMaterial(
        player = getPlayer(),
        material = material,
        requirement = requirement,
        shouldConsiderTeamRepo = shouldConsiderTeamRepo()
    )

    /** 扣除材料（优先背包，不足时从仓库扣）
     *
     * @return [DeductResult.Success] 扣除成功，[DeductResult.Success.teamRepoSuffix] 为消耗了
     *   团队箱时的材料列表后缀（未消耗时为 null）；[DeductResult.InsufficientMaterials] 材料不足扣除失败。
     */
    fun deductMaterials(costs: Map<Identifier, Int>): DeductResult {
        val player = this.getPlayer()
        val considerTeamRepo = this.shouldConsiderTeamRepo()

        if (!hasEnoughMaterials(player, costs, considerTeamRepo)) return DeductResult.InsufficientMaterials

        val playerTeam = player.team
        val teamRepoUsed = mutableMapOf<Material, Int>()
        val fromPlayerInventory = mutableMapOf<Identifier, Int>()
        val fromTeamRepo = mutableMapOf<Identifier, Int>()
        val fromTeamRepoInvalid = mutableMapOf<Identifier, Int>()

        for ((materialId, amount) in costs) {
            val material = ShopMaterialRegistry.getMaterial(player.registryAccess(), materialId)
            checkNotNull(material)

            val deductedFromPlayer = player.inventory.clearOrCountMatchingItems(
                material.itemFilter,
                amount,
                player.inventoryMenu.craftSlots
            )
            if (deductedFromPlayer > 0) {
                fromPlayerInventory[materialId] = (fromPlayerInventory[materialId] ?: 0) + deductedFromPlayer
            }

            val remaining = amount - deductedFromPlayer
            if (remaining > 0 && playerTeam != null && considerTeamRepo) {
                val repository = TeamStores.find(player, playerTeam)
                    ?: return DeductResult.InsufficientMaterials
                if (!repository.deductMaterial(material, remaining)) {
                    return DeductResult.InsufficientMaterials
                }
                teamRepoUsed[material] = remaining
                fromTeamRepo[materialId] = (fromTeamRepo[materialId] ?: 0) + remaining
            }
        }

        val breakdown = DeductBreakdown(
            fromPlayerInventory = fromPlayerInventory.toMap(),
            fromTeamRepo = fromTeamRepo.toMap(),
            fromTeamRepoInvalid = fromTeamRepoInvalid.toMap(),
        )

        if (teamRepoUsed.isNotEmpty()) {
            val shopLines = teamRepoUsed.map { (material, amount) -> material.representativeStack to amount }
            val materialListSuffix = shopMaterialSuffix(shopLines)
            val yellowLineStyle = Style.EMPTY.withColor(ChatFormatting.YELLOW).withItalic(false)
            player.playNotifySound(SoundEvents.CHEST_OPEN, SoundSource.PLAYERS, 1.3f, 1f)

            val level = player.level()
            val server = level.server
            player.team?.players.orEmpty().asSequence()
                .mapNotNull { server.playerList.getPlayerByName(it) }
                .filter {
                    it != player && it.level() == level && PlayerShopSettings.of(it).teamRepoNotifyMode.receivesWithdraw()
                }
                .forEach { teammate ->
                    teammate.sendSystemMessage(
                        component {
                            empty()
                            raw(ItemTextComponents.actorDisplayNameWithEntityHover(player))
                            text(" 使用了团队材料") provide yellowLineStyle
                            raw(materialListSuffix)
                        }
                    )
                }
            return DeductResult.Success(materialListSuffix, breakdown)
        }
        return DeductResult.Success(null, breakdown)
    }

    fun notifyNotEnoughMaterial(costs: Map<Identifier, Int>) {
        val player = this.getPlayer()
        player.sendSystemMessage(
            component {
                text("还缺少 ") formatted ChatFormatting.RED
                costs.forEach { (materialId, amount) ->
                    val material = ShopMaterialRegistry.getMaterial(player.registryAccess(), materialId)
                        ?: return@forEach
                    val delta = countDeltaForMaterial(material, amount)
                    if (delta > 0) {
                        raw(material.description) formatted ChatFormatting.RED
                        text("*$delta ")
                    }
                }
            }
        )
        playFailSound()
    }

    companion object {
        @JvmStatic
        fun countDeltaForMaterial(
            player: ServerPlayer,
            material: Material,
            requirement: Int,
            shouldConsiderTeamRepo: Boolean
        ): Int {
            if (player.isCreative) return 0

            val playerHas = player.inventory.clearOrCountMatchingItems(
                material.itemFilter,
                0,
                player.inventoryMenu.craftSlots
            )

            var teamRepoHas = 0
            if (shouldConsiderTeamRepo) {
                val playerTeam = player.team
                if (playerTeam != null) {
                    TeamStores.find(player, playerTeam)?.let { repo ->
                        teamRepoHas = repo.countMaterialAmount(material)
                    }
                }
            }

            return (requirement - playerHas - teamRepoHas).coerceAtLeast(0)
        }

        @JvmStatic
        fun hasEnoughMaterials(
            player: ServerPlayer,
            costs: Map<Identifier, Int>,
            shouldConsiderTeamRepo: Boolean
        ): Boolean = if (player.isCreative || costs.isEmpty()) {
            true
        } else {
            costs.all { (materialId, amount) ->
                val material = ShopMaterialRegistry.getMaterial(player.registryAccess(), materialId) ?: return false
                countDeltaForMaterial(player, material, amount, shouldConsiderTeamRepo) == 0
            }
        }

        private fun shopMaterialSuffix(entries: List<Pair<ItemStack, Int>>): Component {
            val root = Component.empty()
            root.append(Component.literal("§6[$] "))
            if (entries.isEmpty()) return root
            ItemTextComponents.appendItemGrid(root, entries)
            return root
        }
    }
}
