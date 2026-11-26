package shina.wallwarRebirth.profession

import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.ItemLore
import shina.wallwarRebirth.building.BuildingStyles
import shina.wallwarRebirth.building.Buildings
import shina.wallwarRebirth.building.SiteCoreBlockEntity
import shina.wallwarRebirth.building.SiteCores
import shina.wallwarRebirth.building.rememberCovered
import shina.wallwarRebirth.contract.WallwarTeams
import shina.wallwarRebirth.item.ModItems
import shina.wallwarRebirth.match.Match
import shina.wallwarRebirth.rules.MatchRules
import shina.wallwarRebirth.util.playNotifySound
import shina.wallwarRebirth.util.showTitle
import java.util.UUID

object CommandPosts {
    private const val TAG = "CommandPost"

    fun item(): ItemStack {
        val stack = ItemStack(ModItems.CORE_BARRACKS)
        val data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
        stack[DataComponents.CUSTOM_DATA] = data.update { tag -> tag.putBoolean(TAG, true) }
        stack.set(
            DataComponents.CUSTOM_NAME,
            Component.literal("指挥官兵营").withStyle { style ->
                style.withItalic(false).withColor(ChatFormatting.GOLD)
            },
        )
        stack.set(
            DataComponents.LORE,
            ItemLore(listOf(
                Component.literal("放下后为 3 级兵营").withStyle { style ->
                    style.withItalic(false).withColor(ChatFormatting.GRAY)
                },
                Component.literal("对方会收到放置提醒").withStyle { style ->
                    style.withItalic(false).withColor(ChatFormatting.GRAY)
                },
                Component.literal("打到这座兵营的伤害按 2 倍转到我方基地，兵营本身不扣血。").withStyle { style ->
                    style.withItalic(false).withColor(ChatFormatting.GRAY)
                },
            )),
        )
        return stack
    }

    fun isTagged(stack: ItemStack): Boolean {
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return false
        return data.copyTag().getBooleanOr(TAG, false)
    }

    fun placed(world: ServerLevel, owner: UUID): SiteCoreBlockEntity? =
        SiteCores.all().firstOrNull { site ->
            site.level === world && !site.isRemoved && site.health > 0 && site.commandPost && site.commandOwner == owner
        }

    fun install(site: SiteCoreBlockEntity, player: ServerPlayer) {
        val definition = Buildings.BARRACKS
        val world = site.level as? ServerLevel ?: return
        site.commandPost = true
        site.commandOwner = player.uuid
        site.buildingLevel = definition.maxLevel
        site.health = definition.maxHealth(site.buildingLevel)
        val previous = site.partPositions.toList()
        site.partPositions.clear()
        site.partPositions.addAll(
            BuildingStyles.apply(
                world,
                site.blockPos,
                definition,
                site.buildingLevel,
                site.facingDirection(),
                previous,
            ) { pos, state -> site.rememberCovered(pos, state) },
        )
        site.setChanged()
        announce(world, site)
    }

    fun reclaim(player: ServerPlayer, now: Long): Classes.Cast {
        val world = player.level()
        val site = if (!Match.isArena(world)) null else placed(world, player.uuid)
        if (site == null) {
            if (holds(player)) return Classes.Cast("兵营还在背包里，直接放下即可", null)
            return Classes.Cast("没有可回收的兵营", null)
        }
        val max = Buildings.BARRACKS.maxHealth(site.buildingLevel)
        if (site.health < max) return Classes.Cast("兵营已经受到伤害，不能回收", null)
        site.dropModulesOnRemove = true
        site.destroy()
        val stack = item()
        if (!player.inventory.add(stack)) player.drop(stack, false)
        return Classes.Cast("兵营已回收，可以重新放置", now + MatchRules.COMMANDER_SKILL_COOLDOWN_TICKS)
    }

    private fun holds(player: ServerPlayer): Boolean {
        val inventory = player.inventory
        for (slot in 0 until inventory.containerSize) {
            if (isTagged(inventory.getItem(slot))) return true
        }
        return isTagged(player.containerMenu.carried)
    }

    private fun announce(world: ServerLevel, site: SiteCoreBlockEntity) {
        val enemy = when (site.team) {
            WallwarTeams.RED -> WallwarTeams.BLUE
            WallwarTeams.BLUE -> WallwarTeams.RED
            else -> return
        }
        val pos = site.blockPos
        val message = Component.literal("${site.team}指挥官在 ${pos.x} ${pos.y} ${pos.z} 放置了三级兵营")
        for (player in world.server.playerList.players) {
            if (player.team?.name != enemy) continue
            player.sendSystemMessage(message)
            player.showTitle(Component.literal("指挥官兵营"), Component.literal("${site.team}已放置"))
            player.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1f, 1f)
        }
    }
}
