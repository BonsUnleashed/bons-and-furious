package bons.furious.patch.oculus_passes;

import bons.furious.mixin.oculus_passes.CompositeRenderTypeStateAccessor;
import bons.furious.mixin.oculus_passes.CompositeStateShardsAccessor;
import bons.furious.mixin.oculus_passes.WriteMaskDepthAccessor;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.pipeline.PipelineManager;
import net.irisshaders.iris.pipeline.VanillaRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.slf4j.Logger;

/**
 * Bons and Furious 1.0.49 switch vanilla_order_independent_sort_skip (Minecraft 1.21.1 on NeoForge 21.1.252, client; with
 * Iris 1.8.12 also its batched entity buffers), a `change` switch, off by default. Idea: Collections Of Optimizations
 * (vanilla.skipOrderIndependentSort), idea text only.
 *
 * What the game does. MultiBufferSource.BufferSource.endBatch(RenderType, BufferBuilder) builds the batch's MeshData and,
 * for every render type created with sortOnUpload, calls MeshData.sortQuads with RenderSystem.getVertexSorting(): it
 * unpacks a centroid per quad (a Vector3f each), sorts them back to front and writes a sorted index buffer into the
 * batch's ByteBufferBuilder; RenderType.draw then uploads it (VertexBuffer.upload). Iris's batched entity buffers
 * (SegmentedBufferBuilder.getSegments, which Iris uses for every entity draw of a level frame, with or without a shader
 * pack) make the same sortQuads call when their shouldSortOnUpload(RenderType) says so. Vanilla's eyes layer
 * (RenderType.eyes) is created with sortOnUpload, and mods copy it for their glow layers.
 *
 * What the switch does. For a render type that is a vanilla CompositeRenderType in QUADS mode whose transparency shard is
 * vanilla's ADDITIVE (ONE, ONE), LIGHTNING (SRC_ALPHA, ONE) or GLINT (SRC_COLOR, ONE / ZERO, ONE) transparency, whose write
 * mask does not write depth and which uses no colour logic op (classify, decided once per render type), the sort is left
 * out while no shader pack is in use (packInUse; always without Iris): the MeshData keeps no index buffer, so
 * VertexBuffer.upload binds RenderSystem's shared sequential QUADS index buffer (0, 1, 2, 2, 3, 0 per quad, the pattern
 * the sorted buffer writes per quad) and the quads are drawn in the order they were written. The vertices are the same
 * bytes, and a new MeshData is built for every batch, so nothing carries into the next one. With such blending each
 * fragment adds its colour (and alpha) to what is there and nothing in the batch writes depth, so the order cannot change
 * which fragments pass the depth test. By OpenGL's fixed-point blending rule (both values clamped to [0, 1], the result
 * converted back with rounding) every step adds round(255 x source term) to an exact stored value, so on the game's 8-bit
 * colour targets the sum should not depend on the order at all; the switch stays a `change` until a screenshot comparison
 * in game confirms that on real drivers. With a shader pack the sort always stays (packs define their own blending and
 * float targets).
 *
 * Trade-off (off by default): glow layers (eyes, lightning and glint style layers without depth writes) are drawn in the
 * order they were built instead of back to front, while no shader pack is in use.
 *
 * packInUse() gives the answer IrisApi.isShaderPackInUse() gives (Iris.getPipelineManager().getPipelineNullable() exists
 * and is not a VanillaRenderingPipeline), read from Iris's static pipelineManager field instead of through
 * Iris.getPipelineManager(), because that getter creates the manager when it does not exist yet: asking from inside a
 * batch's end must never create it there. A manager that does not exist yet has no pipeline: false, which is what
 * isShaderPackInUse() answers then too (PipelineManager's constructor starts with a VanillaRenderingPipeline). No Iris
 * class is touched unless Iris is installed: every Iris reference sits in the nested classes IrisPack and PublicApi,
 * which the JVM loads only when IRIS is true (this class's method signatures hold no Iris type). If the field cannot be
 * read (another Iris build, a closed module) the answer is "pack in use" (the sort stays) and one WARN says why.
 *
 * -Dbons_and_furious.vanillaOrderIndependentSortSkip=false is the emergency off at run time.
 * -Dbons_and_furious.vanillaOrderIndependentSortSkip.shadow=true (verification): every sort stays; SHADOW_CHECKS counts the
 * batches of order-free types, SHADOW_SKIPPABLE those the switch would leave unsorted (no shader pack in use), and each
 * time the side-effect-free pack test is compared with Iris's public IrisApi.isShaderPackInUse(): a difference counts in
 * SHADOW_MISMATCHES (WARN for the first 20). The render types classified order-free are logged once each (up to 40) so a
 * test run can check the list.
 */
public final class SortSkip {
    static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.vanillaOrderIndependentSortSkip", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.vanillaOrderIndependentSortSkip.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong(), SHADOW_SKIPPABLE = new AtomicLong();
    private static final AtomicLong LOGGED_TYPES = new AtomicLong();
    private static volatile boolean announced;
    private static final boolean IRIS = irisInstalled();

    /** The positions of the shards in CompositeState's state list (its constructor's order). */
    static final int TRANSPARENCY = 2, WRITE_MASK = 10, COLOR_LOGIC = 11, SHARDS = 13;

    private SortSkip() {
    }

    private static boolean irisInstalled() {
        try {
            Class.forName("net.irisshaders.iris.api.v0.IrisApi", false, SortSkip.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** True when Iris is installed. */
    public static boolean irisPresent() {
        return IRIS;
    }

    /** Whether the draw order of the type's quads cannot change the picture without a shader pack (see the class comment). */
    public static boolean classify(RenderType type) {
        try {
            if (!((Object) type instanceof CompositeRenderTypeStateAccessor composite)) return false;
            RenderType.CompositeState state = composite.bons$state();
            if (state == null) return false;
            List<RenderStateShard> shards = ((CompositeStateShardsAccessor) (Object) state).bons$shards();
            boolean result = decide(type.mode() == VertexFormat.Mode.QUADS, shards, RenderStateShard.ADDITIVE_TRANSPARENCY,
                    RenderStateShard.LIGHTNING_TRANSPARENCY, RenderStateShard.GLINT_TRANSPARENCY, RenderStateShard.NO_COLOR_LOGIC,
                    w -> !((Object) w instanceof WriteMaskDepthAccessor mask) || mask.bons$writeDepth());
            if (result && SHADOW && LOGGED_TYPES.incrementAndGet() <= 40)
                LOGGER.info("Bons and Furious: vanilla_order_independent_sort_skip: {} is order-free ({}, {})", type,
                        shards.get(TRANSPARENCY), shards.get(WRITE_MASK));
            return result;
        } catch (Throwable e) {
            LOGGER.warn("Bons and Furious: vanilla_order_independent_sort_skip could not classify {} ({}); it keeps its sort", type, e.toString());
            return false;
        }
    }

    /**
     * The classification itself, on plain values (java.* types only, so a harness can feed it shards of another class
     * loader): QUADS, a state list of 13 shards, its transparency one of the three adding constants, its write mask not
     * writing depth, its colour logic the no-op constant.
     */
    public static boolean decide(boolean quads, List<?> shards, Object additive, Object lightning, Object glint, Object noColorLogic,
                                 Predicate<Object> writesDepth) {
        if (!quads || shards == null || shards.size() != SHARDS) return false;
        Object t = shards.get(TRANSPARENCY), w = shards.get(WRITE_MASK), logic = shards.get(COLOR_LOGIC);
        boolean addingBlend = t != null && (t == additive || t == lightning || t == glint);
        return addingBlend && w != null && !writesDepth.test(w) && logic == noColorLogic;
    }

    /** BufferSource.endBatch / SegmentedBufferBuilder for an order-free type: true = leave the quad sort out of this batch. */
    public static boolean leaveOutSort() {
        if (!enabled) return false;
        boolean pack = packInUse();
        if (SHADOW) {
            SHADOW_CHECKS.incrementAndGet();
            if (!pack) SHADOW_SKIPPABLE.incrementAndGet();
            boolean api = IRIS && PublicApi.packInUse();
            if (api != pack) {
                long m = SHADOW_MISMATCHES.incrementAndGet();
                if (m <= 20)
                    LOGGER.warn("Bons and Furious: vanilla_order_independent_sort_skip shadow mismatch #{}: pack in use {} here, {} by IrisApi", m, pack, api);
            }
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: vanilla_order_independent_sort_skip applies in shadow mode (counting only, every sort stays)");
            }
            return false;
        }
        if (pack) return false;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_order_independent_sort_skip applies (glow layers without depth writes are drawn unsorted while no shader pack is in use)");
        }
        return true;
    }

    /** The batch's render type: true = an order-free type whose sort is left out of this batch. */
    public static boolean leaveOutSort(RenderType type) {
        return type instanceof OrderFreeType o && o.bons$orderFreeQuads() && leaveOutSort();
    }

    /**
     * IrisApi.isShaderPackInUse() without its side effect (see the class comment). False without Iris. True when Iris's
     * field cannot be read: the switch changes something only while no pack is in use, so it keeps the stock path.
     */
    public static boolean packInUse() {
        return IRIS && IrisPack.packInUse();
    }

    /** Iris's pipeline manager state, loaded only when Iris is installed and the pack question is asked. */
    static final class IrisPack {
        private static final MethodHandle MANAGER;   // static Iris.pipelineManager
        private static volatile boolean warned;

        static {
            MethodHandle manager = null;
            Throwable why = null;
            try {
                Field f = Iris.class.getDeclaredField("pipelineManager");
                f.setAccessible(true);
                manager = MethodHandles.lookup().unreflectGetter(f);
            } catch (Throwable t) {
                why = t;
                manager = null;
            }
            MANAGER = manager;
            if (why != null) {
                warned = true;
                LOGGER.warn("Bons and Furious: vanilla_order_independent_sort_skip cannot read Iris's pipeline state ({}); every sort stays", why.toString());
            }
        }

        private IrisPack() {
        }

        static boolean packInUse() {
            if (MANAGER == null) return true;
            try {
                PipelineManager m = (PipelineManager) MANAGER.invokeExact();
                if (m == null) return false;
                WorldRenderingPipeline p = m.getPipelineNullable();
                return p != null && !(p instanceof VanillaRenderingPipeline);
            } catch (Throwable t) {
                if (!warned) {
                    warned = true;
                    LOGGER.warn("Bons and Furious: vanilla_order_independent_sort_skip could not read Iris's pipeline state ({}); every sort stays", t.toString());
                }
                return true;
            }
        }
    }

    /** Shadow mode only: Iris's public answer (it may create Iris's pipeline manager, which is why the switch never asks it). */
    static final class PublicApi {
        private PublicApi() {
        }

        static boolean packInUse() {
            return IrisApi.getInstance().isShaderPackInUse();
        }
    }
}
