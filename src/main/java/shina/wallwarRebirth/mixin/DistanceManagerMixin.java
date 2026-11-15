package shina.wallwarRebirth.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DistanceManager.class)
public class DistanceManagerMixin {
    @Final
    @Shadow
    Long2ObjectMap<ObjectSet<ServerPlayer>> playersPerChunk;

    @Inject(method = "removePlayer", at = @At("HEAD"), cancellable = true)
    private void wallwar$skipMissingSet(SectionPos sectionPos, ServerPlayer player, CallbackInfo ci) {
        Long2ObjectMap<ObjectSet<ServerPlayer>> chunks = this.playersPerChunk;
        if (chunks == null || chunks.get(sectionPos.chunk().toLong()) == null) ci.cancel();
    }
}
