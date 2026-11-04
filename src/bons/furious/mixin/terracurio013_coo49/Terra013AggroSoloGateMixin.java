package bons.furious.mixin.terracurio013_coo49;

import bons.furious.patch.terracurio013_coo49.Terra013AggroGate;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * terracurio013_aggro_scan_solo_gate (Terra Curio 0.1.13 (mod id confluence), LGPL-3.0; server side): a cancellable inject into
 * ForgeEvents.livingChangeTarget right before its one Level.players() call, i.e. after the handler's own tests (the mob is
 * an Enemy, the new target a Player) and its follow-range read. Every event those tests end is untouched (no code of ours
 * runs). When Terra013AggroGate.skip says the rest cannot change anything (the new target is the level's only player; see
 * Terra013AggroGate), the handler returns there; otherwise it runs on as shipped. Our own logic only.
 */
@Mixin(targets = "org.confluence.mod.event.ForgeEvents", remap = false)
public abstract class Terra013AggroSoloGateMixin {
    @Inject(method = "livingChangeTarget(Lnet/minecraftforge/event/entity/living/LivingChangeTargetEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_6907_()Ljava/util/List;"),
            cancellable = true, require = 1, allow = 1, remap = false)
    private static void bons$terracurio013Coo49AggroSoloGate(LivingChangeTargetEvent event, CallbackInfo ci) {
        if (Terra013AggroGate.skip(event)) ci.cancel();
    }
}
