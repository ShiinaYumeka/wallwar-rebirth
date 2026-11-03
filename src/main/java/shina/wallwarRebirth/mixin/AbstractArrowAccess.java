package shina.wallwarRebirth.mixin;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractArrow.class)
public interface AbstractArrowAccess {
    @Invoker("setPierceLevel")
    void wallwarSetPierceLevel(byte pierceLevel);
}
