package bons.furious.patch.oculus_passes;

import bons.furious.mixin.oculus_passes.CompositeRenderTypeStateAccessor;
import bons.furious.mixin.oculus_passes.CompositeStateShardsAccessor;
import bons.furious.mixin.oculus_passes.WriteMaskDepthAccessor;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/**
 * Bons and Furious 1.0.49 switch vanilla_order_independent_sort_skip (Minecraft 1.20.1 on Forge 47.4.28, client; with
 * Oculus 1.8.0 also its batched entity buffers), a `change` switch, off by default. SRG member names. Idea: Collections Of
 * Optimizations (vanilla.skipOrderIndependentSort), idea text only.
 *
 * What the game does. RenderType.end (m_276775_) asks the BufferBuilder to sort the batch's quads back to front
 * (setQuadSorting m_277127_) for every render type created with sortOnUpload; BufferBuilder then builds a centroid per quad
 * (m_166794_: a Vector3f per quad) and writes a sorted index buffer at end() (m_166786_). Oculus's batched entity buffers
 * (SegmentedBufferBuilder) make the same call for such types. Vanilla's eyes layer is created with sortOnUpload, and mods
 * copy it for their glow layers (Cataclysm's cm_eyes: forty Aptrgangr sort whole models every frame).
 *
 * What the switch does. For a render type that is a vanilla CompositeRenderType in QUADS mode whose transparency shard is
 * vanilla's ADDITIVE (ONE, ONE), LIGHTNING (SRC_ALPHA, ONE) or GLINT (SRC_COLOR, ONE / ZERO, ONE) transparency, whose write
 * mask does not write depth and which uses no colour logic op (classify, decided once per render type), the sort is left
 * out while no shader pack is in use (IrisPasses.packInUse; always without Oculus): the quads keep the order they were
 * written in and end() uses the shared sequential index buffer instead of a sorted one. The vertices are the same bytes.
 * With such blending each fragment adds its colour (and alpha) to what is there and nothing in the batch writes depth, so
 * the order cannot change which fragments pass the depth test. By OpenGL's fixed-point blending rule (both values clamped
 * to [0, 1], the result converted back with rounding) every step adds round(255 x source term) to an exact stored value,
 * so on the game's 8-bit colour targets the sum should not depend on the order at all; the switch stays a `change` until
 * a screenshot comparison in game confirms that on real drivers. With a shader pack the sort always stays (packs define their own
 * blending and float targets).
 *
 * Trade-off (off by default): glow layers (eyes, lightning and glint style layers without depth writes) are drawn in the
 * order they were built instead of back to front, while no shader pack is in use.
 *
 * -Dbons_and_furious.vanillaOrderIndependentSortSkip=false is the emergency off at run time.
 * -Dbons_and_furious.vanillaOrderIndependentSortSkip.shadow=true (verification): every sort stays; SHADOW_CHECKS counts the
 * batches of order-free types, SHADOW_SKIPPABLE those the switch would leave unsorted (no shader pack in use), and each
 * time the side-effect-free pack test (IrisPasses.packInUse) is compared with Oculus's public IrisApi.isShaderPackInUse():
 * a difference counts in SHADOW_MISMATCHES (WARN for the first 20). The render types classified order-free are logged
 * once each (up to 40) so a test run can check the list.
 */
public final class SortSkip {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.vanillaOrderIndependentSortSkip", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.vanillaOrderIndependentSortSkip.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong(), SHADOW_SKIPPABLE = new AtomicLong();
    private static final AtomicLong LOGGED_TYPES = new AtomicLong();
    private static volatile boolean announced;

    /** The positions of the shards in CompositeState's state list (its constructor's order). */
    static final int TRANSPARENCY = 2, WRITE_MASK = 10, COLOR_LOGIC = 11, SHARDS = 13;

    private SortSkip() {
    }

    /**
     * Whether the draw order of the type's quads cannot change the picture without a shader pack (see the class comment).
     * The vanilla shard constants come from RenderTypeOrderFreeMixin (they are protected in RenderStateShard).
     */
    public static boolean classify(RenderType type, RenderStateShard additive, RenderStateShard lightning, RenderStateShard glint,
                                   RenderStateShard noColorLogic) {
        try {
            if (!((Object) type instanceof CompositeRenderTypeStateAccessor composite)) return false;
            RenderType.CompositeState state = composite.bons$state();
            if (state == null) return false;
            List<RenderStateShard> shards = ((CompositeStateShardsAccessor) (Object) state).bons$shards();
            boolean result = decide(type.m_173186_() == VertexFormat.Mode.QUADS, shards, additive, lightning, glint, noColorLogic,
                    w -> !((Object) w instanceof WriteMaskDepthAccessor mask) || mask.bons$writeDepth());
            if (result && SHADOW && LOGGED_TYPES.incrementAndGet() <= 40)
                IrisPasses.LOGGER.info("Bons and Furious: vanilla_order_independent_sort_skip: {} is order-free ({}, {})", type,
                        shards.get(TRANSPARENCY), shards.get(WRITE_MASK));
            return result;
        } catch (Throwable e) {
            IrisPasses.LOGGER.warn("Bons and Furious: vanilla_order_independent_sort_skip could not classify {} ({}); it keeps its sort", type, e.toString());
            return false;
        }
    }

    /**
     * The classification itself, on plain values (java.* types only, so a harness can feed it shards of another class
     * loader): QUADS, a state list of 13 shards, its transparency one of the three adding constants, its write mask not
     * writing depth, its colour logic the no-op constant.
     */
    public static boolean decide(boolean quads, List<?> shards, Object additive, Object lightning, Object glint, Object noColorLogic,
                                 java.util.function.Predicate<Object> writesDepth) {
        if (!quads || shards == null || shards.size() != SHARDS) return false;
        Object t = shards.get(TRANSPARENCY), w = shards.get(WRITE_MASK), logic = shards.get(COLOR_LOGIC);
        boolean addingBlend = t != null && (t == additive || t == lightning || t == glint);
        return addingBlend && w != null && !writesDepth.test(w) && logic == noColorLogic;
    }

    /** RenderType.end / SegmentedBufferBuilder for an order-free type: true = leave the quad sort out of this batch. */
    public static boolean leaveOutSort() {
        if (!enabled) return false;
        boolean pack = IrisPasses.packInUse();
        if (SHADOW) {
            SHADOW_CHECKS.incrementAndGet();
            if (!pack) SHADOW_SKIPPABLE.incrementAndGet();
            boolean api = IrisPasses.oculusPresent() && PublicApi.packInUse();
            if (api != pack) {
                long m = SHADOW_MISMATCHES.incrementAndGet();
                if (m <= 20)
                    IrisPasses.LOGGER.warn("Bons and Furious: vanilla_order_independent_sort_skip shadow mismatch #{}: pack in use {} here, {} by IrisApi", m, pack, api);
            }
            if (!announced) {
                announced = true;
                IrisPasses.LOGGER.info("Bons and Furious: vanilla_order_independent_sort_skip applies in shadow mode (counting only, every sort stays)");
            }
            return false;
        }
        if (pack) return false;
        if (!announced) {
            announced = true;
            IrisPasses.LOGGER.info("Bons and Furious: vanilla_order_independent_sort_skip applies (glow layers without depth writes are drawn unsorted while no shader pack is in use)");
        }
        return true;
    }

    /** SegmentedBufferBuilder's three sort calls: the batch's render type (its currentType). */
    public static boolean leaveOutSort(RenderType type) {
        return type instanceof OrderFreeType o && o.bons$orderFreeQuads() && leaveOutSort();
    }

    /** Shadow mode only: Oculus's public answer (it may create Iris's pipeline manager, which is why the switch never asks it). */
    static final class PublicApi {
        private PublicApi() {
        }

        static boolean packInUse() {
            return net.irisshaders.iris.api.v0.IrisApi.getInstance().isShaderPackInUse();
        }
    }
}
