package bons.furious.mixin.mowziesmobs_client_c49;

import bons.furious.patch.mowziesmobs_client_c49.ChainSubsteps;
import com.bobmowzie.mowziesmobs.client.model.tools.dynamics.GeckoDynamicChain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * mowziesmobs_dynamic_chain_substep_cap (Mowzie's Mobs 1.7.3, client, since 1.0.49, off by default): the substep count
 * argument of updateChain's call to updateSpringConstraint is capped by bons.furious.patch.mowziesmobs_client_c49
 * .ChainSubsteps; every other argument and call is unchanged.
 */
@Mixin(value = GeckoDynamicChain.class, remap = false)
public abstract class GeckoDynamicChainSubstepMixin {
    @ModifyArg(method = "updateChain(FFFFFIZ)V",
            at = @At(value = "INVOKE", target = "Lcom/bobmowzie/mowziesmobs/client/model/tools/dynamics/GeckoDynamicChain;updateSpringConstraint(FFFZFIF)V"),
            index = 5, remap = false)
    private int bons$chainSubsteps(int substeps) {
        return ChainSubsteps.cap(substeps);
    }
}
