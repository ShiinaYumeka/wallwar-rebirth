package shina.wallwarRebirth.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Creeper.class)
public interface CreeperAccess {
    @Accessor("DATA_IS_POWERED")
    static EntityDataAccessor<Boolean> wallwarPoweredData() {
        throw new AssertionError();
    }
}
