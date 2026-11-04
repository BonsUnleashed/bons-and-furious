package bons.furious.patch.oculus_passes;

import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.Map;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.pipeline.PipelineManager;
import net.irisshaders.iris.pipeline.VanillaRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import org.slf4j.Logger;

/**
 * The Oculus (Iris) state the five oculus_passes switches ask (Bons and Furious 1.0.49, client; Oculus 1.8.0 tested).
 * One helper behind oculus_shadow_skip_banner_patterns, oculus_shadow_skip_name_tags, oculus_shadow_skip_sign_text
 * (shadowPass), oculus_vanilla_buffers_without_shaders (vanillaPipelineThisFrame) and vanilla_order_independent_sort_skip
 * (packInUse). SRG member names for Minecraft; Oculus's own names for its classes.
 *
 * No Oculus class is touched unless Oculus is installed: every Oculus reference sits in the nested classes Api and
 * Pipelines, which the JVM loads only when OCULUS is true (the outer class's method signatures hold no Oculus type, so
 * verifying this class never loads one either). Without Oculus there is no shadow pass and no shader pack.
 *
 *  - shadowPass(): Oculus's public API IrisApi.getInstance().isRenderingShadowPass(), which is
 *    ShadowRenderingState.areShadowsCurrentlyBeingRendered() = ShadowRenderer.ACTIVE (set at the start and cleared at the
 *    end of ShadowRenderer.renderShadows). Reading it has no side effect (ShadowRenderer's static initialiser only sets
 *    ACTIVE = false).
 *  - packInUse(): the answer IrisApi.isShaderPackInUse() gives (the pipeline manager's current pipeline exists and is not
 *    a VanillaRenderingPipeline), read from Iris's pipelineManager field instead of through Iris.getPipelineManager(),
 *    because that getter creates the manager and loads a pending shader pack as a side effect: asking from inside
 *    RenderType.end must never move a pack load there. A manager that does not exist yet has no pipeline: false, which is
 *    what isShaderPackInUse() answers then too (a fresh manager starts with a VanillaRenderingPipeline).
 *  - vanillaPipelineThisFrame(): whether the pipeline Iris's own HEAD of LevelRenderer.renderLevel (iris$setupPipeline,
 *    priority 1000) is about to prepare is a VanillaRenderingPipeline. Oculus's batched-entity HEAD handler (priority 999)
 *    runs first, so the manager's current pipeline may still be the last frame's, or a new dimension's pipeline may not
 *    exist yet. PipelineManager.preparePipeline(dim) returns pipelinesPerDimension.get(dim) when that map holds the
 *    dimension and creates a new pipeline otherwise, so the answer is true only when the map already holds a
 *    VanillaRenderingPipeline for Iris.getCurrentDimension() (the value Iris's HEAD passes); a missing entry (the first
 *    frame, after a pack reload, a new dimension) answers false and the stock path runs that frame.
 * Iris's private fields are read through method handles resolved once; if that fails (another Oculus build, a closed
 * module), both pipeline questions answer "unknown" (packInUse true, vanillaPipelineThisFrame false: the stock path) and
 * one WARN says why.
 */
public final class IrisPasses {
    static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean OCULUS = present();

    private IrisPasses() {
    }

    private static boolean present() {
        try {
            Class.forName("net.irisshaders.iris.api.v0.IrisApi", false, IrisPasses.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** True when Oculus is installed. */
    public static boolean oculusPresent() {
        return OCULUS;
    }

    /** True while Oculus renders its shadow map. False without Oculus. */
    public static boolean shadowPass() {
        return OCULUS && Api.shadowPass();
    }

    /**
     * IrisApi.isShaderPackInUse() without its side effects (see the class comment). False without Oculus. True when Iris's
     * fields cannot be read: the callers change something only while no pack is in use, so they keep the stock path.
     */
    public static boolean packInUse() {
        return OCULUS && Pipelines.packInUse();
    }

    /** True only when this frame's pipeline is already known to be the vanilla one (see the class comment). */
    public static boolean vanillaPipelineThisFrame() {
        return OCULUS && Pipelines.vanillaPipelineThisFrame();
    }

    /**
     * Verification (shadow modes) only: whether the calling thread is inside Oculus's ShadowRenderer.renderShadows, an
     * answer that does not depend on ShadowRenderer.ACTIVE.
     */
    public static boolean stackInShadowRenderer() {
        return StackWalker.getInstance().walk(frames -> frames.anyMatch(f ->
                f.getClassName().equals("net.irisshaders.iris.shadows.ShadowRenderer") && f.getMethodName().equals("renderShadows")));
    }

    /**
     * The decision of vanillaPipelineThisFrame on given values (harness): the map's entry for the dimension is a
     * VanillaRenderingPipeline. Needs Oculus on the class path.
     */
    public static boolean nextIsVanilla(Map<?, ?> perDimension, Object dimension) {
        return Pipelines.nextIsVanilla(perDimension, dimension);
    }

    /** Oculus's public API, loaded only when Oculus is installed. */
    static final class Api {
        private Api() {
        }

        static boolean shadowPass() {
            return IrisApi.getInstance().isRenderingShadowPass();
        }
    }

    /** Iris's pipeline manager state, loaded only when Oculus is installed and a pipeline question is asked. */
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
                LOGGER.warn("Bons and Furious: oculus_passes cannot read Oculus's pipeline state ({}); the no-shader-pack switches keep the stock path", why.toString());
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
            LOGGER.warn("Bons and Furious: oculus_passes could not read Oculus's pipeline state ({}); the stock path is used", t.toString());
        }
    }
}
