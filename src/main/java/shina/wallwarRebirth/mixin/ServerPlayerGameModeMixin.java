package shina.wallwarRebirth.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import shina.wallwarRebirth.building.BuildingMining;

@Mixin(value = ServerPlayerGameMode.class, priority = 900)
public class ServerPlayerGameModeMixin {
    @Shadow
    @Final
    protected ServerPlayer player;

    @Inject(method = "handleBlockBreakAction", at = @At("HEAD"), cancellable = true)
    private void wallwar$ignoreClientStop(
        BlockPos pos,
        ServerboundPlayerActionPacket.Action action,
        Direction direction,
        int worldHeight,
        int sequence,
        CallbackInfo ci
    ) {
        if (action != ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK) return;
        BlockState state = this.player.level().getBlockState(pos);
        if (!BuildingMining.holds(state)) return;
        BuildingMining.lockBreakSpeed(this.player);
        BuildingMining.syncMiningView(this.player, pos, state);
        ci.cancel();
    }

    @Inject(method = "handleBlockBreakAction", at = @At("TAIL"))
    private void wallwar$releaseBreakLock(
        BlockPos pos,
        ServerboundPlayerActionPacket.Action action,
        Direction direction,
        int worldHeight,
        int sequence,
        CallbackInfo ci
    ) {
        if (action == ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK) {
            BuildingMining.unlockBreakSpeed(this.player);
            return;
        }
        if (action != ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) return;
        BlockState state = this.player.level().getBlockState(pos);
        if (!BuildingMining.holds(state)) return;
        BuildingMining.lockBreakSpeed(this.player);
        BuildingMining.syncMiningView(this.player, pos, state);
    }

    @Inject(method = "incrementDestroyProgress", at = @At("TAIL"))
    private void wallwar$keepBreakLock(BlockState state, BlockPos pos, int startTick, CallbackInfoReturnable<Float> cir) {
        if (BuildingMining.holds(state)) BuildingMining.lockBreakSpeed(this.player);
        else BuildingMining.unlockBreakSpeed(this.player);
    }

    @Inject(method = "destroyAndAck", at = @At("TAIL"))
    private void wallwar$relockAfterHit(BlockPos pos, int sequence, String reason, CallbackInfo ci) {
        if (BuildingMining.holds(this.player.level().getBlockState(pos))) BuildingMining.lockBreakSpeed(this.player);
        else BuildingMining.unlockBreakSpeed(this.player);
    }
}
