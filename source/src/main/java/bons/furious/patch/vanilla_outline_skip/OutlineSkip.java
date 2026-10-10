package bons.furious.patch.vanilla_outline_skip;

import com.mojang.blaze3d.pipeline.RenderTarget;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryUtil;

/**
 * Bons and Furious switch vanilla_entity_outline_composite_skip (tested build: Minecraft 1.21.1 / NeoForge 21.1.252,
 * client only; first written for Minecraft 1.20.1 / Forge 47.4.16).
 *
 * Every frame GameRenderer.render calls LevelRenderer.doEntityOutline right after renderLevel, which (whenever outlines
 * can be shown, i.e. always in a world) blends the entity-outline target over the screen: blendFuncSeparate(SRC_ALPHA,
 * ONE_MINUS_SRC_ALPHA, ZERO, ONE) with blending enabled, then blitToScreen draws one full-screen quad with the blit_screen
 * shader, whose fragment is the target's texel. renderLevel clears that target to (0, 0, 0, 0) earlier in the frame
 * (PostChain.addTempTarget gives its targets that clear colour); unless a glowing entity, a custom-outline block entity, an
 * outline effect request or a mod's outline render type wrote into it, every texel still has alpha 0, and blending it
 * changes no pixel (colour: src * 0 + dst * 1, alpha: src * 0 + dst * 1, and the blit masks alpha writes anyway; exact in
 * the 8-bit target). At 3840 x 2066 that quad is ~8 million blended texels per frame on a GPU-bound client.
 *
 * Tracking (render thread): afterClear() records the target, its framebuffer and colour texture when renderLevel has
 * cleared it; every bind of that framebuffer (GlStateManager._glBindFramebuffer: RenderTarget.bindWrite, the outline
 * render types, PostChain passes, copyDepthFrom, any GlStateManager bind by id) and every bind of its colour texture
 * (GlStateManager._bindTexture) after the clear marks it written. When doEntityOutline's blit comes and the target was
 * cleared this frame and never touched since, blitToScreen runs as usual - shader, sampler, viewport, colour and depth
 * masks, the immediate vertex buffer's bind and upload, the shader's clear - and only its one glDrawElements is left out
 * (BufferUploader.draw, the BLIT_SCREEN draw). The GL state GlStateManager tracks, the GL objects' contents and the screen
 * are what they would have been.
 *
 * Ported to 1.21.1: blitToScreen(w, h, false) now calls _blitToScreen directly (1.20.1 had a recordRenderCall branch that
 * never ran: RenderSystem.isInInitPhase() returned true), which uploads a 4-vertex DefaultVertexFormat.BLIT_SCREEN quad
 * (position only) through BufferUploader.draw(MeshData). The 1.21.1 blit_screen core shader writes texture(DiffuseSampler)
 * as is and carries no blend block (1.21.1 ShaderInstance.apply sets no blend state; 1.20.1's blit shader multiplied by the
 * vertex colour and ColorModulator and applied its own srcalpha / 1-srcalpha blend), so doEntityOutline's own blend
 * function governs the composite: a transparent target still leaves every pixel unchanged. The skipped draw is checked to
 * be that BLIT_SCREEN draw (LEFT_OUT counts the draws actually left out).
 */
public final class OutlineSkip {
    /** Runtime switch; -Dbons_and_furious.vanillaEntityOutlineCompositeSkip=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.vanillaEntityOutlineCompositeSkip", "true"));
    /**
     * Rig probe: -Dbons_and_furious.vanillaEntityOutlineCompositeSkip.shadow=true never skips; on every frame where the switch
     * would skip, it reads the outline target's colour texture back (glGetTexImage, slow: rig only) and counts frames whose
     * texture has any texel with alpha != 0 (SHADOW_MISMATCHES: a writer the tracking missed; WARN for 20).
     */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.vanillaEntityOutlineCompositeSkip.shadow");
    /** Rig timing: -Dbons_and_furious.vanillaEntityOutlineCompositeSkip.timing=true measures the composite with GL_TIME_ELAPSED. */
    public static final boolean TIMING = Boolean.getBoolean("bons_and_furious.vanillaEntityOutlineCompositeSkip.timing");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    public static final AtomicLong SKIPPED = new AtomicLong(), DRAWN = new AtomicLong();
    /** Composite draws actually left out by BufferUploaderOutlineMixin (equals SKIPPED unless another mod replaced the blit). */
    public static final AtomicLong LEFT_OUT = new AtomicLong();
    /** Sum of GPU nanoseconds measured for the composite (TIMING) and the number of measured frames. */
    public static final AtomicLong GPU_NANOS = new AtomicLong(), GPU_FRAMES = new AtomicLong();

    /** The target, framebuffer and colour texture recorded at the last clear (render thread). */
    static RenderTarget target;
    public static int fbo = -1, texture = -1;
    static boolean cleared;
    public static boolean written;
    /** True only inside the skipped composite's blitToScreen, until its BLIT_SCREEN draw: BufferUploader.draw leaves it out. */
    public static boolean skipDraw;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static boolean announced;
    private static int query = -1;
    private static boolean queryPending;

    private OutlineSkip() {
    }

    /** renderLevel, right after it cleared the entity-outline target. */
    public static void afterClear(RenderTarget t) {
        target = t;
        fbo = t.frameBufferId;
        texture = t.getColorTextureId();
        cleared = true;
        written = false;
    }

    /** GlStateManager._glBindFramebuffer(target, id): any bind of the outline framebuffer after its clear. */
    public static int bound(int id) {
        if (id == fbo && id > 0) written = true;
        return id;
    }

    /** GlStateManager._bindTexture(id): any bind of the outline colour texture after its clear. */
    public static int boundTexture(int id) {
        if (id == texture && id > 0) written = true;
        return id;
    }

    /** doEntityOutline, before its blit: may the draw be left out? (one decision per clear) */
    public static boolean canSkip(RenderTarget t) {
        boolean ok = enabled && cleared && !written && t == target && t.frameBufferId == fbo && t.getColorTextureId() == texture;
        cleared = false;
        if (ok && SHADOW) {
            shadowCheck(t);
            return false;
        }
        if (ok) {
            SKIPPED.incrementAndGet();
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: vanilla_entity_outline_composite_skip: the entity-outline composite is left out while nothing was outlined");
            }
        } else DRAWN.incrementAndGet();
        return ok;
    }

    /**
     * SHADOW: the outline target must hold alpha 0 everywhere when the switch would skip. Reads into client memory, so a
     * pixel-pack buffer some other code left bound is unbound for the read and bound again afterwards.
     */
    static void shadowCheck(RenderTarget t) {
        int w = t.width, h = t.height;
        ByteBuffer buf = MemoryUtil.memAlloc(w * h * 4);
        try {
            int prev = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            int pack = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
            if (pack != 0) GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, t.getColorTextureId());
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prev);
            if (pack != 0) GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pack);
            SHADOW_CHECKS.incrementAndGet();
            for (int i = 3; i < w * h * 4; i += 4) {
                if (buf.get(i) != 0) {
                    if (SHADOW_MISMATCHES.incrementAndGet() <= 20)
                        LOGGER.warn("Bons and Furious: vanilla_entity_outline_composite_skip SHADOW: the outline target holds alpha {} at texel {} "
                                + "although nothing bound it since its clear", buf.get(i) & 255, i / 4);
                    return;
                }
            }
        } finally {
            MemoryUtil.memFree(buf);
        }
    }

    /** TIMING: around the composite (skipped or drawn), GL_TIME_ELAPSED; the previous frame's result is read first. */
    public static void timingBegin() {
        if (query < 0) query = GL15.glGenQueries();
        if (queryPending) {
            long ns = GL33.glGetQueryObjecti64(query, GL15.GL_QUERY_RESULT);
            GPU_NANOS.addAndGet(ns);
            if (GPU_FRAMES.incrementAndGet() % 600 == 0)
                LOGGER.info("Bons and Furious: vanilla_entity_outline_composite_skip TIMING: {} us per frame on the GPU over {} frames ({} skipped, {} drawn)",
                        GPU_NANOS.get() / GPU_FRAMES.get() / 1000.0, GPU_FRAMES.get(), SKIPPED.get(), DRAWN.get());
        }
        GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, query);
    }

    public static void timingEnd() {
        GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
        queryPending = true;
    }
}
