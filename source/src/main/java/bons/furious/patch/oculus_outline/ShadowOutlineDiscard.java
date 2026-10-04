package bons.furious.patch.oculus_outline;

import bons.furious.mixin.oculus_outline.BufferSourceAccessor;
import bons.furious.mixin.oculus_outline.ByteBufferBuilderAccessor;
import bons.furious.mixin.oculus_outline.OutlineSourceAccessor;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch oculus_shadow_outline_discard (tested build: Iris 1.8.12+mc1.21.1 for NeoForge with Sodium
 * 0.6.13 on Minecraft 1.21.1 / NeoForge 21.1.252, client only; first written for Oculus 1.8.0 on Minecraft 1.20.1 /
 * Forge 47.4.16). Helper of bons.furious.mixin.oculus_outline.ShadowOutlineDiscardMixin.
 *
 * Fix: the shader shadow pass renders entities with its own RenderBuffers, which it puts into LevelRenderer.renderBuffers
 * for the duration of the pass, and while a pass is open that RenderBuffers' outlineBufferSource() answers Iris's own
 * OutlineBufferSource. Nothing ever ends that source's outline batch (the main pass's endOutlineBatch is called on the
 * player's buffers). Armor renderers that pick their buffer themselves write a glowing wearer's outline into
 * levelRenderer.renderBuffers.outlineBufferSource(): GeckoLib 4.9.3 for 1.21.1 still does (GeoArmorRenderer.renderToBuffer
 * when shouldShowEntityOutlines() and the wearer glows), as GeckoLib 4.8.4 and AzureLib 3.0.13 did on 1.20.1. In the
 * shadow pass those vertices land in the never-ended outline batch. On 1.21.1, vanilla's immediate outline source keeps
 * the started BufferBuilder of that outline type (QUADS, so getBuffer keeps appending to it) on its shared ByteBufferBuilder:
 * every shadow pass appends the outline vertices again and the native buffer grows (reallocated in steps of up to 2 MB),
 * until a different outline type is requested in a later pass, and then all of them (from many frames, at old positions)
 * are drawn with the outline render type into the shadow buffers. ImmediatelyFast 1.6.9 / 1.8.5 address this upstream
 * ("clear the Iris shadow pass outline buffer"; idea text only). The intended behaviour is that shadow-pass outline
 * vertices are neither kept nor drawn: the main pass draws the glow outline from its own buffers.
 *
 * discard() runs at the end of each shadow pass (just before Iris closes the pass) and ends the pending outline batch
 * without drawing it, i.e. exactly the state changes of OutlineBufferSource.endOutlineBatch() minus the draw:
 *   - vanilla's immediate BufferSource (exact class, no fixed buffers, as OutlineBufferSource creates it): endBatch() is
 *     endLastBatch() (the fixed-buffer loop is empty): endBatch(lastSharedType) removes that type's started builder and
 *     runs endBatch(type, builder): builder.build() and, when it gives a mesh, RenderType.draw(mesh), whose upload
 *     (VertexBuffer.upload) closes the mesh, returning its ByteBufferBuilder result (the buffer resets when no result is
 *     left); then lastSharedType becomes null. discard() makes the same removal and build(), closes the mesh itself instead
 *     of drawing it (a sortOnUpload type would only have added an index result to the mesh being drawn) and clears
 *     lastSharedType;
 *   - any other source class (a mod that replaces MultiBufferSource.immediate, e.g. ImmediatelyFast): left alone (WARN
 *     once), as shipped.
 * Nothing else is touched: Iris draws the shadow pass's entity batch before this point, and the player's buffers (main
 * pass) are not the shadow RenderBuffers.
 *
 * Ported to 1.21.1: the vanilla discard follows the 1.21 buffer API (1.20.1: remove the shared BufferBuilder from the
 * started set, end() it and release() the RenderedBuffer, clear the Optional last state). The ImmediatelyFast 1.5.5 branch
 * is gone: IF 1.5.5 has no 1.21.1 build, and IF for 1.21.1 (1.6.x) is this switch's idea source, whose code is not read;
 * its outline source is left alone with one WARN, exactly as the 1.20.1 helper treated an IF build it could not verify.
 */
public final class ShadowOutlineDiscard {
    /** Runtime switch; -Dbons_and_furious.shadowOutlineDiscard=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.shadowOutlineDiscard", "true"));
    /**
     * Rig probe: -Dbons_and_furious.shadowOutlineDiscard.shadow=true discards nothing; it counts the shadow passes that end
     * with a pending outline batch (SHADOW_MISMATCHES, = the bug firing) out of all passes (SHADOW_CHECKS) and keeps the
     * outline source's shared buffer capacity in BYTES_HELD, so the growth can be watched.
     */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.shadowOutlineDiscard.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Batches discarded, sources left alone (unknown class), outline buffer bytes seen in SHADOW mode. */
    public static final AtomicLong DISCARDED = new AtomicLong(), LEFT_ALONE = new AtomicLong(), BYTES_HELD = new AtomicLong();

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced, warned;

    private ShadowOutlineDiscard() {
    }

    /** End of a shader shadow pass, while it is still open: drop the pending outline batch of its buffers. */
    public static void discard(RenderBuffers shadowBuffers) {
        OutlineBufferSource outline = shadowBuffers.outlineBufferSource();
        MultiBufferSource.BufferSource source = ((OutlineSourceAccessor) outline).bons$outlineSource();
        Class<?> c = source.getClass();
        if (c != MultiBufferSource.BufferSource.class) {
            leftAlone(c.getName());
            return;
        }
        if (SHADOW) {
            probe(source);
            return;
        }
        if (discardVanilla(source)) counted();
    }

    /**
     * Vanilla immediate BufferSource: the state changes of endBatch() (= endLastBatch() without fixed buffers) minus
     * RenderType.draw. Returns whether a batch was pending.
     */
    static boolean discardVanilla(MultiBufferSource.BufferSource source) {
        BufferSourceAccessor a = (BufferSourceAccessor) source;
        if (!a.bons$fixedBuffers().isEmpty()) return false;
        RenderType last = a.bons$lastSharedType();
        if (last == null) return false;
        BufferBuilder builder = a.bons$startedBuilders().remove(last);
        if (builder != null) {
            MeshData mesh = builder.build();
            if (mesh != null) mesh.close();
        }
        a.bons$setLastSharedType(null);
        return builder != null;
    }

    private static void leftAlone(String cls) {
        LEFT_ALONE.incrementAndGet();
        if (!warned) {
            warned = true;
            LOGGER.warn("Bons and Furious: oculus_shadow_outline_discard leaves the shadow outline source alone ({})", cls);
        }
    }

    private static void counted() {
        DISCARDED.incrementAndGet();
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: oculus_shadow_outline_discard: an outline batch written during the shader shadow pass was discarded "
                    + "(armor renderers that pick the level renderer's outline buffer themselves)");
        }
    }

    /** SHADOW mode (vanilla source): count passes ending with a pending outline batch and the shared buffer's bytes. */
    static void probe(MultiBufferSource.BufferSource source) {
        SHADOW_CHECKS.incrementAndGet();
        BufferSourceAccessor a = (BufferSourceAccessor) source;
        RenderType last = a.bons$lastSharedType();
        boolean pending = last != null && a.bons$startedBuilders().containsKey(last);
        ByteBufferBuilderAccessor shared = (ByteBufferBuilderAccessor) a.bons$sharedBuffer();
        long bytes = shared.bons$capacity();
        BYTES_HELD.set(bytes);
        if (pending && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
            LOGGER.warn("Bons and Furious: oculus_shadow_outline_discard SHADOW: shadow pass ended with a pending outline batch "
                    + "({} bytes allocated, {} written in the outline buffer)", bytes, shared.bons$writeOffset());
        }
    }
}
