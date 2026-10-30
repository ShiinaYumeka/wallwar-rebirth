package shina.wallwarRebirth.building

import eu.pb4.polymer.core.api.block.PolymerBlockUtils
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.`object`.builder.v1.block.entity.FabricBlockEntityTypeBuilder
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.PushReaction
import shina.wallwarRebirth.WallwarMod

object ModSites {
    lateinit var CORE: SiteCoreBlock
    lateinit var TYPE: BlockEntityType<SiteCoreBlockEntity>
    lateinit var STRUCTURE: SiteStructureBlock
    lateinit var STRUCTURE_TYPE: BlockEntityType<SiteStructureBlockEntity>
    lateinit var SMOOTH_STONE: SitePartBlock
    lateinit var IRON_ORE: SitePartBlock
    lateinit var COPPER_ORE: SitePartBlock
    lateinit var REDSTONE_ORE: SitePartBlock
    lateinit var LAPIS_ORE: SitePartBlock
    lateinit var DEEPSLATE_IRON_ORE: SitePartBlock
    lateinit var DEEPSLATE_DIAMOND_ORE: SitePartBlock
    lateinit var DEEPSLATE_REDSTONE_ORE: SitePartBlock
    lateinit var DEEPSLATE_LAPIS_ORE: SitePartBlock

    fun init() {
        val id = WallwarMod.idOf("site_core")
        val blockKey = ResourceKey.create(Registries.BLOCK, id)
        CORE = SiteCoreBlock(partProperties(blockKey))
        Registry.register(BuiltInRegistries.BLOCK, blockKey, CORE)
        val structureKey = ResourceKey.create(Registries.BLOCK, WallwarMod.idOf("site_structure"))
        STRUCTURE = SiteStructureBlock(partProperties(structureKey))
        Registry.register(BuiltInRegistries.BLOCK, structureKey, STRUCTURE)
        SMOOTH_STONE = part("site_smooth_stone", Blocks.SMOOTH_STONE)
        IRON_ORE = part("site_iron_ore", Blocks.IRON_ORE)
        COPPER_ORE = part("site_copper_ore", Blocks.COPPER_ORE)
        REDSTONE_ORE = part("site_redstone_ore", Blocks.REDSTONE_ORE)
        LAPIS_ORE = part("site_lapis_ore", Blocks.LAPIS_ORE)
        DEEPSLATE_IRON_ORE = part("site_deepslate_iron_ore", Blocks.DEEPSLATE_IRON_ORE)
        DEEPSLATE_DIAMOND_ORE = part("site_deepslate_diamond_ore", Blocks.DEEPSLATE_DIAMOND_ORE)
        DEEPSLATE_REDSTONE_ORE = part("site_deepslate_redstone_ore", Blocks.DEEPSLATE_REDSTONE_ORE)
        DEEPSLATE_LAPIS_ORE = part("site_deepslate_lapis_ore", Blocks.DEEPSLATE_LAPIS_ORE)
        TYPE = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            WallwarMod.idOf("site_core"),
            FabricBlockEntityTypeBuilder.create(::SiteCoreBlockEntity, CORE).build(),
        )
        STRUCTURE_TYPE = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            WallwarMod.idOf("site_structure"),
            FabricBlockEntityTypeBuilder.create(::SiteStructureBlockEntity, STRUCTURE).build(),
        )
        PolymerBlockUtils.registerBlockEntity(TYPE)
        PolymerBlockUtils.registerBlockEntity(STRUCTURE_TYPE)
        PlayerBlockBreakEvents.BEFORE.register { world, player, pos, state, _ ->
            BuildingMining.finish(world, player, pos, state)
        }
    }

    private fun part(path: String, appearance: Block): SitePartBlock {
        val key = ResourceKey.create(Registries.BLOCK, WallwarMod.idOf(path))
        val block = SitePartBlock(partProperties(key), appearance)
        Registry.register(BuiltInRegistries.BLOCK, key, block)
        return block
    }

    private fun partProperties(key: ResourceKey<Block>): BlockBehaviour.Properties =
        BlockBehaviour.Properties.of()
            .setId(key)
            .strength(3.0f, 3600000.0f)
            .sound(SoundType.DEEPSLATE)
            .requiresCorrectToolForDrops()
            .pushReaction(PushReaction.BLOCK)
}
