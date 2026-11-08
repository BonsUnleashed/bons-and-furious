package bons.furious.patch.ftbchunks_minimap_c49;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.ftb.mods.ftbchunks.FTBChunksWorldConfig;
import dev.ftb.mods.ftbchunks.client.FTBChunksClientConfig;
import dev.ftb.mods.ftbchunks.client.gui.ChunkScreen;
import dev.ftb.mods.ftblibrary.ui.ScreenWrapper;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch ftbchunks_hidden_minimap_upload_defer (FTB Chunks 2101.1.23, client; exact variant). Ours; no
 * FTB Chunks code is carried.
 *
 * What FTB Chunks does. FTBChunksClient.renderHud rebuilds the minimap texture (updateMinimapIfNeeded) BEFORE it checks
 * whether the minimap is shown (hidden GUI, debug screen, minimap disabled, visibility 0, or the server's minimap rule). FTB
 * Chunks switches its own minimap off by default when Xaero's Minimap, JourneyMap, VoxelMap or Antique Atlas is installed,
 * the common "FTB for claims, another mod for maps" setup, which then pays every rebuild's uploads (FTB Chunks 2101 sends
 * the 15 x 15 chunk window as at most four rectangles, one per map region) for a texture nobody looks at.
 *
 * What the switch does. At the start of each rebuild it evaluates the same hidden condition (plus: FTB Chunks' claim
 * screen, which draws the same texture, counts as shown). While hidden, the rebuild runs as before (every region call,
 * every render submission) but its uploads are kept as a list instead of being sent, and the GL pixel-store state is left
 * as the last upload would have left it. The texture is brought up to date from that list at the moment anything can see
 * it: the first frame the minimap is drawn (right after renderHud's own visibility check, before it draws), a call of
 * getMinimapTextureId() (the claim screen's panel and any other reader), or just before FTB Chunks releases one of the
 * listed region images. A shown rebuild uploads the whole window as before and drops the list. Those late uploads restore
 * every GL state they touch (bound texture, the texture's filters, the four unpack settings).
 *
 * Exact variant. The texture's bytes when it is seen are the ones stock's last rebuild uploaded, because a region image's
 * pixels change only while FTB Chunks' map thread renders it, and every finished render schedules a rebuild (a shown
 * frame with a scheduled rebuild uploads the window in its rebuild, before the texture is drawn). The one difference:
 * pixels a region render wrote after the last rebuild (a render still running, or one that finished while its scheduled
 * rebuild has not run yet) show up when the texture is brought up to date outside a rebuild (a getMinimapTextureId reader,
 * a release), up to one rebuild earlier than with stock; both are the same again at the next rebuild. Stock produces that
 * timing too: a change from the map thread reaches its texture in this rebuild or the next depending on thread timing, so
 * the early pixels are the texture stock uploads when the render finishes just before its last rebuild.
 *
 * -Dbons_and_furious.ftbchunksHiddenMinimapUploadDefer=false switches it off at run time.
 */
public final class MinimapHiddenDefer {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.ftbchunksHiddenMinimapUploadDefer", "true"));
    /** Hidden rebuilds, uploads kept back, flushes (visible frame / reader / release) and uploads sent by them. */
    public static long hiddenRebuilds, keptUploads, visibleFlushes, readerFlushes, releaseFlushes, flushedUploads, flushRefused;
    private static volatile boolean announced;

    private static final int CAP = 225;
    private static final NativeImage[] E_IMG = new NativeImage[CAP];
    private static final int[] E_X = new int[CAP], E_Y = new int[CAP], E_SX = new int[CAP], E_SY = new int[CAP], E_W = new int[CAP], E_H = new int[CAP];
    private static final boolean[] E_BLUR = new boolean[CAP];
    private static int entries;
    private static boolean skipping, stale;

    private MinimapHiddenDefer() {}

    /** Stock's hidden condition of renderHud (its skip of the drawing), except that the claim screen counts as shown. */
    static boolean hiddenNow() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ScreenWrapper wrapper && wrapper.getGui() instanceof ChunkScreen) return false;
        return mc.options.hideGui || mc.getDebugOverlay().showDebugScreen() || !(Boolean) FTBChunksClientConfig.MINIMAP_ENABLED.get()
                || (Integer) FTBChunksClientConfig.MINIMAP_VISIBILITY.get() == 0 || !FTBChunksWorldConfig.shouldShowMinimap(mc.player);
    }

    /** updateMinimapIfNeeded, start of the rebuild (the write of updateMinimapScheduled). */
    public static void loopStart() {
        skipping = enabled && RenderSystem.isOnRenderThread() && hiddenNow();
        if (skipping) clearEntries();
    }

    /** The rebuild's rectangle upload: false = kept back. */
    public static boolean upload(NativeImage image, int level, int x, int y, int sx, int sy, int w, int h, boolean blur,
                                 boolean clamp, boolean mipmap, boolean autoClose) {
        if (!skipping) return true;
        if (level != 0 || clamp || mipmap || autoClose || entries == CAP) return true;
        E_IMG[entries] = image;
        E_X[entries] = x;
        E_Y[entries] = y;
        E_SX[entries] = sx;
        E_SY[entries] = sy;
        E_W[entries] = w;
        E_H[entries] = h;
        E_BLUR[entries] = blur;
        entries++;
        keptUploads++;
        stale = true;
        return false;
    }

    /** updateMinimapIfNeeded, end of the rebuild (the write of currentPlayerChunkX); textureId = renderHud's minimap texture. */
    public static void loopEnd(int textureId) {
        if (skipping) {
            skipping = false;
            hiddenRebuilds++;
            lastTextureId = textureId;
            if (entries > 0) {
                // the GL unpack state the last kept upload would have left (its filter equals the one renderHud just set)
                int i = entries - 1;
                NativeImage img = E_IMG[i];
                GlStateManager._pixelStore(3314, E_W[i] == img.getWidth() ? 0 : img.getWidth());
                GlStateManager._pixelStore(3316, E_SX[i]);
                GlStateManager._pixelStore(3315, E_SY[i]);
                GlStateManager._pixelStore(3317, img.format().components());
            }
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: ftbchunks_hidden_minimap_upload_defer applies (FTB Chunks' hidden minimap texture is updated only when shown)");
            }
            return;
        }
        if (stale) {                // a shown rebuild uploaded the whole window: nothing is kept back any more
            stale = false;
            clearEntries();
        }
    }

    /** renderHud, right after its visibility check: the minimap is about to be drawn this frame. */
    public static void visibleFrame(int textureId) {
        if (stale && flush(textureId, null)) visibleFlushes++;
    }

    /** getMinimapTextureId(): someone is about to read the texture. */
    public static void reader(int textureId) {
        if (stale && flush(textureId, null)) readerFlushes++;
    }

    private static int lastTextureId = -1;

    /** MapRegion.releaseMapImage(), before the image is closed: its kept uploads go out first. */
    public static void released(NativeImage image) {
        if (!stale || image == null) return;
        boolean listed = false;
        for (int i = 0; i < entries; i++) listed |= E_IMG[i] == image;
        if (listed && flush(lastTextureId, image)) releaseFlushes++;
    }

    /** Sends the kept uploads (all, or those of one image) into the texture, leaving every GL state it touches as it was. */
    private static boolean flush(int textureId, NativeImage only) {
        if (textureId < 0 || !RenderSystem.isOnRenderThread()) return false;
        if (GlStateManager._getInteger(0x88EF) != 0) {          // GL_PIXEL_UNPACK_BUFFER_BINDING: someone else's upload source
            flushRefused++;
            return false;
        }
        int previous = GlStateManager._getInteger(0x8069);       // GL_TEXTURE_BINDING_2D
        int rowLength = GlStateManager._getInteger(3314), skipPixels = GlStateManager._getInteger(3316);
        int skipRows = GlStateManager._getInteger(3315), alignment = GlStateManager._getInteger(3317);
        GlStateManager._bindTexture(textureId);
        int minFilter = org.lwjgl.opengl.GL11.glGetTexParameteri(3553, 10241), magFilter = org.lwjgl.opengl.GL11.glGetTexParameteri(3553, 10240);
        int kept = 0;
        for (int i = 0; i < entries; i++) {
            NativeImage img = E_IMG[i];
            if (only != null && img != only) {
                E_IMG[kept] = img;
                E_X[kept] = E_X[i];
                E_Y[kept] = E_Y[i];
                E_SX[kept] = E_SX[i];
                E_SY[kept] = E_SY[i];
                E_W[kept] = E_W[i];
                E_H[kept] = E_H[i];
                E_BLUR[kept] = E_BLUR[i];
                kept++;
                continue;
            }
            try {
                img.upload(0, E_X[i], E_Y[i], E_SX[i], E_SY[i], E_W[i], E_H[i], E_BLUR[i], false, false, false);
                flushedUploads++;
            } catch (IllegalStateException closed) {
                // an image closed without FTB Chunks' release (none in 2101.1.23): its rectangle stays as it is
            }
        }
        for (int i = kept; i < entries; i++) E_IMG[i] = null;
        entries = kept;
        GlStateManager._texParameter(3553, 10241, minFilter);
        GlStateManager._texParameter(3553, 10240, magFilter);
        GlStateManager._pixelStore(3314, rowLength);
        GlStateManager._pixelStore(3316, skipPixels);
        GlStateManager._pixelStore(3315, skipRows);
        GlStateManager._pixelStore(3317, alignment);
        GlStateManager._bindTexture(previous);
        if (entries == 0) stale = false;
        return true;
    }

    private static void clearEntries() {
        for (int i = 0; i < entries; i++) E_IMG[i] = null;
        entries = 0;
    }

    /** Harness only: start like a fresh game. */
    public static void reset() {
        clearEntries();
        skipping = stale = false;
        lastTextureId = -1;
    }

    public static boolean stale() {
        return stale;
    }
}
