package shina.wallwarRebirth.command

import com.mojang.brigadier.Command
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import shina.wallwarRebirth.building.BuildingStructure
import shina.wallwarRebirth.building.BuildingStyles
import shina.wallwarRebirth.building.ModSites
import shina.wallwarRebirth.monster.Monsters
import shina.wallwarRebirth.monster.Summoning
import shina.wallwarRebirth.match.Match
import shina.wallwarRebirth.match.MatchGui
import shina.wallwarRebirth.monster.TestTeams
import shina.wallwarRebirth.item.ModItems
import shina.wallwarRebirth.shop.PlayerShopSettings
import shina.wallwarRebirth.shop.ShopLayerRegistry
import shina.wallwarRebirth.shop.ShopLayerTags
import shina.wallwarRebirth.shop.ShopSession
import shina.wallwarRebirth.shop.gui.ShopGui
import shina.wallwarRebirth.util.command.builder.executes
import shina.wallwarRebirth.util.command.builder.intArgument
import shina.wallwarRebirth.util.command.builder.literal
import shina.wallwarRebirth.util.command.builder.literalCommand
import shina.wallwarRebirth.util.command.builder.requires
import shina.wallwarRebirth.util.command.builder.wordArgument
import shina.wallwarRebirth.util.command.context.CommandContext

object WallwarCommands {
    val root = literalCommand("ww") {
        literal("shop") {
            executes { openShop() }
            literal("teamrepo") {
                executes { toggleTeamRepo() }
            }
        }
        literal("build") {
            requires { Commands.LEVEL_GAMEMASTERS.check(permissions()) }
            executes { placeSite() }
        }
        literal("zombie") {
            requires { Commands.LEVEL_GAMEMASTERS.check(permissions()) }
            executes { placeZombie() }
        }
        literal("side") {
            literal("red") {
                executes { joinSide(TestTeams.RED) }
            }
            literal("blue") {
                executes { joinSide(TestTeams.BLUE) }
            }
        }
        literal("match") {
            executes { openMatch() }
            literal("reset") {
                requires { Commands.LEVEL_GAMEMASTERS.check(permissions()) }
                executes { resetMatch() }
            }
        }
        literal("style") {
            requires { Commands.LEVEL_GAMEMASTERS.check(permissions()) }
            wordArgument("building") {
                intArgument("level", 1, 3) {
                    executes { saveStyle() }
                }
            }
        }
        literal("module") {
            requires { Commands.LEVEL_GAMEMASTERS.check(permissions()) }
            executes { giveModules() }
        }
    }

    private fun CommandContext.openShop(): Int {
        val player = context.source.playerOrException
        val layers = ShopLayerRegistry.getTag(ShopLayerTags.STANDARD_TAG)
        if (layers.isEmpty()) {
            sendFailure(Component.translatable("text.wallwar.shop.empty"))
            return 0
        }
        ShopGui(ShopSession(supportsFurnaceUpgrade = true), player, layers).open()
        return Command.SINGLE_SUCCESS
    }

    private fun CommandContext.toggleTeamRepo(): Int {
        val player = context.source.playerOrException
        val preference = PlayerShopSettings.of(player)
        preference.shopTeamRepoEnabled = !preference.shopTeamRepoEnabled
        val key = if (preference.shopTeamRepoEnabled) {
            "text.wallwar.shop.team_repo.enabled"
        } else {
            "text.wallwar.shop.team_repo.disabled"
        }
        sendSystemMessage(Component.translatable(key))
        return Command.SINGLE_SUCCESS
    }

    private fun CommandContext.placeSite(): Int {
        val player = context.source.playerOrException
        val level = player.level()
        if (Match.isArena(level)) {
            context.source.sendFailure(Component.literal("对局里要购买建筑核心再放置"))
            return 0
        }
        val eye = player.eyePosition
        val end = eye.add(player.lookAngle.scale(6.0))
        val hit = level.clip(ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player))
        if (hit.type != HitResult.Type.BLOCK) {
            context.source.sendFailure(Component.literal("没有看向可放置的方块"))
            return 0
        }
        val destination = hit.blockPos.above()
        if (level.isOutsideBuildHeight(destination)) {
            context.source.sendFailure(Component.literal("这个位置不能放置核心"))
            return 0
        }
        val state = level.getBlockState(destination)
        val placeHit = BlockHitResult(Vec3.atCenterOf(destination), hit.direction, destination, false)
        val placeContext = BlockPlaceContext(player, InteractionHand.MAIN_HAND, ItemStack.EMPTY, placeHit)
        if (!state.canBeReplaced(placeContext)) {
            context.source.sendFailure(Component.literal("这个位置不能放置核心"))
            return 0
        }
        val floor = destination.below()
        if (level.isOutsideBuildHeight(floor)) {
            context.source.sendFailure(Component.literal("这个位置不能放置核心"))
            return 0
        }
        level.setBlock(destination, ModSites.CORE.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL)
        BuildingStructure.placeUnselected(level, destination)
        player.sendSystemMessage(Component.literal("已放置未选定的建筑核心"))
        return Command.SINGLE_SUCCESS
    }

    private fun CommandContext.saveStyle(): Int {
        val player = context.source.playerOrException
        val level = player.level()
        val building: String by this
        val rank = context.getArgument("level", Integer::class.java).toInt()
        val eye = player.eyePosition
        val end = eye.add(player.lookAngle.scale(6.0))
        val hit = level.clip(ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player))
        if (hit.type != HitResult.Type.BLOCK) {
            context.source.sendFailure(Component.literal("没有看向结构方块"))
            return 0
        }
        val message = BuildingStyles.save(level, hit.blockPos, building, rank)
        player.sendSystemMessage(Component.literal(message))
        return Command.SINGLE_SUCCESS
    }

    private fun CommandContext.giveModules(): Int {
        val player = context.source.playerOrException
        player.inventory.add(ItemStack(ModItems.REINFORCE))
        player.inventory.add(ItemStack(ModItems.REINFORCE_TWO))
        player.inventory.add(ItemStack(ModItems.DIFFUSION))
        player.sendSystemMessage(Component.literal("已获得强化模块、强化模块 2 和扩散模块"))
        return Command.SINGLE_SUCCESS
    }

    private fun CommandContext.placeZombie(): Int {
        val player = context.source.playerOrException
        val level = player.level()
        val eye = player.eyePosition
        val end = eye.add(player.lookAngle.scale(6.0))
        val hit = level.clip(ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player))
        if (hit.type != HitResult.Type.BLOCK) {
            context.source.sendFailure(Component.literal("没有看向可放置的方块"))
            return 0
        }
        val destination = hit.blockPos.above()
        val head = destination.above()
        if (level.isOutsideBuildHeight(destination) || level.isOutsideBuildHeight(head)) {
            context.source.sendFailure(Component.literal("这个位置不能放置"))
            return 0
        }
        if (!canReplace(player, destination) || !canReplace(player, head)) {
            context.source.sendFailure(Component.literal("这个位置不能放置"))
            return 0
        }
        val spawned = Summoning.spawn(
            level,
            Monsters.ZOMBIE,
            player.team?.name ?: "",
            destination.x + 0.5,
            destination.y.toDouble(),
            destination.z + 0.5,
            player.yRot,
        )
        if (!spawned) {
            context.source.sendFailure(Component.literal("这个位置不能放置"))
            return 0
        }
        val teamLabel = (player.team?.name ?: "").ifEmpty { "无" }
        player.sendSystemMessage(Component.literal("已放置唤灵·僵尸，队伍：$teamLabel"))
        return Command.SINGLE_SUCCESS
    }

    private fun CommandContext.joinSide(name: String): Int {
        val player = context.source.playerOrException
        val board = player.level().server.scoreboard
        val team = board.getPlayerTeam(name)
        if (team == null) {
            context.source.sendFailure(Component.literal("队伍不存在"))
            return 0
        }
        Match.assign(player, name)
        player.sendSystemMessage(Component.literal("已加入$name"))
        return Command.SINGLE_SUCCESS
    }

    private fun CommandContext.openMatch(): Int {
        MatchGui.open(context.source.playerOrException)
        return Command.SINGLE_SUCCESS
    }

    private fun CommandContext.resetMatch(): Int {
        val player = context.source.player
        Match.reset(player)
        if (player == null) context.source.sendSystemMessage(Component.literal("对局已重置"))
        return Command.SINGLE_SUCCESS
    }

    private fun canReplace(player: net.minecraft.server.level.ServerPlayer, pos: net.minecraft.core.BlockPos): Boolean {
        val state = player.level().getBlockState(pos)
        val placeHit = BlockHitResult(Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false)
        val placeContext = BlockPlaceContext(player, InteractionHand.MAIN_HAND, ItemStack.EMPTY, placeHit)
        return state.canBeReplaced(placeContext)
    }
}
