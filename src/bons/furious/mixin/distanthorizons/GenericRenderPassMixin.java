package bons.furious.mixin.distanthorizons;

import bons.furious.patch.distanthorizons.CloudPass;
import com.seibel.distanthorizons.common.render.openGl.generic.GlGenericObjectRenderer;
import com.seibel.distanthorizons.core.render.RenderParams;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IProfilerWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * distanthorizons_cloud_pass_invariants (Distant Horizons 3.3.2), part 2 of 2: GlGenericObjectRenderer.render, which runs
 * every box group's pre-render function once per frame, opens and closes CloudPass's scope. Once per frame, not hot.
 */
@Mixin(value = GlGenericObjectRenderer.class, remap = false)
public abstract class GenericRenderPassMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void bons$beginPass(RenderParams params, IProfilerWrapper profiler, boolean ssao, CallbackInfo ci) {
        CloudPass.begin();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void bons$endPass(RenderParams params, IProfilerWrapper profiler, boolean ssao, CallbackInfo ci) {
        CloudPass.end();
    }
}
