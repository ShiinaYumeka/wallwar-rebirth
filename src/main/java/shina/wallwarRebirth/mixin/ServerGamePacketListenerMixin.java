package shina.wallwarRebirth.mixin;

import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shina.wallwarRebirth.item.CommandItem;
import shina.wallwarRebirth.match.BoundItems;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void wallwar$swapOffhand(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (packet.getAction() != ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND) return;
        ItemStack selected = player.getInventory().getSelectedItem();
        if (CommandItem.isCommand(selected)) {
            CommandItem.toggle(player, selected);
            ci.cancel();
            return;
        }
        if (BoundItems.isBound(selected) || BoundItems.isBound(player.getOffhandItem())) {
            ci.cancel();
        }
    }
}
