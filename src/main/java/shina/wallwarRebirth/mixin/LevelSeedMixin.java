package shina.wallwarRebirth.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import shina.wallwarRebirth.match.Match;
import shina.wallwarRebirth.rules.MatchRules;

@Mixin(ServerLevel.class)
public class LevelSeedMixin {
    @Inject(method = "getSeed()J", at = @At("HEAD"), cancellable = true)
    private void wallwar$matchSeed(CallbackInfoReturnable<Long> cir) {
        ServerLevel level = (ServerLevel) (Object) this;
        ResourceKey<Level> dimension = level.dimension();
        if (!dimension.equals(MatchRules.INSTANCE.getDIMENSION())) return;
        if (Match.terrainSeed == 0L) return;
        cir.setReturnValue(Match.terrainSeed);
    }
}
