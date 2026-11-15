package shina.wallwarRebirth.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shina.wallwarRebirth.match.BoundItems;
import shina.wallwarRebirth.match.MenuClicks;

@Mixin(AbstractContainerMenu.class)
public class AbstractContainerMenuMixin {
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void wallwar$menuItemClick(int slotIndex, int button, ClickType clickType, Player player, CallbackInfo ci) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        if ((clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE) &&
            slotIndex >= 0 && slotIndex < menu.slots.size()) {
            Slot slot = menu.getSlot(slotIndex);
            if (MenuClicks.onSlotClick(player, slot.getItem())) {
                ci.cancel();
                menu.broadcastChanges();
                return;
            }
        }
        if (!BoundItems.blockClick(menu, slotIndex, button, clickType, player)) return;
        ci.cancel();
        menu.broadcastChanges();
    }
}
