package shina.wallwarRebirth.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkMap.class)
public interface ChunkMapAccess {
    @Accessor("randomState")
    @Mutable
    void wallwarSetRandomState(RandomState state);
}
