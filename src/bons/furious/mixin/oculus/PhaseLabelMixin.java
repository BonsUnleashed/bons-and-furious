package bons.furious.mixin.oculus;

import bons.furious.patch.oculus.PhaseLabels;
import net.irisshaders.iris.gl.GLDebug;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * oculus_phase_labels (Oculus 1.8.0 for Minecraft 1.20.1, client).
 *
 * setPhase runs whenever the render state changes phase (entities, block entities, particles, terrain layers, sky:
 * hundreds of times a frame) and rebuilt the phase's debug-group label each time with toLowerCase, replace and
 * capitalize. The label now comes from PhaseLabels, built once per phase with the same expression; the same debug
 * group calls happen in the same order.
 */
@Mixin(value = IrisRenderingPipeline.class, remap = false)
public abstract class PhaseLabelMixin {
    @Shadow
    private WorldRenderingPhase phase;

    /**
     * @author BonsUnleashed
     * @reason Take the debug-group label from a per-phase table (the only change; the rest is the original method).
     */
    @Overwrite
    public void setPhase(WorldRenderingPhase phase) {
        GLDebug.popGroup();
        if (phase != WorldRenderingPhase.NONE) {
            GLDebug.pushGroup(phase.ordinal(), PhaseLabels.of(phase));
        }
        this.phase = phase;
    }
}
