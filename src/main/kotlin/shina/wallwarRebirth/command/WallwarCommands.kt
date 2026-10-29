package shina.wallwarRebirth.command

import com.mojang.brigadier.Command
import eu.pb4.sgui.api.elements.GuiElement
import net.minecraft.commands.Commands
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomModelData
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import shina.wallwarRebirth.building.BuildingStructure
import shina.wallwarRebirth.building.ModSites
import shina.wallwarRebirth.monster.ModMonsters
import shina.wallwarRebirth.monster.SummonedZombie
import shina.wallwarRebirth.flux.gui.FluxGui
import shina.wallwarRebirth.gui.GuiTheme
import shina.wallwarRebirth.gui.SimpleHotbarGui
import shina.wallwarRebirth.item.ModItems
import shina.wallwarRebirth.shop.PlayerShopSettings
import shina.wallwarRebirth.shop.ShopLayerRegistry
import shina.wallwarRebirth.shop.ShopLayerTags
import shina.wallwarRebirth.shop.ShopSession
import shina.wallwarRebirth.shop.gui.ShopGui
import shina.wallwarRebirth.util.command.builder.executes
import shina.wallwarRebirth.util.command.builder.literal
import shina.wallwarRebirth.util.command.builder.literalCommand
import shina.wallwarRebirth.util.command.builder.requires
import shina.wallwarRebirth.util.command.context.CommandContext

object WallwarCommands {
    val root = literalCommand("ww") {
        literal("gui") {
            executes { openFluxGui() }
        }
        literal("hotbar") {
            executes { openHotbar() }
        }
        literal("shop") {
            executes { openShop() }
            literal("teamrepo") {
                executes { toggleTeamRepo() }
            }
        }
        literal("token") {
            executes { giveToken() }
        }
        literal("build") {
            requires { Commands.LEVEL_GAMEMASTERS.check(permissions()) }
            executes { placeSite() }
        }
        literal("zombie") {
            requires { Commands.LEVEL_GAMEMASTERS.check(permissions()) }
            executes { placeZombie() }
        }
    }

    private fun CommandContext.openFluxGui(): Int {
        val player = context.source.playerOrException
        FluxGui(player, Component.literal("Wallwar"), rows = 3) {
            pattern(
                "GGGGGGGGG",
                "GGBGBBGBG",
                "GGGGGGGGG",
            ) {
                bind('G') { item(GuiTheme.GLASS_BLACK) }
                bind('B') { item(GuiTheme.GLASS_BLUE) }
            }
        }.open()
        return Command.SINGLE_SUCCESS
    }

    private fun CommandContext.openHotbar(): Int {
        val player = context.source.playerOrException
        val gui = object : SimpleHotbarGui(player) {}
        gui.setSlot(0, GuiElement(ItemStack(Items.DIAMOND)) { _, _, _, _ ->
            player.sendSystemMessage(Component.literal("hotbar"))
        })
        gui.open()
        return Command.SINGLE_SUCCESS
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
        val zombie = SummonedZombie(ModMonsters.ZOMBIE, level)
        zombie.summonedTeam = player.team?.name ?: ""
        zombie.snapTo(destination.x + 0.5, destination.y.toDouble(), destination.z + 0.5, player.yRot, 0f)
        if (!level.addFreshEntity(zombie)) {
            context.source.sendFailure(Component.literal("这个位置不能放置"))
            return 0
        }
        val teamLabel = zombie.summonedTeam.ifEmpty { "无" }
        player.sendSystemMessage(Component.literal("已放置唤灵·僵尸，队伍：$teamLabel"))
        return Command.SINGLE_SUCCESS
    }

    private fun canReplace(player: net.minecraft.server.level.ServerPlayer, pos: net.minecraft.core.BlockPos): Boolean {
        val state = player.level().getBlockState(pos)
        val placeHit = BlockHitResult(Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false)
        val placeContext = BlockPlaceContext(player, InteractionHand.MAIN_HAND, ItemStack.EMPTY, placeHit)
        return state.canBeReplaced(placeContext)
    }

    private fun CommandContext.giveToken(): Int {
        val player = context.source.playerOrException
        val stack = ItemStack(ModItems.EXAMPLE_TOKEN)
        val empty = CustomModelData.EMPTY
        stack[DataComponents.CUSTOM_MODEL_DATA] = CustomModelData(
            empty.floats(),
            empty.flags(),
            listOf("upgrade"),
            empty.colors(),
        )
        if (!player.inventory.add(stack)) {
            player.drop(stack, false)
        }
        return Command.SINGLE_SUCCESS
    }
}
