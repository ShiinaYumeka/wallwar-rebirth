package shina.wallwarRebirth.item

import net.minecraft.ChatFormatting
import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomModelData
import net.minecraft.world.item.component.ItemLore
import shina.wallwarRebirth.WallwarMod
import shina.wallwarRebirth.item.core.PolymerInteractionType

object ModItems {
    @JvmField
    val EXAMPLE_TOKEN: Item = register("example_token") { properties ->
        SimpleModdedItem(
            properties = properties.stacksTo(1),
            polymerItem = Items.PAPER,
            interactionType = PolymerInteractionType.CLASSIC,
        )
    }

    @JvmField
    val REINFORCE: Item = module(
        "reinforce_module",
        "强化模块",
        Items.LIGHT_BLUE_GLAZED_TERRACOTTA,
        listOf(
            "使部分建筑的效果增强。",
            "*那是个疯狂的夜晚，禁忌科学让强化核心诞生。",
            "电闪雷鸣，狂风怒号，整个世界都愤怒地否定着。",
            "但是这一切都无济于事，强化核心仍然活着，活着！",
        ),
    )

    @JvmField
    val REINFORCE_TWO: Item = module(
        "reinforce_module_2",
        "强化模块 2",
        Items.LIGHT_GRAY_GLAZED_TERRACOTTA,
        listOf(
            "取消部分建筑的生效范围",
            "*扩散模块生于辽宁省朝阳市，",
            "从小就被亲戚朋友教会了",
            "优秀的侦查以及说闲话技巧，",
            "目前在村委会担当播音员一职。",
        ),
    )

    @JvmField
    val EGG_ZOMBIE: Item = egg("summon_zombie", "唤灵·僵尸", Items.ZOMBIE_SPAWN_EGG, "zombie")

    @JvmField
    val EGG_SKELETON: Item = egg("summon_skeleton", "唤灵·骷髅", Items.SKELETON_SPAWN_EGG, "skeleton")

    @JvmField
    val EGG_GOLEM: Item = egg("summon_golem", "唤灵·铁傀儡", Items.IRON_GOLEM_SPAWN_EGG, "golem")

    @JvmField
    val CORE_REACTOR: Item = core("core_reactor", "矿物反应堆核心", Items.BLAST_FURNACE, "reactor")

    @JvmField
    val CORE_WALL: Item = core("core_wall", "城墙核心", Items.STONE_BRICKS, "wall")

    @JvmField
    val CORE_TOWER: Item = core("core_tower", "弩架核心", Items.CROSSBOW, "tower")

    @JvmField
    val CORE_BARRACKS: Item = core("core_barracks", "兵营核心", Items.IRON_BARS, "barracks")

    fun init() {
        EXAMPLE_TOKEN
        REINFORCE
        REINFORCE_TWO
        EGG_ZOMBIE
        EGG_SKELETON
        EGG_GOLEM
        CORE_REACTOR
        CORE_WALL
        CORE_TOWER
        CORE_BARRACKS
    }

    private fun module(path: String, name: String, icon: Item, lore: List<String>): Item = register(path) { properties ->
        SimpleModdedItem(
            properties = properties.stacksTo(1)
                .component(
                    DataComponents.CUSTOM_NAME,
                    Component.literal(name).withStyle { style -> style.withItalic(false).withColor(ChatFormatting.AQUA) },
                )
                .component(DataComponents.CUSTOM_MODEL_DATA, CustomModelData(listOf(1f), emptyList(), emptyList(), emptyList()))
                .component(DataComponents.LORE, ItemLore(lore.map { line ->
                    Component.literal(line).withStyle { style -> style.withItalic(false).withColor(ChatFormatting.GRAY) }
                })),
            polymerItem = icon,
            interactionType = PolymerInteractionType.CLASSIC,
        )
    }

    private fun egg(path: String, name: String, icon: Item, monsterId: String): Item = register(path) { properties ->
        SummonEggItem(
            properties = properties.component(
                DataComponents.CUSTOM_NAME,
                Component.literal(name).withStyle { style -> style.withItalic(false) },
            ),
            polymerItem = icon,
            monsterId = monsterId,
        )
    }

    private fun core(path: String, name: String, icon: Item, buildingId: String): Item = register(path) { properties ->
        BuildingCoreItem(
            properties = properties.component(
                DataComponents.CUSTOM_NAME,
                Component.literal(name).withStyle { style -> style.withItalic(false) },
            ),
            polymerItem = icon,
            buildingId = buildingId,
        )
    }

    private fun <T : Item> register(path: String, constructor: (Item.Properties) -> T): T {
        val id = WallwarMod.idOf(path)
        val key = ResourceKey.create(Registries.ITEM, id)
        return Registry.register(BuiltInRegistries.ITEM, key, constructor(Item.Properties().setId(key)))
    }
}
