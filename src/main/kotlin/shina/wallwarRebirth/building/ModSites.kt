package shina.wallwarRebirth.building

import eu.pb4.polymer.core.api.block.PolymerBlockUtils
import net.fabricmc.fabric.api.`object`.builder.v1.block.entity.FabricBlockEntityTypeBuilder
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import shina.wallwarRebirth.WallwarMod
import shina.wallwarRebirth.shop.ShopBuyListener
import shina.wallwarRebirth.shop.ShopHooks
import shina.wallwarRebirth.shop.element.type.BuildingPanelItem

object ModSites {
    lateinit var CORE: SiteCoreBlock
    lateinit var TYPE: BlockEntityType<SiteCoreBlockEntity>

    fun init() {
        val id = WallwarMod.idOf("site_core")
        val blockKey = ResourceKey.create(Registries.BLOCK, id)
        CORE = SiteCoreBlock(
            BlockBehaviour.Properties.of().setId(blockKey).strength(1.5f).sound(SoundType.STONE),
        )
        Registry.register(BuiltInRegistries.BLOCK, blockKey, CORE)
        TYPE = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            WallwarMod.idOf("site_core"),
            FabricBlockEntityTypeBuilder.create(::SiteCoreBlockEntity, CORE).build(),
        )
        PolymerBlockUtils.registerBlockEntity(TYPE)
        ShopHooks.buyListeners += ShopBuyListener { _, player, shopItem, _, _, _, _ ->
            val panel = shopItem as? BuildingPanelItem ?: return@ShopBuyListener
            if (panel.role != BuildingPanelItem.Role.UPGRADE) return@ShopBuyListener
            panel.site?.tryUpgrade(player)
        }
    }
}
