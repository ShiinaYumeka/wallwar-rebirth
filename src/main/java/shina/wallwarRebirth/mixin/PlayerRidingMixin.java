package shina.wallwarRebirth.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import shina.wallwarRebirth.building.CannonCrew;

@Mixin(Player.class)
public class PlayerRidingMixin {
    @Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
    private void wallwar$stayOnShell(CallbackInfoReturnable<Boolean> cir) {
        if (CannonCrew.locked((Player) (Object) this)) cir.setReturnValue(false);
    }
}
