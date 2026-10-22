package shina.wallwarRebirth

import net.minecraft.resources.Identifier

object WallwarMod {
    const val MOD_ID = "wallwar-rebirth"

    @JvmStatic
    fun idOf(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)
}
