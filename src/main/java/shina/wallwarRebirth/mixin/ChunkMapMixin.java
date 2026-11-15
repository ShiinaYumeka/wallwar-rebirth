package shina.wallwarRebirth.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.PlayerMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import shina.wallwarRebirth.server.ChunkTracking;
import shina.wallwarRebirth.world.ArenaReset;

@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin implements ChunkTracking {
    @Final
    @Shadow
    ServerLevel level;

    @Final
    @Shadow
    private PlayerMap playerMap;

    @Final
    @Shadow
    private Int2ObjectMap<?> entityMap;

    @Shadow
    protected abstract void addEntity(Entity entity);

    @Shadow
    protected abstract void removeEntity(Entity entity);

    @Shadow
    public abstract void move(ServerPlayer player);

    @Inject(method = "save(Lnet/minecraft/world/level/chunk/ChunkAccess;)Z", at = @At("HEAD"), cancellable = true)
    private void wallwar$skipResetChunk(ChunkAccess chunk, CallbackInfoReturnable<Boolean> cir) {
        if (!ArenaReset.condemned(chunk)) return;
        chunk.tryMarkSaved();
        cir.setReturnValue(false);
    }

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void wallwar$skipUntrackedMove(ServerPlayer player, CallbackInfo ci) {
        if (player.isRemoved() || wallwar$untracked(player)) ci.cancel();
    }

    @Override
    public void wallwar$follow(ServerPlayer player) {
        if (player.isRemoved() || player.level() != this.level) return;
        wallwar$replaceRemovedTracker(player);
        if (wallwar$untracked(player)) return;
        this.move(player);
    }

    @Unique
    private boolean wallwar$untracked(ServerPlayer player) {
        return this.playerMap.ignoredOrUnknown(player) && !this.playerMap.ignored(player);
    }

    @Unique
    private void wallwar$replaceRemovedTracker(ServerPlayer live) {
        Object tracked = this.entityMap.get(live.getId());
        if (!(tracked instanceof TrackedEntityAccess access)) return;
        Entity current = access.wallwar$entity();
        if (current == live || !current.isRemoved()) return;
        this.removeEntity(current);
        if (this.entityMap.containsKey(live.getId())) return;
        this.addEntity(live);
    }
}
