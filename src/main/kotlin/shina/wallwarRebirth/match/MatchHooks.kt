package shina.wallwarRebirth.match

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.loot.v3.LootTableEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.BlockTags
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.core.BlockPos
import net.minecraft.world.item.Items
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.CrafterBlock
import net.minecraft.world.level.block.CraftingTableBlock
import net.minecraft.world.level.block.LevelEvent
import net.minecraft.world.level.block.state.BlockState
import shina.wallwarRebirth.building.SiteCoreBlock
import shina.wallwarRebirth.building.SiteCores
import shina.wallwarRebirth.building.SitePartBlock
import shina.wallwarRebirth.building.SiteStructureBlock
import shina.wallwarRebirth.item.BuildingCoreItem
import shina.wallwarRebirth.item.SummonEggItem
import shina.wallwarRebirth.monster.SummonedUnit
import java.util.UUID

object MatchHooks {
    private val craftTold = HashMap<UUID, Int>()
    private val COOKED = mapOf(
        Items.BEEF to Items.COOKED_BEEF,
        Items.PORKCHOP to Items.COOKED_PORKCHOP,
        Items.MUTTON to Items.COOKED_MUTTON,
        Items.CHICKEN to Items.COOKED_CHICKEN,
        Items.RABBIT to Items.COOKED_RABBIT,
    )

    @JvmStatic
    fun blockCrafting(player: Player): Boolean {
        val serverPlayer = player as? ServerPlayer ?: return false
        if (Match.phase != Match.Phase.FIGHT) return false
        val now = serverPlayer.tickCount
        if (craftTold[serverPlayer.uuid] != now) {
            craftTold[serverPlayer.uuid] = now
            serverPlayer.sendSystemMessage(Component.literal("现在不能合成"))
        }
        return true
    }

    @JvmStatic
    fun blockCrafter(): Boolean = Match.phase == Match.Phase.FIGHT

    fun init() {
        ServerTickEvents.END_SERVER_TICK.register { Match.tick(it) }
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ -> Match.onJoin(handler.player) }
        ServerPlayConnectionEvents.DISCONNECT.register { _, server -> Match.onLeave(server) }
        ServerPlayerEvents.AFTER_RESPAWN.register { _, newPlayer, _ -> Match.afterRespawn(newPlayer) }
        LootTableEvents.MODIFY_DROPS.register { _, context, drops ->
            if (context.getOptionalParameter(LootContextParams.THIS_ENTITY) !is Animal) return@register
            val list = drops as MutableList<ItemStack>
            for (index in list.indices) {
                val cooked = COOKED[list[index].item] ?: continue
                list[index] = ItemStack(cooked, list[index].count)
            }
        }
        ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, source, _ ->
            if (Match.phase != Match.Phase.SETTLE) return@register true
            val attacker = source.entity as? ServerPlayer
            if (Match.isArena(entity.level())) return@register false
            attacker == null || !Match.restricted(attacker)
        }
        PlayerBlockBreakEvents.BEFORE.register { world, player, pos, state, _ ->
            if (player !is ServerPlayer) return@register true
            if (Match.phase != Match.Phase.LOBBY && Match.restricted(player)) return@register false
            if (!SurfaceOres.harvest(world, player, pos, state)) return@register false
            if (Match.isArena(world) && !keepsDrop(state) && world is ServerLevel) {
                world.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state))
                world.playSound(null, pos, state.soundType.breakSound, SoundSource.BLOCKS, 1f, 1f)
                world.destroyBlock(pos, false, player)
                return@register false
            }
            true
        }
        UseItemCallback.EVENT.register { player, level, hand ->
            if (player !is ServerPlayer || level.isClientSide) return@register InteractionResult.PASS
            val stack = player.getItemInHand(hand)
            if (!Match.castMiner(player, stack)) return@register InteractionResult.PASS
            if (stack.isEmpty) player.setItemInHand(hand, ItemStack.EMPTY)
            InteractionResult.SUCCESS
        }
        UseBlockCallback.EVENT.register { player, world, hand, hit ->
            if (player !is ServerPlayer) return@register InteractionResult.PASS
            if (Match.phase == Match.Phase.FIGHT) {
                val block = world.getBlockState(hit.blockPos).block
                if (block is CraftingTableBlock || block is CrafterBlock) {
                    player.sendSystemMessage(Component.literal("现在不能合成"))
                    return@register InteractionResult.FAIL
                }
            }
            val stack = player.getItemInHand(hand)
            if (Match.castMiner(player, stack)) {
                if (stack.isEmpty) player.setItemInHand(hand, ItemStack.EMPTY)
                return@register InteractionResult.FAIL
            }
            val placeAt = placementPos(player, hand, stack, hit)
            if (placeAt != null && world is ServerLevel && SiteCores.insideBuilding(world, placeAt)) {
                player.sendSystemMessage(Component.literal("这个位置不能放置"))
                return@register InteractionResult.FAIL
            }
            if (Match.phase == Match.Phase.LOBBY || !Match.restricted(player)) return@register InteractionResult.PASS
            if (places(stack)) {
                player.sendSystemMessage(Component.literal("现在不能放置"))
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

    private fun keepsDrop(state: BlockState): Boolean {
        val block = state.block
        if (block is SiteCoreBlock || block is SiteStructureBlock || block is SitePartBlock) return true
        if (state.`is`(BlockTags.LOGS) || state.`is`(BlockTags.PLANKS)) return true
        return state.`is`(BlockTags.COAL_ORES) ||
            state.`is`(BlockTags.IRON_ORES) ||
            state.`is`(BlockTags.COPPER_ORES) ||
            state.`is`(BlockTags.GOLD_ORES) ||
            state.`is`(BlockTags.REDSTONE_ORES) ||
            state.`is`(BlockTags.LAPIS_ORES) ||
            state.`is`(BlockTags.DIAMOND_ORES) ||
            state.`is`(BlockTags.EMERALD_ORES)
    }

    private fun places(stack: ItemStack): Boolean =
        stack.item is BlockItem || stack.item is BuildingCoreItem || stack.item is SummonEggItem

    private fun placementPos(player: ServerPlayer, hand: net.minecraft.world.InteractionHand, stack: ItemStack, hit: net.minecraft.world.phys.BlockHitResult): BlockPos? {
        val item = stack.item
        if (item is BuildingCoreItem) return hit.blockPos.relative(hit.direction)
        if (item is BlockItem) return BlockPlaceContext(player, hand, stack, hit).clickedPos
        return null
    }
}
