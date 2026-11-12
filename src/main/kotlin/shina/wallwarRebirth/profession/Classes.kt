package shina.wallwarRebirth.profession

import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.context.UseOnContext
import shina.wallwarRebirth.building.Buildings
import shina.wallwarRebirth.building.SiteCoreBlockEntity
import shina.wallwarRebirth.item.ModItems
import shina.wallwarRebirth.rules.MatchRules
import kotlin.math.min

object Classes {
    const val MINER = "miner"
    const val WRECKER = "wrecker"
    const val BASTION = "bastion"
    const val SWORDSMAN = "swordsman"

    val selectable = listOf(MINER, WRECKER, BASTION, SWORDSMAN)

    data class Spec(
        val id: String,
        val displayName: String,
        val item: () -> Item,
        val icon: Item,
        val passive: String,
        val active: String,
    )

    val specs = listOf(
        Spec(
            MINER,
            "矿工",
            { ModItems.SKILL_MINER },
            Items.CLOCK,
            "挖掘矿石且镐的等级够时，50% 概率把这一次的掉落再掉一份；身上有急迫时必定触发",
            "手持黄金时刻右键，获得急迫 I，持续 30 秒，冷却 60 秒",
        ),
        Spec(
            WRECKER,
            "破坏手",
            { ModItems.SKILL_WRECKER },
            Items.EXPERIENCE_BOTTLE,
            "自己把敌方建筑生命扣到 0 并摧毁时，在核心位置掉落该建筑购买与升级费用合计的 10%，每种资源向上取整",
            "手持娴熟技巧右键，之后 30 秒内挖掘建筑伤害翻倍，冷却 60 秒",
        ),
        Spec(
            BASTION,
            "堡垒",
            { ModItems.SKILL_BASTION },
            Items.SLIME_BALL,
            "购买或升级城墙并完成扣费后，按该次费用返还 25%，每种资源向上取整",
            "手持强力粘合剂右键己方城墙核心，恢复当前生命上限的 50%，冷却 60 秒",
        ),
        Spec(
            SWORDSMAN,
            "驱魔人",
            { ModItems.SKILL_SWORDSMAN },
            Items.FEATHER,
            "在商店购买的剑带有亡灵杀手 III，不另收费",
            "手持急速冲刺右键，获得速度 II，持续 5 秒，冷却 30 秒",
        ),
    )

    private val byId = specs.associateBy { it.id }

    fun normalize(id: String): String = if (id in byId) id else MINER

    fun name(id: String): String = byId[normalize(id)]?.displayName ?: "矿工"

    fun spec(id: String): Spec = byId[normalize(id)]!!

    fun isClass(classLocked: Boolean, classId: String, expected: String): Boolean =
        classLocked && classId == expected

    fun isMiner(classLocked: Boolean, classId: String): Boolean = isClass(classLocked, classId, MINER)

    fun isWrecker(classLocked: Boolean, classId: String): Boolean = isClass(classLocked, classId, WRECKER)

    fun isBastion(classLocked: Boolean, classId: String): Boolean = isClass(classLocked, classId, BASTION)

    fun isSwordsman(classLocked: Boolean, classId: String): Boolean = isClass(classLocked, classId, SWORDSMAN)

    class Cast(val message: String?, val readyAt: Long?)

    fun useSkill(
        classLocked: Boolean,
        classId: String,
        readyAt: Long,
        fighting: Boolean,
        now: Long,
        stack: ItemStack,
        context: UseOnContext?,
    ): Cast? {
        if (!classLocked) return null
        val spec = byId[classId] ?: return null
        if (!stack.`is`(spec.item())) return null
        if (!fighting) return Cast("现在不能释放", null)
        if (readyAt > now) {
            val seconds = ((readyAt - now + 19) / 20).toInt()
            return Cast("技能冷却还剩 $seconds 秒", null)
        }
        return when (classId) {
            MINER -> haste(now)
            WRECKER -> wreckerDig(now)
            SWORDSMAN -> sprint(now)
            BASTION -> repair(now, context)
            else -> null
        }
    }

    private fun haste(now: Long): Cast {
        return Cast(
            "急迫 I 持续 ${MatchRules.MINER_ACTIVE_TICKS / MatchRules.TICKS_PER_SECOND} 秒",
            now + MatchRules.MINER_SKILL_COOLDOWN_TICKS,
        )
    }

    private fun wreckerDig(now: Long): Cast {
        return Cast(
            "挖掘建筑伤害翻倍持续 ${MatchRules.WRECKER_ACTIVE_TICKS / MatchRules.TICKS_PER_SECOND} 秒",
            now + MatchRules.WRECKER_SKILL_COOLDOWN_TICKS,
        )
    }

    private fun sprint(now: Long): Cast {
        return Cast(
            "速度 II 持续 ${MatchRules.SWORDSMAN_ACTIVE_TICKS / MatchRules.TICKS_PER_SECOND} 秒",
            now + MatchRules.SWORDSMAN_SKILL_COOLDOWN_TICKS,
        )
    }

    private fun repair(now: Long, context: UseOnContext?): Cast? {
        val player = context?.player as? ServerPlayer ?: return null
        val level = context.level as? ServerLevel ?: return null
        val site = level.getBlockEntity(context.clickedPos) as? SiteCoreBlockEntity ?: return null
        if (site.buildingId != Buildings.WALL.id) return null
        if (site.team.isNotEmpty() && !site.isAlly(player)) return Cast(null, null)
        val max = Buildings.WALL.maxHealth(site.buildingLevel)
        if (max <= 0) return Cast(null, null)
        if (site.health >= max) return Cast("这座城墙已满血", null)
        val heal = max / 2
        site.health = min(max, site.health + heal)
        site.setChanged()
        return Cast("城墙恢复 $heal 点生命", now + MatchRules.BASTION_SKILL_COOLDOWN_TICKS)
    }

    fun applyEffect(player: ServerPlayer, classId: String) {
        when (classId) {
            MINER -> player.addEffect(
                MobEffectInstance(MobEffects.HASTE, MatchRules.MINER_ACTIVE_TICKS, 0, false, true),
            )
            SWORDSMAN -> player.addEffect(
                MobEffectInstance(MobEffects.SPEED, MatchRules.SWORDSMAN_ACTIVE_TICKS, 1, false, true),
            )
        }
    }

    fun applySmite(player: ServerPlayer, stack: ItemStack) {
        val registry = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
        val key = ResourceKey.create(
            Registries.ENCHANTMENT,
            Identifier.fromNamespaceAndPath("minecraft", MatchRules.SMITE_PATH),
        )
        val holder = registry.get(key).orElse(null) ?: return
        stack.enchant(holder, MatchRules.SMITE_LEVEL)
    }
}
