package shina.wallwarRebirth.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shina.wallwarRebirth.item.ModComponents;

@Mixin(ItemEntity.class)
public class ItemEntityMixin {
    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void wallwar$limitDropTeam(Player player, CallbackInfo ci) {
        ItemStack stack = ((ItemEntity) (Object) this).getItem();
        if (!stack.has(ModComponents.DROP_TEAM)) return;
        String team = stack.get(ModComponents.DROP_TEAM);
        String playerTeam = player.getTeam() == null ? "" : player.getTeam().getName();
        if (team != null && !playerTeam.equals(team)) ci.cancel();
    }
}
