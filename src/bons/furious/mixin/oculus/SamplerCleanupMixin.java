package bons.furious.mixin.oculus;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.sampler.SamplerLimits;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL45C;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * oculus_empty_sampler_fast_return (Oculus 1.8.0 for Minecraft 1.20.1).
 *
 * unbindAllSamplers runs whenever Oculus clears the active samplers and scanned the sampler cache of every texture
 * unit even when nothing was bound. A private conservative flag, ac$samplersMayBeBound, records that a non-zero
 * sampler may be cached: bindSamplerToUnit sets it when it stores a non-zero sampler, initRenderer and a completed
 * cleanup clear it. While it is clear (and the cache exists) unbindAllSamplers returns at once; otherwise it runs the
 * original cleanup, now in ac$clearNonemptySamplers. If that cleanup throws, the flag stays set.
 */
@Mixin(value = IrisRenderSystem.class, remap = false)
public abstract class SamplerCleanupMixin {
    @Shadow @Final private static int[] emptyArray;
    @Shadow private static IrisRenderSystem.DSAAccess dsaState;
    @Shadow private static boolean hasMultibind;
    @Shadow private static boolean supportsCompute;
    @Shadow private static boolean supportsTesselation;
    @Shadow private static int[] samplers;

    /** True when a non-zero sampler may be bound to some texture unit. */
    @Unique
    private static boolean ac$samplersMayBeBound;

    /**
     * @author BonsUnleashed
     * @reason A new sampler cache starts empty, so clear the flag (the only change; the rest is the original method).
     */
    @Overwrite
    public static void initRenderer() {
        if (GL.getCapabilities().OpenGL45) {
            dsaState = new IrisRenderSystem.DSACore();
            Iris.logger.info("OpenGL 4.5 detected, enabling DSA.");
        } else if (GL.getCapabilities().GL_ARB_direct_state_access) {
            dsaState = new IrisRenderSystem.DSAARB();
            Iris.logger.info("ARB_direct_state_access detected, enabling DSA.");
        } else {
            dsaState = new IrisRenderSystem.DSAUnsupported();
            Iris.logger.info("DSA support not detected.");
        }

        hasMultibind = GL.getCapabilities().OpenGL45 || GL.getCapabilities().GL_ARB_multi_bind;

        supportsCompute = GL.getCapabilities().glDispatchCompute != 0L;
        supportsTesselation = GL.getCapabilities().GL_ARB_tessellation_shader || GL.getCapabilities().OpenGL40;

        samplers = new int[SamplerLimits.get().getMaxTextureUnits()];
        ac$samplersMayBeBound = false;
    }

    /**
     * @author BonsUnleashed
     * @reason Record that a non-zero sampler may now be bound (the only change; the rest is the original method).
     */
    @Overwrite
    public static void bindSamplerToUnit(int unit, int sampler) {
        if (samplers[unit] == sampler) {
            return;
        }

        GL33C.glBindSampler(unit, sampler);

        samplers[unit] = sampler;
        if (sampler != 0) {
            ac$samplersMayBeBound = true;
        }
    }

    /**
     * @author BonsUnleashed
     * @reason Return at once while no sampler can be bound; otherwise run the original cleanup.
     */
    @Overwrite
    public static void unbindAllSamplers() {
        if (samplers != null && !ac$samplersMayBeBound) {
            return;
        }
        ac$clearNonemptySamplers();
        ac$samplersMayBeBound = false;
    }

    /** Oculus' original unbindAllSamplers body. */
    @Unique
    private static void ac$clearNonemptySamplers() {
        boolean usedASampler = false;
        for (int i = 0; i < samplers.length; i++) {
            if (samplers[i] != 0) {
                usedASampler = true;
                if (!hasMultibind) {
                    GL33C.glBindSampler(i, 0);
                }
                samplers[i] = 0;
            }
        }
        if (usedASampler && hasMultibind) {
            GL45C.glBindSamplers(0, emptyArray);
        }
    }
}
