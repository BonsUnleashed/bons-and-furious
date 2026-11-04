package bons.furious.mixin.terracurio_coo49;

import bons.furious.patch.terracurio_coo49.TerraAggroGate;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * terracurio_aggro_scan_solo_gate (Terra Curio 0.2.0, LGPL-3.0; server side): a cancellable inject into
 * ForgeEvents.livingChangeTarget right before its one Level.players() call, i.e. after the handler's own tests (the mob is
 * an Enemy, the new target a Player) and its follow-range read. Every event those tests end is untouched (no code of ours
 * runs). When TerraAggroGate.skip says the rest cannot change anything (the new target is the level's only player; see
 * TerraAggroGate), the handler returns there; otherwise it runs on as shipped. Our own logic only.
 */
@Mixin(targets = "org.confluence.terra_curio.event.ForgeEvents", remap = false)
public abstract class TerraAggroSoloGateMixin {
    @Inject(method = "livingChangeTarget(Lnet/minecraftforge/event/entity/living/LivingChangeTargetEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_6907_()Ljava/util/List;"),
            cancellable = true, require = 1, allow = 1, remap = false)
    private static void bons$terracurioCoo49AggroSoloGate(LivingChangeTargetEvent event, CallbackInfo ci) {
        if (TerraAggroGate.skip(event)) ci.cancel();
    }
}
