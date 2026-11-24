package shina.wallwarRebirth.mixin;

import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.ValueInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
public class LivingEntityEmptyTeamMixin {
    @Redirect(
        method = "readAdditionalSaveData",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/storage/ValueInput;getString(Ljava/lang/String;)Ljava/util/Optional;"
        )
    )
    private Optional<String> wallwar$ignoreEmptyTeam(ValueInput input, String key) {
        Optional<String> value = input.getString(key);
        if ("Team".equals(key) && value.isPresent() && value.get().isEmpty()) {
            return Optional.empty();
        }
        return value;
    }
}
