package shina.wallwarRebirth.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EvokerFangs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shina.wallwarRebirth.contract.Ports;
import shina.wallwarRebirth.monster.Monsters;
import shina.wallwarRebirth.monster.SummonedUnit;
import shina.wallwarRebirth.rules.MatchRules;
import shina.wallwarRebirth.targeting.Targeting;

@Mixin(EvokerFangs.class)
public class EvokerFangsMixin {
    @Inject(method = "dealDamageTo", at = @At("HEAD"), cancellable = true)
    private void wallwar$teamFangDamage(LivingEntity target, CallbackInfo ci) {
        EvokerFangs fangs = (EvokerFangs) (Object) this;
        LivingEntity owner = fangs.getOwner();
        if (!(owner instanceof SummonedUnit unit)) return;
        ci.cancel();
        if (!target.isAlive() || target == owner) return;
        if (target instanceof Player player && !Targeting.canTargetPlayer(player)) return;
        String team = unit.getSummonedTeam();
        if (target instanceof SummonedUnit other && !Ports.INSTANCE.getTeams().hostile(team, other.getSummonedTeam())) {
            return;
        }
        if (target instanceof Player player) {
            String otherTeam = player.getTeam() == null ? "" : player.getTeam().getName();
            if (!Ports.INSTANCE.getTeams().hostile(team, otherTeam)) return;
        }
        var definition = Monsters.INSTANCE.byId(unit.getKindId());
        float amount = definition != null ? (float) definition.getPlayerAttack() : (float) MatchRules.EVOKER_ATTACK;
        if (!(fangs.level() instanceof ServerLevel server)) return;
        var source = fangs.damageSources().indirectMagic(fangs, owner);
        if (target.hurtServer(server, source, amount)) {
            unit.getMind().noteExchange();
        }
    }
}
