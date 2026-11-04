package bons.furious.patch.oculus_passes;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Bons and Furious 1.0.49 switch oculus_vanilla_buffers_without_shaders (Oculus 1.8.0 tested, client), a `change` switch,
 * off by default. Idea: Collections Of Optimizations (oculus.skipBatchingWithoutShaders), idea text only.
 *
 * What Oculus does. Its batched-entity mixin on LevelRenderer.renderLevel (net.irisshaders.batchedentityrendering.mixin.
 * MixinLevelRenderer, priority 999) calls RenderBuffersExt.beginLevelRendering() at HEAD on every frame, shader pack or not;
 * while begins > 0 its RenderBuffers mixin hands every caller of bufferSource() / crumblingBufferSource() /
 * outlineBufferSource() its FullyBufferedMultiBufferSource, which buffers the whole frame's entity geometry per render type
 * and sorts and flushes it at the "translucent" point. Every other Oculus user of these sources checks
 * `instanceof FullyBufferedMultiBufferSource` and has a vanilla branch (Groupable / readyUp / endBatchWithType).
 *
 * What the switch does. When the pipeline this frame will use is already known to be Iris's VanillaRenderingPipeline (no
 * shader pack: IrisPasses.vanillaPipelineThisFrame), the beginLevelRendering() call in that HEAD handler is left out, and the
 * matching endLevelRendering() in its RETURN handler too (the decision is kept per renderLevel call on a small bit stack in
 * LevelRenderer, so nested renderLevel calls pair up). begins stays 0, so bufferSource() returns vanilla's BufferSource
 * and entities are buffered and flushed as in vanilla. A frame whose pipeline is not known yet (first frame, after a pack
 * reload, a new dimension) keeps the batched path; with a shader pack the batched path always stays.
 *
 * Trade-off (what can differ, without a shader pack only): entities draw as in vanilla, render type by render type in
 * the order they are drawn, so where translucent layers of different entities overlap they can resolve in another order
 * than with Oculus's batching. Nothing else changes; saved data, packets and the server are not involved.
 *
 * -Dbons_and_furious.oculusVanillaBuffersWithoutShaders=false is the emergency off at run time.
 * -Dbons_and_furious.oculusVanillaBuffersWithoutShaders.shadow=true (verification): batching is never left out; at the
 * RETURN of every renderLevel the prediction made at HEAD is compared with the pipeline Iris actually prepared that frame:
 * SHADOW_CHECKS counts frames, SHADOW_SKIPPABLE the frames that would have used vanilla buffers, SHADOW_MISMATCHES a frame
 * predicted vanilla whose pipeline is a shader pack's (must stay 0; WARN for the first 20), SHADOW_UNKNOWN a frame that kept
 * the batched path because its pipeline was not known yet although it then was vanilla (the first frame of a dimension).
 */
public final class NoPackBuffers {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.oculusVanillaBuffersWithoutShaders", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.oculusVanillaBuffersWithoutShaders.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong(), SHADOW_SKIPPABLE = new AtomicLong(),
            SHADOW_UNKNOWN = new AtomicLong();
    private static volatile boolean announced;

    private NoPackBuffers() {
    }

    /** HEAD of Oculus's renderLevel handler: the prediction for this renderLevel call (true = no shader pack this frame). */
    public static boolean predictVanilla() {
        return enabled && IrisPasses.vanillaPipelineThisFrame();
    }

    /** Whether a call predicted vanilla leaves Oculus's begin/end out (false in shadow mode). */
    public static boolean act(boolean predictedVanilla) {
        if (!predictedVanilla || SHADOW) return false;
        if (!announced) {
            announced = true;
            IrisPasses.LOGGER.info("Bons and Furious: oculus_vanilla_buffers_without_shaders applies (no shader pack: entities use vanilla's buffers)");
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

        /** HEAD: true when Oculus's beginLevelRendering() must run. */
        public boolean begin() {
            return begin(depth < 62 && predictVanilla());
        }

        /** HEAD with a given prediction (harness): true when Oculus's beginLevelRendering() must run. */
        public boolean begin(boolean predictedVanilla) {
            boolean vanilla = false;
            if (depth < 62) {
                vanilla = predictedVanilla;
                bits = (bits << 1) | (vanilla ? 1L : 0L);
            }
            depth++;
            return !act(vanilla);
        }

        /** RETURN: true when Oculus's endLevelRendering() must run (the HEAD decision of the same call, popped). */
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

    /** Shadow mode, RETURN of Oculus's renderLevel handler: the HEAD prediction against the pipeline Iris prepared. */
    public static void verify(boolean predictedVanilla) {
        if (!SHADOW || !enabled) return;
        SHADOW_CHECKS.incrementAndGet();
        boolean pack = IrisPasses.packInUse();
        if (predictedVanilla) {
            SHADOW_SKIPPABLE.incrementAndGet();
            if (pack) {
                long m = SHADOW_MISMATCHES.incrementAndGet();
                if (m <= 20)
                    IrisPasses.LOGGER.warn("Bons and Furious: oculus_vanilla_buffers_without_shaders shadow mismatch #{}: the frame was predicted without a shader pack but used one", m);
            }
        } else if (!pack) {
            SHADOW_UNKNOWN.incrementAndGet();
        }
        if (!announced) {
            announced = true;
            IrisPasses.LOGGER.info("Bons and Furious: oculus_vanilla_buffers_without_shaders applies in shadow mode (counting only, batching stays)");
        }
    }
}
