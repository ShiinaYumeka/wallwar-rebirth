package shina.wallwarRebirth.building

import eu.pb4.polymer.core.api.block.PolymerBlockUtils
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.`object`.builder.v1.block.entity.FabricBlockEntityTypeBuilder
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.block.Block
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

    fun init() {
        val id = WallwarMod.idOf("building_core")
        val blockKey = ResourceKey.create(Registries.BLOCK, id)
        CORE = SiteCoreBlock(partProperties(blockKey))
        Registry.register(BuiltInRegistries.BLOCK, blockKey, CORE)
        val structureKey = ResourceKey.create(Registries.BLOCK, WallwarMod.idOf("building_part"))
        STRUCTURE = SiteStructureBlock(partProperties(structureKey))
        Registry.register(BuiltInRegistries.BLOCK, structureKey, STRUCTURE)
        TYPE = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            WallwarMod.idOf("building_core"),
            FabricBlockEntityTypeBuilder.create(::SiteCoreBlockEntity, CORE).build(),
        )
        STRUCTURE_TYPE = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            WallwarMod.idOf("building_part"),
            FabricBlockEntityTypeBuilder.create(::SiteStructureBlockEntity, STRUCTURE).build(),
        )
        PolymerBlockUtils.registerBlockEntity(TYPE)
        PolymerBlockUtils.registerBlockEntity(STRUCTURE_TYPE)
        PlayerBlockBreakEvents.BEFORE.register { world, player, pos, state, _ ->
            BuildingMining.finish(world, player, pos, state)
        }
    }

    private fun partProperties(key: ResourceKey<Block>): BlockBehaviour.Properties =
        BlockBehaviour.Properties.of()
            .setId(key)
            .strength(3.0f, 3600000.0f)
            .sound(SoundType.DEEPSLATE)
            .requiresCorrectToolForDrops()
            .pushReaction(PushReaction.BLOCK)
}
