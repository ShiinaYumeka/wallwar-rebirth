package shina.wallwarRebirth.mixin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shina.wallwarRebirth.server.ChunkTracking;

@Mixin(ServerEntity.class)
public class ServerEntityMixin {
    @Unique
    private static final Set<UUID> PENDING = new HashSet<>();

    @Final
    @Shadow
    private Entity entity;

    @Inject(method = "sendPairingData", at = @At("HEAD"), cancellable = true)
    private void wallwar$skipRemoved(
        ServerPlayer viewer,
        Consumer<Packet<? super ClientGamePacketListener>> consumer,
        CallbackInfo ci
    ) {
        if (!this.entity.isRemoved()) return;
        ci.cancel();
        if (!(this.entity instanceof ServerPlayer dead)) return;
        ServerLevel level = dead.level();
        ServerPlayer live = level.getServer().getPlayerList().getPlayer(dead.getUUID());
        if (live == null || live.isRemoved() || live.level() != level) return;
        if (!(level.getChunkSource().chunkMap instanceof ChunkTracking tracking)) return;
        if (!PENDING.add(live.getUUID())) return;
        level.getServer().execute(() -> {
            PENDING.remove(live.getUUID());
            tracking.wallwar$follow(live);
        });
    }
}