package shina.wallwarRebirth.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import shina.wallwarRebirth.match.MatchHooks;

@Mixin(ResultSlot.class)
public class ResultSlotMixin {
    @Shadow
    @Final
    private Player player;

    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void wallwar$blockCraft(int amount, CallbackInfoReturnable<ItemStack> cir) {
        if (((ResultSlot) (Object) this).getItem().isEmpty()) return;
        if (MatchHooks.blockCrafting(this.player)) cir.setReturnValue(ItemStack.EMPTY);
    }
}
