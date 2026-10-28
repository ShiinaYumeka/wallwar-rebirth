package shina.wallwarRebirth.shop.element.type

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import shina.wallwarRebirth.shop.element.ShopItemWithIndex
import net.minecraft.util.StringRepresentable
import java.util.*

sealed class TypedShopItem(private val idx: Int) : ShopItemWithIndex {
    override fun index(): Int = idx

    abstract fun type(): Type

    enum class Type(internal val codec: MapCodec<out TypedShopItem>) : StringRepresentable {
        STATIC(StaticItem.CODEC),
        DYNAMIC_PLAYER(DynamicItemForPlayer.CODEC),
        DYNAMIC_HAPPY_GHAST(DynamicItemForHappyGhast.CODEC),
        SPECIAL_BLOCK_BLAST_UPGRADING(SpecialBlockBlastUpgradeItem.CODEC),
        BRANCH_ENTRY(BranchEntryItem.CODEC),
        BUILDING(BuildingPanelItem.CODEC),
        ;

        private val serializeName: String = this.name.lowercase(Locale.ROOT)
        override fun getSerializedName(): String = serializeName
    }

    companion object {
        private val TYPE_CODEC: Codec<Type> = StringRepresentable.fromEnum(Type::values)

        val CODEC: Codec<TypedShopItem> = TYPE_CODEC.dispatch("type", TypedShopItem::type, Type::codec)
    }
}
