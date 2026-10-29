package shina.wallwarRebirth.monster

import eu.pb4.polymer.core.api.entity.PolymerEntityUtils
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricDefaultAttributeRegistry
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.monster.zombie.Zombie
import shina.wallwarRebirth.WallwarMod

object ModMonsters {
    lateinit var ZOMBIE: EntityType<SummonedZombie>
        private set

    fun init() {
        val id = WallwarMod.idOf("summoned_zombie")
        val key = ResourceKey.create(Registries.ENTITY_TYPE, id)
        ZOMBIE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            id,
            EntityType.Builder.of(::SummonedZombie, MobCategory.MONSTER)
                .sized(0.6f, 1.95f)
                .clientTrackingRange(8)
                .noLootTable()
                .build(key),
        )
        FabricDefaultAttributeRegistry.register(ZOMBIE, Zombie.createAttributes())
        PolymerEntityUtils.registerType(ZOMBIE)
    }
}
