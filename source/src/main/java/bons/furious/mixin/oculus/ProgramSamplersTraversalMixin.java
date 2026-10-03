package bons.furious.mixin.oculus;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import net.irisshaders.iris.gl.program.GlUniform1iCall;
import net.irisshaders.iris.gl.program.ProgramSamplers;
import net.irisshaders.iris.gl.sampler.SamplerBinding;
import net.irisshaders.iris.gl.state.ValueUpdateNotifier;
import net.irisshaders.iris.mixin.GlStateManagerAccessor;
import org.lwjgl.opengl.GL20C;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * oculus_program_traversal, part 1 of 2 (Oculus 1.8.0 for Minecraft 1.20.1): ProgramSamplers.
 *
 * update and removeListeners run on every shader program change and walked Guava ImmutableLists with for-each loops,
 * allocating an iterator each time. The immutable sampler bindings and notifiers are now read by index in the same
 * order. The mutable initializer list keeps its iterator loop, and everything else is Oculus' original code.
 */
@Mixin(value = ProgramSamplers.class, remap = false)
public abstract class ProgramSamplersTraversalMixin {
    @Shadow private static ProgramSamplers active;
    @Shadow @Final private ImmutableList<SamplerBinding> samplerBindings;
    @Shadow @Final private ImmutableList<ValueUpdateNotifier> notifiersToReset;
    @Shadow private List<GlUniform1iCall> initializer;

    /**
     * @author BonsUnleashed
     * @reason Index the immutable sampler bindings instead of allocating an iterator per program change.
     */
    @Overwrite
    public void update() {
        if (active != null) {
            active.removeListeners();
        }

        active = (ProgramSamplers) (Object) this;

        if (this.initializer != null) {
            for (GlUniform1iCall call : this.initializer) {
                RenderSystem.glUniform1i(call.location(), call.value());
            }

            this.initializer = null;
        }

        // Keep the active texture unit intact, like the original: the sampler bindings below change it.
        int activeTexture = GlStateManagerAccessor.getActiveTexture();

        ImmutableList<SamplerBinding> bindings = this.samplerBindings;
        int count = bindings.size();
        for (int i = 0; i < count; i++) {
            SamplerBinding samplerBinding = bindings.get(i);
            samplerBinding.update();
        }

        RenderSystem.activeTexture(GL20C.GL_TEXTURE0 + activeTexture);
    }

    /**
     * @author BonsUnleashed
     * @reason Index the immutable notifier list instead of allocating an iterator per program change.
     */
    @Overwrite
    public void removeListeners() {
        active = null;

        ImmutableList<ValueUpdateNotifier> notifiers = this.notifiersToReset;
        int count = notifiers.size();
        for (int i = 0; i < count; i++) {
            ValueUpdateNotifier notifier = notifiers.get(i);
            notifier.setListener(null);
        }
    }
}
