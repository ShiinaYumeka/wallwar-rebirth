package shina.wallwarRebirth.mixin;

import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerChunkCache.class)
public interface ServerChunkCacheAccess {
    @Invoker("runDistanceManagerUpdates")
    boolean wallwarRunDistanceManagerUpdates();
}
