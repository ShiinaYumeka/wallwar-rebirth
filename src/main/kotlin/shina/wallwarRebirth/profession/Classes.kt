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
import shina.wallwarRebirth.monster.SummonedUnits
import shina.wallwarRebirth.rules.MatchRules
import kotlin.math.min

object Classes {
    const val MINER = "miner"
    const val WRECKER = "wrecker"
    const val BASTION = "bastion"
    const val SWORDSMAN = "swordsman"
    const val CONQUEROR = "conqueror"
    const val CHAMPION = "champion"
    const val COMMANDER = "commander"

    val selectable = listOf(MINER, WRECKER, BASTION, SWORDSMAN, CONQUEROR, CHAMPION, COMMANDER)

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
            "挖掘矿石时，50%概率额外掉落一次，若有急迫效果则必定触发",
            "黄金时刻：获得30秒急迫I（冷却：60s）",
        ),
        Spec(
            WRECKER,
            "破坏手",
            { ModItems.SKILL_WRECKER },
            Items.EXPERIENCE_BOTTLE,
            "挖掘敌方建筑时伤害翻倍。摧毁建筑时获得该建筑所消耗费用的 20%（向上取整）",
            "娴熟技巧：清除自身的挖掘疲劳和蓄风；附近 8 格内有敌方唤灵时隐身 10 秒（冷却：60s）",
        ),
        Spec(
            BASTION,
            "堡垒",
            { ModItems.SKILL_BASTION },
            Items.SLIME_BALL,
            "购买或升级城墙后，返还 25%费用（向上取整）。",
            "强力粘合剂：右键己方城墙核心，为其恢复 50%最大生命值（冷却：60s）",
        ),
        Spec(
            SWORDSMAN,
            "驱魔人",
            { ModItems.SKILL_SWORDSMAN },
            Items.FEATHER,
            "在商店购买的剑附带亡灵杀手 II 和节肢杀手 II。（冷却：180s）",
            "急速冲刺：获得5秒速度II（冷却：30s）",
        ),
        Spec(
            CONQUEROR,
            "征服者",
            { ModItems.SKILL_CONQUEROR },
            Items.IRON_AXE,
            "摧毁敌方建筑时获得其装配模块合计价格的 75%（向上取整）。",
            "镇压：对面向 4 格内的敌方防御建筑核心造成 8 点伤害（冷却：90s）",
        ),
        Spec(
            CHAMPION,
            "冠军",
            { ModItems.SKILL_CHAMPION },
            Items.GOLDEN_APPLE,
            "穿着一整套钻石护甲时，复活时间减半，受到的非玩家伤害减半，对唤灵的伤害增加 50%，挖掘建筑的伤害翻倍，生命上限变为 40，不会被击退。",
            "加冕：回满饱食度；穿着整套钻石护甲时额外回满饱和度并清除所有状态效果（冷却：90s）",
        ),
        Spec(
            COMMANDER,
            "指挥官",
            { ModItems.SKILL_COMMANDER },
            Items.GOAT_HORN,
            "开局获得一座 3 级兵营但不能再购买或放置新的兵营。打到这座兵营的伤害按 2 倍转到我方基地，兵营本身不扣血。",
            "调度：若这座兵营当前满血，则回收并可以重新放置（冷却：30s)",
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

    fun isConqueror(classLocked: Boolean, classId: String): Boolean = isClass(classLocked, classId, CONQUEROR)

    fun isChampion(classLocked: Boolean, classId: String): Boolean = isClass(classLocked, classId, CHAMPION)

    fun isCommander(classLocked: Boolean, classId: String): Boolean = isClass(classLocked, classId, COMMANDER)

    class Cast(val message: String?, val readyAt: Long?)

    fun useSkill(
        classLocked: Boolean,
        classId: String,
        readyAt: Long,
        fighting: Boolean,
        now: Long,
        player: ServerPlayer,
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
            WRECKER -> wreckerDig(now, player)
            SWORDSMAN -> sprint(now)
            BASTION -> repair(now, context)
            CONQUEROR -> suppress(now, player, context)
            CHAMPION -> crown(now, player)
            COMMANDER -> CommandPosts.reclaim(player, now)
            else -> null
        }
    }

    private fun haste(now: Long): Cast {
        return Cast(
            "急迫 I 持续 ${MatchRules.MINER_ACTIVE_TICKS / MatchRules.TICKS_PER_SECOND} 秒",
            now + MatchRules.MINER_SKILL_COOLDOWN_TICKS,
        )
    }

    private fun wreckerDig(now: Long, player: ServerPlayer): Cast {
        val invis = if (SummonedUnits.hasNearbyHostile(player, MatchRules.WRECKER_INVISIBILITY_RANGE)) {
            "，隐身 ${MatchRules.WRECKER_INVISIBILITY_TICKS / MatchRules.TICKS_PER_SECOND} 秒"
        } else {
            "，附近没有敌方唤灵，未隐身"
        }
        return Cast(
            "已清除挖掘疲劳和蓄风$invis",
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

    private fun suppress(now: Long, player: ServerPlayer, context: UseOnContext?): Cast {
        val site = ProfessionEffects.facingDefense(player, context)
            ?: return Cast("没有面向敌方防御建筑的核心", null)
        val definition = Buildings.byId(site.buildingId)
        site.damage(MatchRules.CONQUEROR_DAMAGE, site.blockPos, player.scoreboardName, player)
        val name = definition?.displayName ?: "建筑"
        return Cast("对${name} 造成 ${MatchRules.CONQUEROR_DAMAGE} 点伤害", now + MatchRules.CONQUEROR_SKILL_COOLDOWN_TICKS)
    }

    private fun crown(now: Long, player: ServerPlayer): Cast {
        val armed = ProfessionEffects.championArmed(player)
        val food = player.foodData
        food.foodLevel = 20
        if (armed) {
            food.setSaturation(20f)
            player.removeAllEffects()
        }
        val text = if (armed) "饱食度和饱和度已满，已清除所有状态效果" else "饱食度已满"
        return Cast(text, now + MatchRules.CHAMPION_SKILL_COOLDOWN_TICKS)
    }

    fun applyEffect(player: ServerPlayer, classId: String) {
        when (classId) {
            MINER -> player.addEffect(
                MobEffectInstance(MobEffects.HASTE, MatchRules.MINER_ACTIVE_TICKS, 0, false, true),
            )
            SWORDSMAN -> player.addEffect(
                MobEffectInstance(MobEffects.SPEED, MatchRules.SWORDSMAN_ACTIVE_TICKS, 1, false, true),
            )
            WRECKER -> {
                player.removeEffect(MobEffects.MINING_FATIGUE)
                player.removeEffect(MobEffects.WIND_CHARGED)
                if (SummonedUnits.hasNearbyHostile(player, MatchRules.WRECKER_INVISIBILITY_RANGE)) {
                    player.addEffect(
                        MobEffectInstance(
                            MobEffects.INVISIBILITY,
                            MatchRules.WRECKER_INVISIBILITY_TICKS,
                            0,
                            false,
                            true,
                        ),
                    )
                }
            }
        }
    }

    fun applySwordEnchants(player: ServerPlayer, stack: ItemStack) {
        enchant(player, stack, MatchRules.SMITE_PATH, MatchRules.SMITE_LEVEL)
        enchant(player, stack, MatchRules.BANE_OF_ARTHROPODS_PATH, MatchRules.BANE_OF_ARTHROPODS_LEVEL)
    }

    fun swordPassiveStatus(readyAt: Long, now: Long): String {
        if (readyAt <= now) return "被动已就绪"
        val seconds = ((readyAt - now + MatchRules.TICKS_PER_SECOND - 1) / MatchRules.TICKS_PER_SECOND).toInt()
        return "被动冷却还剩 $seconds 秒"
    }

    private fun enchant(player: ServerPlayer, stack: ItemStack, path: String, level: Int) {
        val registry = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
        val key = ResourceKey.create(
            Registries.ENCHANTMENT,
            Identifier.fromNamespaceAndPath("minecraft", path),
        )
        val holder = registry.get(key).orElse(null) ?: return
        stack.enchant(holder, level)
    }
}
