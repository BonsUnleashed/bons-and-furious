package bons.furious.mixin.oculus;

import com.google.common.collect.ImmutableList;
import net.irisshaders.iris.gl.program.ProgramUniforms;
import net.irisshaders.iris.gl.state.ValueUpdateNotifier;
import net.irisshaders.iris.gl.uniform.Uniform;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * oculus_program_traversal, part 2 of 2 (Oculus 1.8.0 for Minecraft 1.20.1): ProgramUniforms.
 *
 * updateStage (once per uniform stage) and removeListeners run on every shader program change and walked Guava
 * ImmutableLists with for-each loops, allocating an iterator each time. The same lists are now read by index in the
 * same order.
 */
@Mixin(value = ProgramUniforms.class, remap = false)
public abstract class ProgramUniformsTraversalMixin {
    @Shadow private static ProgramUniforms active;
    @Shadow @Final private ImmutableList<ValueUpdateNotifier> notifiersToReset;

    /**
     * @author BonsUnleashed
     * @reason Index the immutable uniform list instead of allocating an iterator per stage.
     */
    @Overwrite
    private void updateStage(ImmutableList<Uniform> uniforms) {
        // Read through a local, like the 1.0.19 patch this replaces.
        ImmutableList<Uniform> stage = uniforms;
        int count = stage.size();
        for (int i = 0; i < count; i++) {
            Uniform uniform = stage.get(i);
            uniform.update();
        }
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
