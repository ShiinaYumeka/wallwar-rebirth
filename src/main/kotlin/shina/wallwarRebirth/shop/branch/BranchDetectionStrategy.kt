package shina.wallwarRebirth.shop.branch

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import shina.wallwarRebirth.shop.UpgradeForks
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.StringRepresentable
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.enchantment.Enchantment as McEnchantment

sealed interface BranchDetectionStrategy {
    fun type(): String
    fun detectCurrent(player: ServerPlayer, lookup: HolderLookup.Provider): String?

    data class EnchantmentDetect(
        val slot: EquipmentSlot,
        val branches: Map<String, Map<ResourceKey<McEnchantment>, Int>>
    ) : BranchDetectionStrategy {
        override fun type() = "enchantment"

        override fun detectCurrent(player: ServerPlayer, lookup: HolderLookup.Provider): String? =
            player.getItemBySlot(slot).let { stack ->
                if (stack.isEmpty) return null
                val enchantments = stack.enchantments
                val registry = lookup.lookupOrThrow(Registries.ENCHANTMENT)
                branches.entries.firstOrNull { (_, required) ->
                    required.all { (enchantKey, level) ->
                        val holder = registry.get(enchantKey).orElse(null) ?: return@all false
                        enchantments.getLevel(holder) == level
                    }
                }?.key
            }

        companion object {
            val CODEC: MapCodec<EnchantmentDetect> = RecordCodecBuilder.mapCodec { instance ->
                instance.group(
                    StringRepresentable.fromEnum(EquipmentSlot::values)
                        .fieldOf("slot")
                        .forGetter(EnchantmentDetect::slot),
                    Codec.unboundedMap(
                        Codec.STRING,
                        Codec.unboundedMap(ResourceKey.codec(Registries.ENCHANTMENT), Codec.INT)
                    )
                        .fieldOf("branches")
                        .forGetter(EnchantmentDetect::branches)
                ).apply(instance, ::EnchantmentDetect)
            }
        }
    }

    /** 通过 hgwar itemUpgrade 命令查询玩家的升级等级和 fork，不依赖物品位置 */
    data class CommandForkDetect(
        val upgradeType: String,
        val expectedLevel: Int,
        val branches: Map<String, String>
    ) : BranchDetectionStrategy {
        override fun type() = "command_fork"

        override fun detectCurrent(player: ServerPlayer, lookup: HolderLookup.Provider): String? {
            val forkName = UpgradeForks.query.currentFork(player, upgradeType, expectedLevel) ?: return null
            return branches.entries.firstOrNull { it.value == forkName }?.key
        }

        companion object {
            val CODEC: MapCodec<CommandForkDetect> = RecordCodecBuilder.mapCodec { instance ->
                instance.group(
                    Codec.STRING.fieldOf("upgrade_type").forGetter(CommandForkDetect::upgradeType),
                    Codec.INT.fieldOf("expected_level").forGetter(CommandForkDetect::expectedLevel),
                    Codec.unboundedMap(Codec.STRING, Codec.STRING)
                        .fieldOf("branches")
                        .forGetter(CommandForkDetect::branches)
                ).apply(instance, ::CommandForkDetect)
            }
        }
    }

    companion object {
        val CODEC: Codec<BranchDetectionStrategy> = Codec.STRING.dispatch(BranchDetectionStrategy::type) { type ->
            when (type) {
                "enchantment" -> EnchantmentDetect.CODEC
                "command_fork" -> CommandForkDetect.CODEC
                else -> throw IllegalArgumentException("Unknown branch detection strategy: $type")
            }
        }
    }
}
