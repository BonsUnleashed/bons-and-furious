package bons.furious.patch.oculus_passes;

import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.PipelineManager;
import net.irisshaders.iris.pipeline.VanillaRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import org.slf4j.Logger;

/**
 * Bons and Furious 1.0.49 switch oculus_vanilla_buffers_without_shaders on Minecraft 1.21.1 / NeoForge (client; Iris
 * 1.8.12+mc1.21.1 for NeoForge with Sodium 0.6.13, the target of the Oculus-prefixed switches on this Minecraft version), a
 * `change` switch, off by default. Idea: Collections Of Optimizations (oculus.skipBatchingWithoutShaders), idea text only.
 *
 * What Iris does. Its batched-entity mixin on LevelRenderer.renderLevel (net.irisshaders.batchedentityrendering.mixin.
 * MixinLevelRenderer, priority 999) calls RenderBuffersExt.beginLevelRendering() at HEAD on every frame, shader pack or not;
 * while begins > 0 its RenderBuffers mixin hands every caller of bufferSource() / crumblingBufferSource() /
 * outlineBufferSource() its FullyBufferedMultiBufferSource, which buffers the whole frame's entity geometry per render type
 * and sorts and flushes it at the "translucent" point. Every other Iris user of these sources checks
 * `instanceof FullyBufferedMultiBufferSource` or `instanceof Groupable` and has a vanilla branch (readyUp /
 * endBatchWithType / groups; the banner renderer's grouping).
 *
 * What the switch does. When the pipeline this frame will use is already known to be Iris's VanillaRenderingPipeline (no
 * shader pack: vanillaPipelineThisFrame), the beginLevelRendering() call in that HEAD handler is left out, and the matching
 * endLevelRendering() in its RETURN handler too (the decision is kept per renderLevel call on a small bit stack in
 * LevelRenderer, so nested renderLevel calls pair up). begins stays 0, so bufferSource() returns vanilla's BufferSource
 * and entities are buffered and flushed as in vanilla. A frame whose pipeline is not known yet (first frame, after a pack
 * reload, a new dimension) keeps the batched path; with a shader pack the batched path always stays.
 *
 * The prediction. Iris's own HEAD of renderLevel (net.irisshaders.iris.mixin.MixinLevelRenderer.iris$setupPipeline, default
 * priority 1000) runs after the batched handler and sets pipeline = Iris.getPipelineManager().preparePipeline(
 * Iris.getCurrentDimension()). PipelineManager.preparePipeline(dim) returns pipelinesPerDimension.get(dim) when that map holds
 * the dimension and creates a new pipeline otherwise, so the answer is true only when the map already holds a
 * VanillaRenderingPipeline for Iris.getCurrentDimension(); a missing entry answers false and the stock path runs that frame.
 * Iris's private static pipelineManager and PipelineManager.pipelinesPerDimension are read through method handles resolved
 * once (Iris.getPipelineManager() would create the manager as a side effect); if that fails (another Iris build, a closed
 * module), every frame takes the stock path and one WARN says why. packInUse() reproduces IrisApi.isShaderPackInUse() from
 * the same field (the shadow-mode check).
 *
 * Trade-off (what can differ, without a shader pack only): entities draw as in vanilla, render type by render type in
 * the order they are drawn, so where translucent layers of different entities overlap they can resolve in another order
 * than with Iris's batching. Nothing else changes; saved data, packets and the server are not involved.
 *
 * -Dbons_and_furious.oculusVanillaBuffersWithoutShaders=false is the emergency off at run time.
 * -Dbons_and_furious.oculusVanillaBuffersWithoutShaders.shadow=true (verification): batching is never left out; at the
 * RETURN of every renderLevel the prediction made at HEAD is compared with the pipeline Iris actually prepared that frame:
 * SHADOW_CHECKS counts frames, SHADOW_SKIPPABLE the frames that would have used vanilla buffers, SHADOW_MISMATCHES a frame
 * predicted vanilla whose pipeline is a shader pack's (must stay 0; WARN for the first 20), SHADOW_UNKNOWN a frame that kept
 * the batched path because its pipeline was not known yet although it then was vanilla (the first frame of a dimension).
 */
public final class NoPackBuffers {
    static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean IRIS = present();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.oculusVanillaBuffersWithoutShaders", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.oculusVanillaBuffersWithoutShaders.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong(), SHADOW_SKIPPABLE = new AtomicLong(),
            SHADOW_UNKNOWN = new AtomicLong();
    private static volatile boolean announced;

    private NoPackBuffers() {
    }

    private static boolean present() {
        try {
            Class.forName("net.irisshaders.iris.Iris", false, NoPackBuffers.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** True only when this frame's pipeline is already known to be the vanilla one (see the class comment). */
    public static boolean vanillaPipelineThisFrame() {
        return IRIS && Pipelines.vanillaPipelineThisFrame();
    }

    /**
     * IrisApi.isShaderPackInUse() without its side effects. False without Iris. True when Iris's fields cannot be read
     * (the shadow-mode check then counts no frame as one that may use vanilla buffers).
     */
    public static boolean packInUse() {
        return IRIS && Pipelines.packInUse();
    }

    /** The decision of vanillaPipelineThisFrame on given values (harness): the map's entry for the dimension is vanilla. */
    public static boolean nextIsVanilla(Map<?, ?> perDimension, Object dimension) {
        return Pipelines.nextIsVanilla(perDimension, dimension);
    }

    /** HEAD of Iris's renderLevel handler: the prediction for this renderLevel call (true = no shader pack this frame). */
    public static boolean predictVanilla() {
        return enabled && vanillaPipelineThisFrame();
    }

    /** Whether a call predicted vanilla leaves Iris's begin/end out (false in shadow mode). */
    public static boolean act(boolean predictedVanilla) {
        if (!predictedVanilla || SHADOW) return false;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: oculus_vanilla_buffers_without_shaders applies (no shader pack: entities use vanilla's buffers)");
        }
        return true;
    }

    /**
     * One per LevelRenderer (BatchedNoPackMixin's field): the decision made at the HEAD of each renderLevel call, kept on a
     * bit stack until its RETURN, so a left-out begin is always paired with a left-out end, also for nested renderLevel
     * calls. Depths beyond 62 take the stock path. Render thread only.
     */
    public static final class Frames {
        private long bits;
        private int depth;

        /** HEAD: true when Iris's beginLevelRendering() must run. */
        public boolean begin() {
            return begin(depth < 62 && predictVanilla());
        }

        /** HEAD with a given prediction (harness): true when Iris's beginLevelRendering() must run. */
        public boolean begin(boolean predictedVanilla) {
            boolean vanilla = false;
            if (depth < 62) {
                vanilla = predictedVanilla;
                bits = (bits << 1) | (vanilla ? 1L : 0L);
            }
            depth++;
            return !act(vanilla);
        }

        /** RETURN: true when Iris's endLevelRendering() must run (the HEAD decision of the same call, popped). */
        public boolean end() {
            boolean vanilla = false;
            if (depth > 0) {
                depth--;
                if (depth < 62) {
                    vanilla = (bits & 1L) != 0L;
                    bits >>>= 1;
                }
            }
            verify(vanilla);
            return !act(vanilla);
        }

        public int depth() {
            return depth;
        }
    }

    /** Shadow mode, RETURN of Iris's renderLevel handler: the HEAD prediction against the pipeline Iris prepared. */
    public static void verify(boolean predictedVanilla) {
        if (!SHADOW || !enabled) return;
        SHADOW_CHECKS.incrementAndGet();
        boolean pack = packInUse();
        if (predictedVanilla) {
            SHADOW_SKIPPABLE.incrementAndGet();
            if (pack) {
                long m = SHADOW_MISMATCHES.incrementAndGet();
                if (m <= 20)
                    LOGGER.warn("Bons and Furious: oculus_vanilla_buffers_without_shaders shadow mismatch #{}: the frame was predicted without a shader pack but used one", m);
            }
        } else if (!pack) {
            SHADOW_UNKNOWN.incrementAndGet();
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: oculus_vanilla_buffers_without_shaders applies in shadow mode (counting only, batching stays)");
        }
    }

    /** Iris's pipeline manager state, loaded only when Iris is installed and a pipeline question is asked. */
    static final class Pipelines {
        private static final MethodHandle MANAGER;          // static Iris.pipelineManager
        private static final MethodHandle PER_DIMENSION;    // PipelineManager.pipelinesPerDimension
        private static volatile boolean warned;

        static {
            MethodHandle manager = null, perDimension = null;
            Throwable why = null;
            try {
                manager = getter(Iris.class, "pipelineManager");
                perDimension = getter(PipelineManager.class, "pipelinesPerDimension");
            } catch (Throwable t) {
                why = t;
                manager = null;
                perDimension = null;
            }
            MANAGER = manager;
            PER_DIMENSION = perDimension;
            if (why != null) {
                warned = true;
                LOGGER.warn("Bons and Furious: oculus_vanilla_buffers_without_shaders cannot read Iris's pipeline state ({}); every frame keeps Iris's batching", why.toString());
            }
        }

        private Pipelines() {
        }

        private static MethodHandle getter(Class<?> owner, String name) throws ReflectiveOperationException {
            Field f = owner.getDeclaredField(name);
            f.setAccessible(true);
            return MethodHandles.lookup().unreflectGetter(f);
        }

        static boolean packInUse() {
            if (MANAGER == null) return true;
            try {
                PipelineManager m = (PipelineManager) MANAGER.invokeExact();
                if (m == null) return false;
                WorldRenderingPipeline p = m.getPipelineNullable();
                return p != null && !(p instanceof VanillaRenderingPipeline);
            } catch (Throwable t) {
                warnOnce(t);
                return true;
            }
        }

        static boolean vanillaPipelineThisFrame() {
            if (MANAGER == null || PER_DIMENSION == null) return false;
            try {
                PipelineManager m = (PipelineManager) MANAGER.invokeExact();
                if (m == null) return false;
                Map<?, ?> perDimension = (Map<?, ?>) PER_DIMENSION.invokeExact(m);
                return nextIsVanilla(perDimension, Iris.getCurrentDimension());
            } catch (Throwable t) {
                warnOnce(t);
                return false;
            }
        }

        static boolean nextIsVanilla(Map<?, ?> perDimension, Object dimension) {
            return perDimension != null && perDimension.get(dimension) instanceof VanillaRenderingPipeline;
        }

        private static void warnOnce(Throwable t) {
            if (warned) return;
            warned = true;
            LOGGER.warn("Bons and Furious: oculus_vanilla_buffers_without_shaders could not read Iris's pipeline state ({}); the stock path is used", t.toString());
        }
    }
}
