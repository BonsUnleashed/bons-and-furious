package bons.furious.mixin.distanthorizons;

import bons.furious.patch.distanthorizons.CloudPass;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
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
 *
 * 1.0.34: the scope is also closed when render throws (a box group's pre-render function, an API event listener or GL),
 * by a method wrapper whose finally calls CloudPass.end(): the RETURN injection is not reached then, and the remembered
 * level, wrapper and colour stayed referenced until a later pass ended normally, after a world was left even across the
 * title screen. end() is idempotent, so on a normal return the second call changes nothing; the exception still propagates
 * to Distant Horizons' own handler exactly as before.
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

    @WrapMethod(method = "render")
    private void bons$endPassAlways(RenderParams params, IProfilerWrapper profiler, boolean ssao, Operation<Void> original) {
        try {
            original.call(params, profiler, ssao);
        } finally {
            CloudPass.end();
        }
    }
}
