package bons.furious.patch.immersive_overlays;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.slf4j.Logger;

/**
 * Bons and Furious switch immersiveaircraft_hud_blit_batch (Immersive Aircraft, GPL-3.0-only; 1.21.1 tested build
 * 1.7.0+1.21.1; client). Mojang member names.
 *
 * What Immersive Aircraft does. While the player rides one of its vehicles, OverlayRenderer.renderAircraftHealth draws the
 * vehicle's health as ten hearts with up to three GuiGraphics.blit calls each (background, full, half), all from one
 * texture, with nothing else in between. In 1.21.1 every blit is its own draw: GuiGraphics.blit(RL, x, y, u, v, w, h, tw,
 * th) -> blit(RL, x, y, w, h, u, v, w, h, tw, th) -> blit(RL, x1, x2, y1, y2, z, uW, vH, u, v, tw, th) -> innerBlit, which
 * sets shader texture 0 and the position-texture shader, begins a QUADS / POSITION_TEX BufferBuilder on the Tesselator's
 * buffer, writes four vertices and uploads and draws them (BufferUploader.drawWithShader(buildOrThrow())). Up to 30 draw
 * calls per frame for one texture.
 *
 * What the switch does. Inside renderAircraftHealth only, each blit's quad is written into one open QUADS / POSITION_TEX
 * builder instead: the same four addVertex(pose, x, y, z).setUv(u, v) calls with the same numbers (x2 = x + w, y2 = y + h,
 * z = 0, u0 = (u + 0) / tw, u1 = (u + w) / tw, v0 = (v + 0) / th, v1 = (v + h) / th, evaluated in the same float order as
 * GuiGraphics.blit, the pose matrix read at each blit), in the same order. When the method ends (also on an exception), and
 * before a blit of another texture, the builder is drawn once with the state each stock blit would have set: shader texture
 * 0 = that texture, shader = GameRenderer.getPositionTexShader. Nothing else changes RenderSystem or GL state between those
 * blits, so one draw of N quads rasterises like N draws of one (primitives are processed in order, with the same depth test
 * and blending). Fewer draws, identical pixels.
 *
 * Stands aside (stock blits) for that call when: the runtime switch is off; ImmediatelyFast is HUD batching this
 * GuiGraphics (its buffer source is ImmediatelyFast's HudBatchingBufferSource, the condition its own innerBlit hook acts on);
 * Vivecraft's VR is running (its innerBlit hook toggled blending per blit on 1.20.1); the call is not on the render thread.
 *
 * Ported to 1.21.1: a BufferBuilder is a new object per Tesselator.begin (there is no shared builder whose building state
 * could be asked), so the batch begins its builder at the first blit exactly as innerBlit does and nothing else uses the
 * Tesselator until it is drawn (every blit of the method is wrapped). ImmediatelyFast 1.6 for 1.21 answers its old public
 * batching API with false (and logs an error), so the HUD batching test reads the GuiGraphics' buffer source instead.
 *
 * -Dbons_and_furious.immersiveaircraftHudBlitBatch=false switches it off at run time.
 * -Dbons_and_furious.immersiveaircraftHudBlitBatch.shadow=true (verification runs only): the stock blits run; SHADOW_CHECKS
 * counts the blits the switch would have batched and SHADOW_DRAWS_SAVED the draw calls it would have saved.
 */
public final class HudQuadBatch {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.immersiveaircraftHudBlitBatch", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.immersiveaircraftHudBlitBatch.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong(), SHADOW_DRAWS_SAVED = new AtomicLong();
    /** Draw calls issued by batches and blits they replaced (counted always; read by the in-game check). */
    public static final AtomicLong DRAWS = new AtomicLong(), BLITS = new AtomicLong();
    private static final Class<?> IF_HUD_SOURCE = immediatelyFastHudSource();
    private static final MethodHandle VR_RUNNING = vivecraftVrRunning();
    private static volatile boolean announced;

    /** Render thread only: the GuiGraphics whose renderAircraftHealth call is batching (null = not batching). */
    private static GuiGraphics open;
    private static BufferBuilder builder;
    private static ResourceLocation texture;
    private static int quads;
    private static int shadowBlits;
    private static ResourceLocation shadowTexture;

    private HudQuadBatch() {
    }

    /** renderAircraftHealth starts: whether its blits may be batched (the caller must call end(g) when this returns true). */
    public static boolean begin(GuiGraphics g) {
        if (!enabled || open != null || !RenderSystem.isOnRenderThread()) return false;
        if (immediatelyFastBatching(g) || vrRunning()) return false;
        if (SHADOW) {
            shadowBlits = 0;
            shadowTexture = null;
            open = g;
            return true;
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: immersiveaircraft_hud_blit_batch applies (Immersive Aircraft's vehicle health hearts are drawn in one call per texture instead of one per heart)");
        }
        open = g;
        builder = null;
        texture = null;
        quads = 0;
        return true;
    }

    /**
     * One blit of renderAircraftHealth: false = the caller makes the stock blit. Writes the quad GuiGraphics.innerBlit would
     * write, into the open builder.
     */
    public static boolean blit(GuiGraphics g, ResourceLocation atlas, int x, int y, float u, float v, int w, int h, int texW, int texH) {
        if (open != g) return false;
        if (SHADOW) {
            SHADOW_CHECKS.incrementAndGet();
            if (shadowTexture != null && !shadowTexture.equals(atlas)) shadowBlits = 0;
            shadowTexture = atlas;
            if (++shadowBlits > 1) SHADOW_DRAWS_SAVED.incrementAndGet();
            return false;
        }
        if (texture != null && !texture.equals(atlas)) flush();
        if (builder == null) {
            builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            texture = atlas;
        }
        // GuiGraphics.blit -> blit(atlas, x, y, w, h, u, v, w, h, texW, texH) -> blit(atlas, x, x + w, y, y + h,
        // 0, w, h, u, v, texW, texH) -> innerBlit(atlas, x1, x2, y1, y2, z, u0, u1, v0, v1)
        int x1 = x, x2 = x + w, y1 = y, y2 = y + h, z = 0;
        float u0 = (u + 0.0F) / (float) texW, u1 = (u + (float) w) / (float) texW;
        float v0 = (v + 0.0F) / (float) texH, v1 = (v + (float) h) / (float) texH;
        Matrix4f pose = g.pose().last().pose();
        BufferBuilder bb = builder;
        bb.addVertex(pose, (float) x1, (float) y1, (float) z).setUv(u0, v0);
        bb.addVertex(pose, (float) x1, (float) y2, (float) z).setUv(u0, v1);
        bb.addVertex(pose, (float) x2, (float) y2, (float) z).setUv(u1, v1);
        bb.addVertex(pose, (float) x2, (float) y1, (float) z).setUv(u1, v0);
        quads++;
        BLITS.incrementAndGet();
        return true;
    }

    /** renderAircraftHealth ends (normally or by an exception): draw what is batched. */
    public static void end(GuiGraphics g) {
        if (open != g) return;
        try {
            if (!SHADOW) flush();
        } finally {
            open = null;
            builder = null;
            texture = null;
            quads = 0;
        }
    }

    /** Draws the open builder with the state every stock blit of it set (shader texture 0 = its texture, position-tex shader). */
    private static void flush() {
        BufferBuilder bb = builder;
        ResourceLocation atlas = texture;
        builder = null;
        texture = null;
        if (bb == null) return;
        int n = quads;
        quads = 0;
        RenderSystem.setShaderTexture(0, atlas);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        MeshData mesh = bb.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
            DRAWS.incrementAndGet();
        } else if (n != 0) {
            LOGGER.debug("Bons and Furious: immersiveaircraft_hud_blit_batch built an empty batch of {} quads", n);
        }
    }

    private static boolean immediatelyFastBatching(GuiGraphics g) {
        if (IF_HUD_SOURCE == null) return false;
        try {
            return IF_HUD_SOURCE.isInstance(g.bufferSource());
        } catch (Throwable t) {
            return true;   // cannot tell: leave the blits to ImmediatelyFast's hook
        }
    }

    private static boolean vrRunning() {
        if (VR_RUNNING == null) return false;
        try {
            return (boolean) VR_RUNNING.invokeExact();
        } catch (Throwable t) {
            return true;
        }
    }

    /**
     * ImmediatelyFast's HUD batching buffer source class (its innerBlit hook acts while a GuiGraphics' bufferSource is one);
     * null without ImmediatelyFast.
     */
    private static Class<?> immediatelyFastHudSource() {
        try {
            return Class.forName("net.raphimc.immediatelyfast.feature.batching.HudBatchingBufferSource", false, HudQuadBatch.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            return null;
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: immersiveaircraft_hud_blit_batch cannot tell whether ImmediatelyFast batches the HUD ({}); the blits are left as shipped", t.toString());
            return Object.class;
        }
    }

    /** Vivecraft's VRState.VR_RUNNING (public static boolean); null without Vivecraft. */
    private static MethodHandle vivecraftVrRunning() {
        try {
            Class<?> state = Class.forName("org.vivecraft.client_vr.VRState", false, HudQuadBatch.class.getClassLoader());
            return MethodHandles.publicLookup().findStaticGetter(state, "VR_RUNNING", boolean.class);
        } catch (ClassNotFoundException e) {
            return null;
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: immersiveaircraft_hud_blit_batch cannot read Vivecraft's VR state ({}); the blits are left as shipped", t.toString());
            return MethodHandles.constant(boolean.class, true);
        }
    }
}
