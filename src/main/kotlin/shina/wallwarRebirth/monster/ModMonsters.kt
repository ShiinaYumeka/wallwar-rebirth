package shina.wallwarRebirth.monster

import eu.pb4.polymer.core.api.entity.PolymerEntityUtils
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricDefaultAttributeRegistry
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.animal.golem.IronGolem
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton
import net.minecraft.world.entity.monster.zombie.Zombie
import shina.wallwarRebirth.WallwarMod
import shina.wallwarRebirth.building.BuildingHealthLabel
import shina.wallwarRebirth.building.TowerArrow
import shina.wallwarRebirth.building.TowerCrossbow

object ModMonsters {
    lateinit var ZOMBIE: EntityType<SummonedZombie>
        private set
    lateinit var SKELETON: EntityType<SummonedSkeleton>
        private set
    lateinit var GOLEM: EntityType<SummonedGolem>
        private set
    lateinit var SUMMON_ARROW: EntityType<SummonArrow>
        private set
    lateinit var TOWER_ARROW: EntityType<TowerArrow>
        private set
    lateinit var HEALTH_LABEL: EntityType<BuildingHealthLabel>
        private set
    lateinit var TOWER_CROSSBOW: EntityType<TowerCrossbow>
        private set

    fun init() {
        ZOMBIE = register("summoned_zombie", ::SummonedZombie, MobCategory.MONSTER, 0.6f, 1.95f, 8)
        FabricDefaultAttributeRegistry.register(ZOMBIE, Zombie.createAttributes())
        SKELETON = register("summoned_skeleton", ::SummonedSkeleton, MobCategory.MONSTER, 0.6f, 1.99f, 8)
        FabricDefaultAttributeRegistry.register(SKELETON, AbstractSkeleton.createAttributes())
        GOLEM = register("summoned_golem", ::SummonedGolem, MobCategory.MISC, 1.4f, 2.7f, 10)
        FabricDefaultAttributeRegistry.register(GOLEM, IronGolem.createAttributes())
        val arrowId = WallwarMod.idOf("summon_arrow")
        val arrowKey = ResourceKey.create(Registries.ENTITY_TYPE, arrowId)
        SUMMON_ARROW = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            arrowId,
            EntityType.Builder.of(::SummonArrow, MobCategory.MISC)
                .sized(0.5f, 0.5f)
                .clientTrackingRange(4)
                .updateInterval(2)
                .build(arrowKey),
        )
        PolymerEntityUtils.registerType(SUMMON_ARROW)
        val towerId = WallwarMod.idOf("tower_arrow")
        val towerKey = ResourceKey.create(Registries.ENTITY_TYPE, towerId)
        TOWER_ARROW = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            towerId,
            EntityType.Builder.of(::TowerArrow, MobCategory.MISC)
                .sized(0.5f, 0.5f)
                .clientTrackingRange(4)
                .updateInterval(2)
                .build(towerKey),
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
        val crossbowId = WallwarMod.idOf("tower_crossbow")
        val crossbowKey = ResourceKey.create(Registries.ENTITY_TYPE, crossbowId)
        TOWER_CROSSBOW = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            crossbowId,
            EntityType.Builder.of(::TowerCrossbow, MobCategory.MISC)
                .sized(0f, 0f)
                .clientTrackingRange(8)
                .updateInterval(2)
                .build(crossbowKey),
        )
        PolymerEntityUtils.registerType(TOWER_CROSSBOW)
        TowerCrossbow.bind(TOWER_CROSSBOW)
    }

    private fun <T : net.minecraft.world.entity.Mob> register(
        path: String,
        factory: EntityType.EntityFactory<T>,
        category: MobCategory,
        width: Float,
        height: Float,
        tracking: Int,
    ): EntityType<T> {
        val id = WallwarMod.idOf(path)
        val key = ResourceKey.create(Registries.ENTITY_TYPE, id)
        val type = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            id,
            EntityType.Builder.of(factory, category)
                .sized(width, height)
                .clientTrackingRange(tracking)
                .noLootTable()
                .build(key),
        )
        PolymerEntityUtils.registerType(type)
        return type
    }
}
