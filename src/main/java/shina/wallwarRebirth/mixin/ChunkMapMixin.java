package shina.wallwarRebirth.mixin;

import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import shina.wallwarRebirth.match.ArenaReset;

@Mixin(net.minecraft.server.level.ChunkMap.class)
public class ChunkMapMixin {
    @Inject(method = "save(Lnet/minecraft/world/level/chunk/ChunkAccess;)Z", at = @At("HEAD"), cancellable = true)
    private void wallwar$skipResetChunk(ChunkAccess chunk, CallbackInfoReturnable<Boolean> cir) {
        if (!ArenaReset.condemned(chunk)) return;
        chunk.tryMarkSaved();
        cir.setReturnValue(false);
    }
}
