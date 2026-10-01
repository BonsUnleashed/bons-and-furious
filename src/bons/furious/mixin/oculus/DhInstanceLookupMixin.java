package bons.furious.mixin.oculus;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.compat.dh.DHCompat;
import net.irisshaders.iris.compat.dh.DHCompatInternal;
import net.irisshaders.iris.compat.dh.LodRendererEvents;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * oculus_dh_instance_lookup (Oculus 1.8.0, client).
 *
 * LodRendererEvents.getInstance runs for every Distant Horizons render event Oculus handles and built
 * getPipeline().map(getDHCompat).map(getInstance).orElse(SHADERLESS): two or three Optional objects per call. The same
 * lookups now run as null checks on getPipelineNullable() (getPipeline() wraps that same field): no pipeline, no DH
 * compat, or no instance gives SHADERLESS exactly as the empty Optional did, otherwise the same instance; a missing
 * pipeline manager still throws the same NullPointerException. Oculus is LGPL-3.0; the method body may be carried.
 */
@Mixin(value = LodRendererEvents.class, remap = false)
public abstract class DhInstanceLookupMixin {
    /**
     * @author Bons and Furious (oculus_dh_instance_lookup)
     * @reason the same lookup without allocating Optionals
     */
    @Overwrite
    private static DHCompatInternal getInstance() {
        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline == null) return DHCompatInternal.SHADERLESS;
        DHCompat compat = pipeline.getDHCompat();
        if (compat == null) return DHCompatInternal.SHADERLESS;
        Object instance = compat.getInstance();
        return instance == null ? DHCompatInternal.SHADERLESS : (DHCompatInternal) instance;
    }
}
