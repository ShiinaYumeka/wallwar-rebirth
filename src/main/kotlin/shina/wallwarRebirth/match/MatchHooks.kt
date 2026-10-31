package shina.wallwarRebirth.match

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import shina.wallwarRebirth.item.BuildingCoreItem
import shina.wallwarRebirth.item.SummonEggItem
import shina.wallwarRebirth.monster.SummonedUnit

object MatchHooks {
    fun init() {
        ServerTickEvents.END_SERVER_TICK.register { Match.tick(it) }
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ -> Match.onJoin(handler.player) }
        ServerPlayConnectionEvents.DISCONNECT.register { _, server -> Match.onLeave(server) }
        ServerLivingEntityEvents.ALLOW_DEATH.register { entity, _, _ ->
            if (entity is ServerPlayer) Match.holdDeath(entity)
            true
        }
        ServerPlayerEvents.AFTER_RESPAWN.register { _, newPlayer, _ -> Match.restoreDeath(newPlayer) }
        ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, source, _ ->
            if (Match.phase == Match.Phase.LOBBY || Match.isFight()) return@register true
            val attacker = source.entity as? ServerPlayer
            if (Match.isArena(entity.level())) return@register false
            attacker == null || !Match.restricted(attacker)
        }
        PlayerBlockBreakEvents.BEFORE.register { world, player, pos, state, _ ->
            if (player !is ServerPlayer) return@register true
            if (Match.restricted(player)) return@register false
            SurfaceOres.harvest(world, player, pos, state)
        }
        UseItemCallback.EVENT.register { player, level, hand ->
            if (player !is ServerPlayer || level.isClientSide) return@register InteractionResult.PASS
            val stack = player.getItemInHand(hand)
            if (!Classes.castMiner(player, stack)) return@register InteractionResult.PASS
            if (stack.isEmpty) player.setItemInHand(hand, ItemStack.EMPTY)
            InteractionResult.SUCCESS
        }
        UseBlockCallback.EVENT.register { player, _, hand, _ ->
            if (player !is ServerPlayer) return@register InteractionResult.PASS
            val stack = player.getItemInHand(hand)
            if (Classes.castMiner(player, stack)) {
                if (stack.isEmpty) player.setItemInHand(hand, ItemStack.EMPTY)
                return@register InteractionResult.FAIL
            }
            if (!Match.restricted(player)) return@register InteractionResult.PASS
            if (places(stack)) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("现在不能放置"))
                InteractionResult.FAIL
            } else {
                InteractionResult.PASS
            }
        }
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register { entity, world ->
            if (!Match.isArena(world)) return@register
            if (entity is Monster && entity !is SummonedUnit) entity.discard()
            if (entity is Mob && entity !is SummonedUnit && entity.type.category == net.minecraft.world.entity.MobCategory.MONSTER) {
                entity.discard()
            }
        }
    }

    private fun places(stack: ItemStack): Boolean =
        stack.item is BlockItem || stack.item is BuildingCoreItem || stack.item is SummonEggItem
}
