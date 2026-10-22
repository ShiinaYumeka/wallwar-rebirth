package shina.wallwarRebirth.shop.branch

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import shina.wallwarRebirth.shop.element.BaseShopItem
import shina.wallwarRebirth.shop.gui.MaterialConsumeAccess
import shina.wallwarRebirth.shop.PlayerShopSettings
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.ExtraCodecs

data class BranchOption(
    val id: String,
    val slotIndex: Int,
    val item: BaseShopItem
) {
    companion object {
        val CODEC: Codec<BranchOption> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("id").forGetter(BranchOption::id),
                Codec.intRange(0, 80).fieldOf("slot").forGetter(BranchOption::slotIndex),
                BaseShopItem.CODEC.fieldOf("item").forGetter(BranchOption::item)
            ).apply(instance, ::BranchOption)
        }
    }
}

data class BranchGroup(
    val title: Component,
    val rows: Int,
    val detect: BranchDetectionStrategy,
    val branches: List<BranchOption>,
    val closeOnBuy: Boolean = false
) {
    companion object {
        val CODEC: Codec<BranchGroup> = RecordCodecBuilder.create { instance ->
            instance.group(
                ComponentSerialization.CODEC.fieldOf("title").forGetter(BranchGroup::title),
                ExtraCodecs.intRange(1, 6).optionalFieldOf("rows", 3).forGetter(BranchGroup::rows),
                BranchDetectionStrategy.CODEC.fieldOf("detect").forGetter(BranchGroup::detect),
                BranchOption.CODEC.listOf().fieldOf("branches").forGetter(BranchGroup::branches),
                Codec.BOOL.optionalFieldOf("close_on_buy", false).forGetter(BranchGroup::closeOnBuy)
            ).apply(instance, ::BranchGroup)
        }
    }

    /** 是否存在至少一个玩家可购买（非当前分支且材料充足）的分支 */
    fun hasPurchasableBranch(player: ServerPlayer): Boolean {
        val considerRepo = PlayerShopSettings.of(player).shopTeamRepoEnabled
        val currentId = detect.detectCurrent(player, player.level().registryAccess())
        return branches.any { branch ->
            branch.id != currentId &&
                MaterialConsumeAccess.hasEnoughMaterials(player, branch.item.costs, considerRepo)
        }
    }
}
