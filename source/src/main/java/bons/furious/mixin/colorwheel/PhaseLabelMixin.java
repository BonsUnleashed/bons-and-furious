package bons.furious.mixin.colorwheel;

import bons.furious.patch.colorwheel.PhaseLabels;
import dev.djefrey.colorwheel.engine.ClrwlDrawManager;
import dev.djefrey.colorwheel.engine.ClrwlRenderingPhase;
import net.irisshaders.iris.gl.GLDebug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * colorwheel_phase_labels (Colorwheel 1.3.0+mc1.20.1, client).
 *
 * setPhase rebuilt its GL debug-group label ("Clrwl " + optional "Shadow " + the phase name lower-cased, underscores to
 * spaces, capitalised) on every Flywheel phase change. The 13 x 2 labels are now taken from PhaseLabels, built once with
 * the same expression; the pop, push and field write are unchanged and in the same order. Colorwheel is LGPLv3; the
 * method body may be carried. Same pattern as 1.0.21's oculus_phase_labels.
 */
@Mixin(value = ClrwlDrawManager.class, remap = false)
public abstract class PhaseLabelMixin {
    @Shadow
    private ClrwlRenderingPhase currentRenderPhase;

    /**
     * @author Bons and Furious (colorwheel_phase_labels)
     * @reason the same label, built once per phase instead of on every phase change
     */
    @Overwrite
    protected void setPhase(ClrwlRenderingPhase phase, boolean shadow) {
        String name = PhaseLabels.label(phase, shadow);
        GLDebug.popGroup();
        GLDebug.pushGroup(phase.getValue(), name);
        this.currentRenderPhase = phase;
    }
}
