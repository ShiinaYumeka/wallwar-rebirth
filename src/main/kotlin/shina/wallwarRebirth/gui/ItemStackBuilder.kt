package shina.wallwarRebirth.gui

import com.google.common.collect.ImmutableList
import shina.wallwarRebirth.item.ModComponents
import shina.wallwarRebirth.shop.PlayerShopSettings
import shina.wallwarRebirth.shop.ShopMaterialRegistry
import shina.wallwarRebirth.shop.element.BaseShopItem
import shina.wallwarRebirth.shop.gui.MaterialConsumeAccess
import shina.wallwarRebirth.util.text.TextStyles
import shina.wallwarRebirth.util.text.component
import shina.wallwarRebirth.util.text.style
import net.minecraft.ChatFormatting
import net.minecraft.advancements.criterion.BlockPredicate
import net.minecraft.core.Holder
import net.minecraft.core.HolderLookup
import net.minecraft.core.HolderSet
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.tags.DamageTypeTags
import net.minecraft.tags.TagKey
import net.minecraft.util.Unit
import net.minecraft.world.entity.EquipmentSlotGroup
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.item.*
import net.minecraft.world.item.component.*
import net.minecraft.world.item.component.BlocksAttacks.DamageReduction
import net.minecraft.world.item.component.BlocksAttacks.ItemDamageFunction
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.ItemEnchantments
import net.minecraft.world.item.equipment.ArmorMaterial
import net.minecraft.world.item.equipment.ArmorType
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.storage.loot.functions.SetNameFunction
import java.util.*
import java.util.function.Consumer
import kotlin.jvm.optionals.getOrNull

@Suppress("unused", "TooManyFunctions")
class ItemStackBuilder private constructor(private val stack: ItemStack) {
    companion object {
        private val PARSED_NULL = Component.literal("未知材料")
            .withStyle(ChatFormatting.RED, ChatFormatting.ITALIC)

        private val COSTS_LINE = Component.literal("消耗")
            .setStyle(TextStyles.LIGHTER_GRAY_ITEM_LORE)

        private val EMPTY_BLOCK_PREDICATE = BlockPredicate.Builder.block().build()

        @JvmStatic
        fun of(item: Item): ItemStackBuilder = ItemStackBuilder(item.defaultInstance)

        @JvmStatic
        fun of(stack: ItemStack): ItemStackBuilder = ItemStackBuilder(stack.copy())

        /*@JvmStatic
        fun firework(color: Int, flight: Int, type: FireworkExplosion.Shape): ItemStackBuilder {
            val rocket = ItemStack(Items.FIREWORK_ROCKET, 1)

            rocket.set(DataComponents.FIREWORKS, Fireworks(
                flight,
                listOf(FireworkExplosion(type, IntList.of(color), IntList.of(), false, false))
            ))

            return ItemStackBuilder(rocket)
        }*/

        @JvmStatic
        val SHIELD_BLOCK = BlocksAttacks(
            0.25f,
            1.0f,
            listOf(DamageReduction(90.0f, Optional.empty(), 0.0f, 1.0f)),
            ItemDamageFunction(3.0f, 1.0f, 1.0f),
            Optional.of(DamageTypeTags.BYPASSES_SHIELD),
            Optional.of(SoundEvents.SHIELD_BLOCK),
            Optional.of(SoundEvents.SHIELD_BREAK)
        )
    }

    fun setCount(count: Int): ItemStackBuilder = apply { stack.count = count }

    fun transmute(item: Item): ItemStackBuilder {
        if (item == Items.AIR) return of(ItemStack.EMPTY)

        var attributeModifiers = this.stack.getOrDefault(
            DataComponents.ATTRIBUTE_MODIFIERS,
            ItemAttributeModifiers.EMPTY
        )
        item.components()[DataComponents.ATTRIBUTE_MODIFIERS]?.modifiers?.forEach { entry ->
            attributeModifiers = attributeModifiers.withModifierAdded(entry.attribute, entry.modifier, entry.slot)
        }

        return of(this.stack.transmuteCopy(item)).set(DataComponents.ATTRIBUTE_MODIFIERS, attributeModifiers)
    }

    fun hideComponent(dataComponentType: DataComponentType<*>, hide: Boolean): ItemStackBuilder = apply {
        val tooltipDisplay = stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT)
        stack[DataComponents.TOOLTIP_DISPLAY] = tooltipDisplay.withHidden(dataComponentType, hide)
    }

    fun setEnchantment(
        player: ServerPlayer,
        enchantment: ResourceKey<Enchantment>,
        level: Int,
        add: Boolean = false
    ): ItemStackBuilder = setEnchantment(player.level(), enchantment, level, add)

    private fun setEnchantment(
        server: MinecraftServer,
        enchantment: ResourceKey<Enchantment>,
        level: Int,
        add: Boolean = false
    ): ItemStackBuilder = setEnchantment(server.registryAccess(), enchantment, level, add)

    fun setEnchantment(
        world: Level,
        enchantment: ResourceKey<Enchantment>,
        level: Int,
        add: Boolean = false
    ): ItemStackBuilder = setEnchantment(world.registryAccess(), enchantment, level, add)

    private fun setEnchantment(
        lookup: HolderLookup.Provider,
        enchantment: ResourceKey<Enchantment>,
        level: Int,
        add: Boolean
    ): ItemStackBuilder =
        setEnchantment(lookup.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment), level, add)

    private fun setEnchantment(enchantment: Holder<Enchantment>, level: Int, add: Boolean): ItemStackBuilder = apply {
        // 必须读原始 base：stack.enchantments 会被合并进配件贡献，写回会把贡献固化进 base
        val itemEnchantments = ItemEnchantments.Mutable(stack.enchantments)
        if (add) {
            itemEnchantments[enchantment] = itemEnchantments.getLevel(enchantment) + level
        } else {
            itemEnchantments[enchantment] = level
        }
        stack[DataComponents.ENCHANTMENTS] = itemEnchantments.toImmutable()
    }

    fun resetEnchantments(): ItemStackBuilder = apply {
        stack[DataComponents.ENCHANTMENTS] = null
    }

    fun enableShieldBlocking(): ItemStackBuilder = apply {
        stack[DataComponents.BLOCKS_ATTACKS] = SHIELD_BLOCK
    }

    fun disableShieldBlocking(): ItemStackBuilder = apply {
        stack[DataComponents.BLOCKS_ATTACKS] = null
    }

    fun <T : Any> set(type: DataComponentType<T>, value: T?): ItemStackBuilder = apply { stack[type] = value }

    fun setUnbreakable(): ItemStackBuilder = apply { stack[DataComponents.UNBREAKABLE] = Unit.INSTANCE }

    fun setHiddenUnbreakable(): ItemStackBuilder = setUnbreakable().hideComponent(DataComponents.UNBREAKABLE, true)

    fun setName(
        text: Component,
        target: SetNameFunction.Target = SetNameFunction.Target.CUSTOM_NAME
    ): ItemStackBuilder = apply {
        stack[target.component()] = text
    }

    fun setItemName(translationKey: String) = apply {
        setName(Component.translatable(translationKey), SetNameFunction.Target.ITEM_NAME)
    }

    fun setItemName(translationKey: String, chatFormatting: ChatFormatting) = apply {
        setName(Component.translatable(translationKey).withStyle(chatFormatting), SetNameFunction.Target.ITEM_NAME)
    }

    fun unsetName(): ItemStackBuilder = apply { stack.remove(DataComponents.CUSTOM_NAME) }

    fun setPriorLore(texts: List<Component>): ItemStackBuilder = apply {
        stack[ModComponents.PRIOR_ITEM_LORE] = ItemLore(texts)
    }

    fun setPriorLore(vararg texts: Component): ItemStackBuilder = apply {
        stack[ModComponents.PRIOR_ITEM_LORE] = ItemLore(texts.toList())
    }

    fun setLore(texts: List<Component>): ItemStackBuilder = apply {
        stack[DataComponents.LORE] = ItemLore(texts)
    }

    fun setLore(vararg texts: Component): ItemStackBuilder = apply {
        stack[DataComponents.LORE] = ItemLore(texts.toList())
    }

    fun appendLore(text: Component): ItemStackBuilder = apply {
        val loreComponent = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY)
        stack[DataComponents.LORE] = loreComponent.withLineAdded(text)
    }

    fun appendLore(vararg texts: Component) = appendLore(texts.toList())

    fun appendLore(texts: List<Component>): ItemStackBuilder = apply {
        val loreComponent = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY)
        val newList = ImmutableList.builderWithExpectedSize<Component>(loreComponent.lines.size + 1)
            .addAll(loreComponent.lines).addAll(texts).build()
        stack[DataComponents.LORE] = ItemLore(newList)
    }

    fun prependLore(text: Component): ItemStackBuilder = apply {
        val loreComponent = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY)
        val newList = ImmutableList.builderWithExpectedSize<Component>(loreComponent.lines.size + 1)
            .add(text).addAll(loreComponent.lines).build()
        stack[DataComponents.LORE] = ItemLore(newList)
    }

    fun prependLore(vararg texts: Component): ItemStackBuilder = prependLore(texts.toList())

    fun prependLore(texts: List<Component>): ItemStackBuilder = apply {
        val loreComponent = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY)
        val newList = ImmutableList.builderWithExpectedSize<Component>(loreComponent.lines.size + 1)
            .addAll(texts).addAll(loreComponent.lines).build()
        stack[DataComponents.LORE] = ItemLore(newList)
    }

    fun setItemModel(item: Item): ItemStackBuilder = apply {
        stack[DataComponents.ITEM_MODEL] = item.components()[DataComponents.ITEM_MODEL]
    }

    fun addModifier(
        attribute: Holder<Attribute>,
        modifier: AttributeModifier,
        slot: EquipmentSlotGroup
    ): ItemStackBuilder = apply {
//        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers(listOf(ItemAttributeModifiers.EffectEntry(attribute, modifier, slot))))
        val itemAttributes = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
        stack[DataComponents.ATTRIBUTE_MODIFIERS] = itemAttributes.withModifierAdded(attribute, modifier, slot)
    }

    fun addModifier(
        attribute: Holder<Attribute>,
        modifier: AttributeModifier,
        slot: EquipmentSlotGroup,
        display: ItemAttributeModifiers.Display
    ): ItemStackBuilder = apply {
        val itemAttributes = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
        val builder = ItemAttributeModifiers.builder()
        for (entry in itemAttributes.modifiers()) {
            builder.add(entry.attribute(), entry.modifier, entry.slot(), entry.display())
        }
        builder.add(attribute, modifier, slot, display)
        stack[DataComponents.ATTRIBUTE_MODIFIERS] = builder.build()
    }

    fun removeModifier(
        attribute: Holder<Attribute>,
        identifier: Identifier
    ): ItemStackBuilder = apply {
        val itemAttributes = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
        if (itemAttributes == ItemAttributeModifiers.EMPTY) return@apply
        val newModifierBuilder: ImmutableList.Builder<ItemAttributeModifiers.Entry> = ImmutableList.builder()
        for (entry in itemAttributes.modifiers) {
            if (entry.attribute() == attribute && entry.modifier.id == identifier) continue
            newModifierBuilder.add(entry)
        }
        stack[DataComponents.ATTRIBUTE_MODIFIERS] = ItemAttributeModifiers(newModifierBuilder.build())
    }

    fun updateCustomData(function: Consumer<CompoundTag>): ItemStackBuilder = apply {
        val nbtComponent = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
        stack[DataComponents.CUSTOM_DATA] = nbtComponent.update(function)
    }

    fun withAdventureCanBreak(): ItemStackBuilder = apply {
        stack[DataComponents.CAN_BREAK] = AdventureModePredicate(listOf(EMPTY_BLOCK_PREDICATE))
    }

    fun setArmorFromMaterial(
        armorMaterial: ArmorMaterial,
        specificArmorType: ArmorType
    ): ItemStackBuilder = apply {
        armorMaterial.createAttributes(specificArmorType).modifiers().forEach { entry ->
            addModifier(entry.attribute(), entry.modifier(), entry.slot())
        }
    }

    fun setTool(tool: Tool): ItemStackBuilder = apply { stack[DataComponents.TOOL] = tool }

    fun setToolFromMaterial(
        player: ServerPlayer,
        material: ToolMaterial,
        mineAndDropBlocks: TagKey<Block>,
        extraMiningSpeed: Float = 0f
    ): ItemStackBuilder = setToolFromMaterial(
        player.level().registryAccess(),
        material,
        mineAndDropBlocks,
        extraMiningSpeed
    )

    fun setToolFromMaterial(
        provider: HolderLookup.Provider,
        material: ToolMaterial,
        mineAndDropBlocks: TagKey<Block>,
        extraMiningSpeed: Float
    ): ItemStackBuilder = apply {
        val holderGetter = provider.lookupOrThrow(Registries.BLOCK)
        val correctBlocks = holderGetter[mineAndDropBlocks].getOrNull()
        val incorrectBlocks = holderGetter[material.incorrectBlocksForDrops].getOrNull()

        val ruleList = buildList {
            if (incorrectBlocks != null) {
                add(Tool.Rule.deniesDrops(incorrectBlocks))
            }
            if (correctBlocks != null) {
                add(Tool.Rule.minesAndDrops(correctBlocks, material.speed + extraMiningSpeed))
            }
        }
        val tool = Tool(ruleList, 1.0f, 1, true)
        stack[DataComponents.TOOL] = tool
    }

    fun withToolRule(
        blocks: HolderSet<Block>,
        speed: Float?,
        correctForDrops: Boolean?,
    ): ItemStackBuilder = apply {
        val tool = stack.getOrDefault(DataComponents.TOOL, Tool(emptyList(), 1.0f, 1, true))
        val newRules = buildList {
            add(Tool.Rule(blocks, Optional.ofNullable(speed), Optional.ofNullable(correctForDrops)))
            addAll(tool.rules)
        }
        stack[DataComponents.TOOL] = Tool(
            newRules,
            tool.defaultMiningSpeed,
            tool.damagePerBlock,
            tool.canDestroyBlocksInCreative
        )
    }

    fun setSpearKinetic(
        swingDuration: Float,
        damageMultiplier: Float,
        delay: Float,
        dismountMaxDuration: Float,
        dismountMinSpeed: Float,
        knockbackMaxDuration: Float,
        knockbackMinSpeed: Float,
        damageMaxDuration: Float,
        damageMinSpeed: Float
    ): ItemStackBuilder = apply {
        stack[DataComponents.KINETIC_WEAPON] = KineticWeapon(
            10,
            (delay * 20.0f).toInt(),
            KineticWeapon.Condition.ofAttackerSpeed((dismountMaxDuration * 20.0f).toInt(), dismountMinSpeed),
            KineticWeapon.Condition.ofAttackerSpeed((knockbackMaxDuration * 20.0f).toInt(), knockbackMinSpeed),
            KineticWeapon.Condition.ofRelativeSpeed((damageMaxDuration * 20.0f).toInt(), damageMinSpeed),
            0.38f,
            damageMultiplier,
            Optional.of(SoundEvents.SPEAR_USE),
            Optional.of(SoundEvents.SPEAR_HIT)
        )
        stack[DataComponents.SWING_ANIMATION] = SwingAnimation(SwingAnimationType.STAB, (swingDuration * 20f).toInt())
    }

    /**
     * 为物品添加材料信息
     */
    fun addCostInfo(player: ServerPlayer, baseItem: BaseShopItem, locked: Boolean): ItemStackBuilder =
        addCostInfo(player, baseItem, locked, null)

    /**
     * 为物品添加材料信息，[hasEnoughMaterialsOverride] 非 null 时使用它替代从 costs 计算的结果
     */
    fun addCostInfo(
        player: ServerPlayer,
        baseItem: BaseShopItem,
        locked: Boolean,
        hasEnoughMaterialsOverride: Boolean?
    ): ItemStackBuilder = apply {
        val needsUnlock = baseItem.checkFurnaceLevel < 99 && locked
        val shouldConsiderTeamRepo = PlayerShopSettings.of(player).shopTeamRepoEnabled

        var allAfford = !locked
        val costItems = baseItem.costs.map { (id, count) ->
            val material = ShopMaterialRegistry.getMaterial(player.level().registryAccess(), id)
            if (material == null) {
                allAfford = false
                return@map PARSED_NULL
            }
            val canAfford =
                MaterialConsumeAccess.countDeltaForMaterial(player, material, count, shouldConsiderTeamRepo) == 0
            if (!canAfford) allAfford = false
            component {
                // 底色与资源颜色相同
                empty() provide TextStyles.GRAY_ITEM_LORE provide material.description.style
                text("$count × ")
                raw(material.description)
                text(" ")
                if (canAfford) {
                    text("√") provide style {
                        color(ChatFormatting.GREEN)
                        bold(true)
                        italic(true)
                    }
                } else {
                    text("x") provide style {
                        color(ChatFormatting.RED)
                        bold(true)
                        italic(true)
                    }
                }
            }
        }
        val hasEnoughMaterials = hasEnoughMaterialsOverride ?: allAfford

        // 资源包相关
        customModelDataRelative(needsUnlock, hasEnoughMaterials)

        // 隐藏物品属性
        hideComponent(DataComponents.ATTRIBUTE_MODIFIERS, true)

        // 添加Lore
        if (locked) {
            appendLore(
                baseItem.lockMessage?.takeIf { it.string.isNotBlank() }
                    ?: Component.literal("未解锁")
            )
        } else if (!hasEnoughMaterials) {
            appendLore(
                Component.literal("材料不足")
                    .withStyle { it.withColor(ChatFormatting.RED).withItalic(false) }
            )
        } else if (!baseItem.hideBuyText) {
            appendLore(
                Component.literal("点击购买")
                    .withStyle { it.withColor(ChatFormatting.YELLOW).withItalic(false) }
            )
        }

        if (costItems.isEmpty()) return this@ItemStackBuilder
        setPriorLore(listOf(COSTS_LINE) + costItems)
    }

    private fun customModelDataRelative(needsUnlock: Boolean, hasEnoughMaterials: Boolean) {
        // 如果资源足够，修改custom_model_data.strings[0]
        val originModelData = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY)
        val strings = originModelData.strings.toMutableList()
        if (needsUnlock) {
            strings.add(0, "locked")
        } else if (hasEnoughMaterials) {
            strings.add(0, "upgrade")
        }
        stack[DataComponents.CUSTOM_MODEL_DATA] = CustomModelData(
            originModelData.floats,
            originModelData.flags,
            strings,
            originModelData.colors
        )
    }

    fun build(): ItemStack = stack.copy()
}
