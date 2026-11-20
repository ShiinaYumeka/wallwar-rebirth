package shina.wallwarRebirth.mixin;

import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shina.wallwarRebirth.building.SuperWeaponAim;

@Mixin(ServerGamePacketListenerImpl.class)
public class SuperWeaponClickMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleUseItemOn", at = @At("HEAD"), cancellable = true)
    private void wallwar$superWeaponUseOn(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
        if (SuperWeaponAim.busy(this.player) && SuperWeaponAim.handleClick(this.player)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true)
    private void wallwar$superWeaponUse(ServerboundUseItemPacket packet, CallbackInfo ci) {
        if (SuperWeaponAim.busy(this.player) && SuperWeaponAim.handleClick(this.player)) {
            ci.cancel();
        }
    }
}
