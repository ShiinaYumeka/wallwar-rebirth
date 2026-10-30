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
import shina.wallwarRebirth.building.BuildingHealthLabel
import shina.wallwarRebirth.building.TowerArrow

object ModMonsters {
    lateinit var ZOMBIE: EntityType<SummonedZombie>
        private set
    lateinit var TOWER_ARROW: EntityType<TowerArrow>
        private set
    lateinit var HEALTH_LABEL: EntityType<BuildingHealthLabel>
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
        val arrowId = WallwarMod.idOf("tower_arrow")
        val arrowKey = ResourceKey.create(Registries.ENTITY_TYPE, arrowId)
        TOWER_ARROW = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            arrowId,
            EntityType.Builder.of(::TowerArrow, MobCategory.MISC)
                .sized(0.5f, 0.5f)
                .clientTrackingRange(4)
                .updateInterval(2)
                .build(arrowKey),
        )
        PolymerEntityUtils.registerType(TOWER_ARROW)
        val labelId = WallwarMod.idOf("building_health")
        val labelKey = ResourceKey.create(Registries.ENTITY_TYPE, labelId)
        HEALTH_LABEL = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            labelId,
            EntityType.Builder.of(::BuildingHealthLabel, MobCategory.MISC)
                .sized(0.0f, 0.0f)
                .clientTrackingRange(4)
                .updateInterval(20)
                .build(labelKey),
        )
        PolymerEntityUtils.registerType(HEALTH_LABEL)
    }
}
