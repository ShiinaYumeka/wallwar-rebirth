package shina.wallwarRebirth.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import shina.wallwarRebirth.match.MatchMenu;

@Mixin(ServerPlayer.class)
public class ServerPlayerDropMixin {
    @Inject(method = "drop(Z)V", at = @At("HEAD"), cancellable = true)
    private void wallwar$blockHotbarDrop(boolean entireStack, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (!MatchMenu.isBound(player.getInventory().getSelectedItem())) return;
        ci.cancel();
    }

    @Inject(
        method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void wallwar$blockStackDrop(
        ItemStack stack,
        boolean throwRandomly,
        boolean retainOwnership,
        CallbackInfoReturnable<ItemEntity> cir
    ) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (!MatchMenu.isBound(stack)) return;
        if (!stack.isEmpty()) player.getInventory().placeItemBackInInventory(stack);
        cir.setReturnValue(null);
    }
}
