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
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.item.component.ItemLore
import shina.wallwarRebirth.WallwarMod
import shina.wallwarRebirth.item.core.PolymerInteractionType

object ModItems {
    @JvmField
    val MENU: Item = register("match_menu") { properties ->
        MatchMenuItem(
            properties = properties.stacksTo(1)
                .component(
                    DataComponents.CUSTOM_NAME,
                    Component.literal("大厅终端").withStyle { style -> style.withItalic(false).withColor(ChatFormatting.WHITE) },
                )
                .component(
                    DataComponents.LORE,
                    ItemLore(listOf(
                        Component.literal("右键打开").withStyle { style -> style.withItalic(false).withColor(ChatFormatting.GRAY) },
                    )),
                ),
            polymerItem = Items.COMPASS,
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
            "使部分建筑的效果增强。",
            "*他是强化核心的姐妹，强化核心二号，他一直不满意自己的名字。",
            "因为他觉得自己的名字是因为父母想不出新名字随便起的一样。",
        ),
    )

    @JvmField
    val DIFFUSION: Item = module(
        "diffusion_module",
        "扩散模块",
        Items.PURPLE_GLAZED_TERRACOTTA,
        listOf(
            "取消部分建筑的生效范围",
            "*扩散模块生于辽宁省朝阳市，",
            "从小就被亲戚朋友教会了",
            "优秀的侦查以及说闲话技巧，",
            "目前在村委会担当播音员一职。",
        ),
    )

    @JvmField
    val SKILL_MINER: Item = skill("skill_golden_hour", "黄金时刻", Items.CLOCK, "手持右键获得急迫 I，持续 30 秒，冷却 60 秒")

    @JvmField
    val SKILL_WRECKER: Item = skill("skill_practiced", "娴熟技巧", Items.EXPERIENCE_BOTTLE, "手持右键后 30 秒内挖掘建筑伤害翻倍，冷却 60 秒")

    @JvmField
    val SKILL_BASTION: Item = skill("skill_binder", "强力粘合剂", Items.SLIME_BALL, "手持右键己方城墙核心，恢复当前生命上限的 50%，冷却 60 秒")

    @JvmField
    val SKILL_SWORDSMAN: Item = skill("skill_sprint", "急速冲刺", Items.FEATHER, "手持右键获得速度 II，持续 5 秒，冷却 30 秒")

    @JvmField
    val TERMINAL: Item = register("portable_terminal") { properties ->
        PortableTerminalItem(
            properties = properties.stacksTo(1)
                .component(
                    DataComponents.CUSTOM_NAME,
                    Component.literal("局内终端").withStyle { style -> style.withItalic(false).withColor(ChatFormatting.WHITE) },
                )
                .component(
                    DataComponents.LORE,
                    ItemLore(listOf(
                        Component.literal("右键打开局内终端").withStyle { style ->
                            style.withItalic(false).withColor(ChatFormatting.GRAY)
                        },
                    )),
                ),
            polymerItem = Items.RECOVERY_COMPASS,
        )
    }

    @JvmField
    val RETURN_LOBBY: Item = register("return_lobby") { properties ->
        ReturnLobbyItem(
            properties = properties.stacksTo(1)
                .component(
                    DataComponents.CUSTOM_NAME,
                    Component.literal("返回大厅").withStyle { style -> style.withItalic(false).withColor(ChatFormatting.WHITE) },
                )
                .component(
                    DataComponents.LORE,
                    ItemLore(listOf(
                        Component.literal("点击返回大厅，对局继续进行").withStyle { style ->
                            style.withItalic(false).withColor(ChatFormatting.GRAY)
                        },
                    )),
                ),
            polymerItem = Items.OAK_DOOR,
        )
    }

    @JvmField
    val EGG_ZOMBIE: Item = egg("summon_zombie", "唤灵·僵尸", Items.ZOMBIE_SPAWN_EGG, "zombie")

    @JvmField
    val EGG_SKELETON: Item = egg("summon_skeleton", "唤灵·骷髅", Items.SKELETON_SPAWN_EGG, "skeleton")

    @JvmField
    val EGG_GOLEM: Item = egg("summon_golem", "唤灵·铁傀儡", Items.IRON_GOLEM_SPAWN_EGG, "golem")

    @JvmField
    val EGG_SPIDER: Item = egg("summon_spider", "唤灵·蜘蛛", Items.SPIDER_SPAWN_EGG, "spider")

    @JvmField
    val EGG_MINER: Item = egg("summon_miner", "矿工僵尸", Items.ZOMBIE_SPAWN_EGG, "miner_zombie")

    @JvmField
    val EGG_HUSK: Item = egg("summon_husk", "唤灵·尸壳", Items.HUSK_SPAWN_EGG, "husk")

    @JvmField
    val EGG_DEATH_KNIGHT: Item = egg("summon_death_knight", "唤灵·骷髅死士", Items.SKELETON_SPAWN_EGG, "death_knight")

    @JvmField
    val EGG_CREEPER: Item = egg("summon_creeper", "唤灵·苦力怕", Items.CREEPER_SPAWN_EGG, "creeper")

    @JvmField
    val EGG_WARRIOR: Item = egg("summon_warrior", "唤灵·僵尸战士", Items.ZOMBIE_SPAWN_EGG, "zombie_warrior")

    @JvmField
    val EGG_PILLAGER: Item = egg("summon_pillager", "唤灵·掠夺者", Items.PILLAGER_SPAWN_EGG, "pillager")

    @JvmField
    val EGG_VINDICATOR: Item = egg("summon_vindicator", "唤灵·卫道士", Items.VINDICATOR_SPAWN_EGG, "vindicator")

    @JvmField
    val EGG_EVOKER: Item = egg("summon_evoker", "唤灵·幻魔者", Items.EVOKER_SPAWN_EGG, "evoker")

    @JvmField
    val EGG_ILLUSIONER: Item = egg("summon_illusioner", "唤灵·幻术师", Items.EVOKER_SPAWN_EGG, "illusioner")

    @JvmField
    val EGG_VILLAGER: Item = egg("summon_villager", "唤灵·村民", Items.VILLAGER_SPAWN_EGG, "villager")

    @JvmField
    val EGG_RAVAGER: Item = egg("summon_ravager", "唤灵·劫掠兽", Items.RAVAGER_SPAWN_EGG, "ravager")

    @JvmField
    val CORE_WALL: Item = core("core_wall", "城墙核心", Items.STONE_BRICKS, "wall")

    @JvmField
    val CORE_TOWER: Item = core("core_tower", "弩架核心", Items.CROSSBOW, "tower")

    @JvmField
    val CORE_TESLA: Item = core("core_tesla", "特斯拉塔核心", Items.LIGHTNING_ROD, "tesla")

    @JvmField
    val CORE_BARRACKS: Item = core("core_barracks", "兵营核心", Blocks.CRAFTER.asItem(), "barracks")

    @JvmField
    val CORE_CANNON: Item = core("core_cannon", "炮台核心", Items.DISPENSER, "cannon")

    @JvmField
    val CORE_RELAY: Item = core("core_relay", "传输装置核心", Items.HOPPER, "relay")

    @JvmField
    val CORE_PILLAGER: Item = core("core_pillager", "掠夺者哨塔核心", Items.WHITE_BANNER, "pillager")

    @JvmField
    val CORE_FARMLAND: Item = core("core_farmland", "农田核心", Items.COMPOSTER, "farmland")

    fun init() {
        MENU
        SKILL_MINER
        SKILL_WRECKER
        SKILL_BASTION
        SKILL_SWORDSMAN
        REINFORCE
        REINFORCE_TWO
        DIFFUSION
        TERMINAL
        RETURN_LOBBY
        EGG_ZOMBIE
        EGG_SKELETON
        EGG_GOLEM
        EGG_SPIDER
        EGG_MINER
        EGG_HUSK
        EGG_DEATH_KNIGHT
        EGG_CREEPER
        EGG_WARRIOR
        EGG_PILLAGER
        EGG_VINDICATOR
        EGG_EVOKER
        EGG_ILLUSIONER
        EGG_RAVAGER
        EGG_VILLAGER
        CORE_WALL
        CORE_TOWER
        CORE_TESLA
        CORE_BARRACKS
        CORE_CANNON
        CORE_RELAY
        CORE_PILLAGER
        CORE_FARMLAND
    }

    private fun skill(path: String, name: String, icon: Item, lore: String): Item = register(path) { properties ->
        SkillItem(
            properties = properties.stacksTo(1)
                .component(
                    DataComponents.CUSTOM_NAME,
                    Component.literal(name).withStyle { style -> style.withItalic(false).withColor(ChatFormatting.AQUA) },
                )
                .component(
                    DataComponents.LORE,
                    ItemLore(listOf(
                        Component.literal(lore).withStyle { style -> style.withItalic(false).withColor(ChatFormatting.GRAY) },
                    )),
                ),
            polymerItem = icon,
        )
    }

    private fun module(path: String, name: String, icon: Item, lore: List<String>): Item = register(path) { properties ->
        SimpleModdedItem(
            properties = properties.stacksTo(1)
                .component(
                    DataComponents.CUSTOM_NAME,
                    Component.literal(name).withStyle { style -> style.withItalic(false).withColor(ChatFormatting.AQUA) },
                )
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
