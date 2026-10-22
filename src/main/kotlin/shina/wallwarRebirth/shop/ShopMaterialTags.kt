package shina.wallwarRebirth.shop

import shina.wallwarRebirth.WallwarMod
import shina.wallwarRebirth.shop.element.Material
import net.minecraft.tags.TagKey

object ShopMaterialTags {
    @JvmField
    val DROP_ON_DEATH = create("drop_on_death")

    @JvmField
    val BUNDLE_WHITELIST = create("bundle_whitelist")

    @JvmField
    val REPOSITORY = create("repository")

    private fun create(name: String): TagKey<Material> =
        TagKey.create(ShopMaterialRegistry.REGISTRY_KEY, WallwarMod.idOf(name))
}
