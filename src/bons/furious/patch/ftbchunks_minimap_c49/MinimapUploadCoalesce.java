package bons.furious.patch.ftbchunks_minimap_c49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch ftbchunks_minimap_upload_coalesce (FTB Chunks 2001.3.8, client). Ours; no FTB Chunks code is
 * carried.
 *
 * What FTB Chunks does. FTBChunksClient.renderHud rebuilds its 240 x 240 minimap texture whenever a rebuild is scheduled
 * (every chunk-border crossing, every changed map chunk, every finished region render: most frames while chunks stream
 * in). The rebuild walks a 15 x 15 chunk window and uploads each chunk as its own 16 x 16 NativeImage.upload from the
 * region's 512 x 512 image: 225 uploads of 7 GL calls each (two filter settings, four pixel-store settings and a
 * glTexSubImage2D).
 *
 * What the switch does. The tile uploads of one rebuild are collected (the loop and its region calls run as before) and
 * the window is uploaded as at most four rectangles, one per region image (each at most 15 x 15 tiles), through the same
 * NativeImage.upload with the same filter, row length (512), alignment and source offsets; the rectangle holding the
 * window's last tile leaves that tile out and the last tile is uploaded on its own at the end, exactly like stock's last
 * upload, so the GL pixel-store state after the rebuild is stock's.
 *
 * Why it is identical. The tiles of one region form one rectangle of the window whose source offsets in the region image
 * advance with the tile (checked for every tile: otherwise the rebuild's tiles are uploaded one by one as stock does), so
 * each rectangle writes exactly the texels its tiles write, from the same source pixels; tiles never overlap, so the
 * texture bytes are stock's. Region image pixels are written only by FTB Chunks' map-thread render task, which stock's
 * uploads race with in the same way (each pixel is read once either way). Anything unusual (another thread, another
 * level or size, a tile out of order) is uploaded the stock way.
 *
 * -Dbons_and_furious.ftbchunksMinimapUploadCoalesce=false switches it off at run time.
 */
public final class MinimapUploadCoalesce {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.ftbchunksMinimapUploadCoalesce", "true"));
    /** Rebuilds coalesced, tile calls collected, rectangle and single uploads issued, rebuilds uploaded tile by tile. */
    public static long rebuilds, tiles, rectangles, singles, fallbacks;
    private static volatile boolean announced;

    private static final NativeImage[] IMG = new NativeImage[4];
    private static int images;
    private static final int[] T_SLOT = new int[225], T_MX = new int[225], T_MZ = new int[225], T_SX = new int[225], T_SY = new int[225];
    private static final NativeImage[] T_IMG = new NativeImage[225];
    private static int recorded, next;
    private static boolean active, blur0;

    private MinimapUploadCoalesce() {}

    /** The rebuild loop's tile upload (renderHud's only NativeImage.upload call). */
    public static void upload(NativeImage image, int level, int x, int y, int sx, int sy, int w, int h, boolean blur,
                              boolean clamp, boolean mipmap, boolean autoClose, Operation<Void> original) {
        if (!enabled || level != 0 || w != 16 || h != 16 || clamp || mipmap || autoClose || x < 0 || y < 0 || x > 224 || y > 224
                || (x & 15) != 0 || (y & 15) != 0 || !RenderSystem.isOnRenderThreadOrInit()) {
            replay(original);
            original.call(image, level, x, y, sx, sy, w, h, blur, clamp, mipmap, autoClose);
            return;
        }
        int mx = x >> 4, mz = y >> 4, t = mz * 15 + mx;
        if (t == 0) {
            replay(original);                       // an unfinished rebuild (none in FTB Chunks' loop) is uploaded first
            clear();
            active = true;
            blur0 = blur;
        }
        if (!active || t != next || blur != blur0) {
            replay(original);
            original.call(image, level, x, y, sx, sy, w, h, blur, clamp, mipmap, autoClose);
            return;
        }
        int slot = -1;
        for (int i = 0; i < images; i++) {
            if (IMG[i] == image) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            if (images == 4) {
                replay(original);
                original.call(image, level, x, y, sx, sy, w, h, blur, clamp, mipmap, autoClose);
                return;
            }
            slot = images++;
            IMG[slot] = image;
        }
        T_SLOT[recorded] = slot;
        T_IMG[recorded] = image;
        T_MX[recorded] = mx;
        T_MZ[recorded] = mz;
        T_SX[recorded] = sx;
        T_SY[recorded] = sy;
        recorded++;
        next++;
        tiles++;
        if (t == 224) finish(original);
    }

    /** After the window's last tile: the rectangles (the last tile on its own, last), or every tile when one does not fit. */
    private static void finish(Operation<Void> original) {
        int[] minX = new int[4], maxX = new int[4], minZ = new int[4], maxZ = new int[4], count = new int[4], first = new int[4];
        java.util.Arrays.fill(minX, Integer.MAX_VALUE);
        java.util.Arrays.fill(minZ, Integer.MAX_VALUE);
        java.util.Arrays.fill(maxX, Integer.MIN_VALUE);
        java.util.Arrays.fill(maxZ, Integer.MIN_VALUE);
        java.util.Arrays.fill(first, -1);
        boolean fits = true;
        for (int i = 0; i < recorded; i++) {
            int s = T_SLOT[i];
            if (first[s] < 0) first[s] = i;
            int f = first[s];
            if (T_SX[i] - T_SX[f] != (T_MX[i] - T_MX[f]) * 16 || T_SY[i] - T_SY[f] != (T_MZ[i] - T_MZ[f]) * 16) fits = false;
            minX[s] = Math.min(minX[s], T_MX[i]);
            maxX[s] = Math.max(maxX[s], T_MX[i]);
            minZ[s] = Math.min(minZ[s], T_MZ[i]);
            maxZ[s] = Math.max(maxZ[s], T_MZ[i]);
            count[s]++;
        }
        int[] srcX = new int[4], srcY = new int[4];
        for (int s = 0; fits && s < images; s++) {
            int f = first[s];
            srcX[s] = T_SX[f] + (minX[s] - T_MX[f]) * 16;
            srcY[s] = T_SY[f] + (minZ[s] - T_MZ[f]) * 16;
            int wt = maxX[s] - minX[s] + 1, ht = maxZ[s] - minZ[s] + 1;
            if (count[s] != wt * ht || srcX[s] < 0 || srcY[s] < 0 || srcX[s] + wt * 16 > IMG[s].m_84982_() || srcY[s] + ht * 16 > IMG[s].m_85084_()) fits = false;
        }
        if (!fits) {
            fallbacks++;
            replay(original);
            return;
        }
        int last = recorded - 1;
        int lastSlot = T_SLOT[last];
        for (int s = 0; s < images; s++) {
            if (s != lastSlot) {
                rect(original, s, minX[s], minZ[s], maxX[s], maxZ[s], srcX[s], srcY[s]);
                continue;
            }
            // the rectangle holding tile (14, 14): everything above its last row, then its last row without the last tile
            if (minZ[s] <= 13) rect(original, s, minX[s], minZ[s], 14, 13, srcX[s], srcY[s]);
            if (minX[s] <= 13) rect(original, s, minX[s], 14, 13, 14, srcX[s], srcY[s] + (14 - minZ[s]) * 16);
        }
        original.call(T_IMG[last], 0, 224, 224, T_SX[last], T_SY[last], 16, 16, blur0, false, false, false);
        singles++;
        rebuilds++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: ftbchunks_minimap_upload_coalesce applies (FTB Chunks' minimap rebuild uploads at most four rectangles)");
        }
        clear();
    }

    private static void rect(Operation<Void> original, int s, int x0, int z0, int x1, int z1, int srcX, int srcY) {
        original.call(IMG[s], 0, x0 * 16, z0 * 16, srcX, srcY, (x1 - x0 + 1) * 16, (z1 - z0 + 1) * 16, blur0, false, false, false);
        rectangles++;
    }

    /** Uploads what this rebuild collected so far one tile at a time, in the order stock uploads them. */
    private static void replay(Operation<Void> original) {
        if (recorded > 0) {
            for (int i = 0; i < recorded; i++) original.call(T_IMG[i], 0, T_MX[i] * 16, T_MZ[i] * 16, T_SX[i], T_SY[i], 16, 16, blur0, false, false, false);
            fallbacks++;
        }
        clear();
    }

    private static void clear() {
        for (int i = 0; i < recorded; i++) T_IMG[i] = null;
        for (int i = 0; i < images; i++) IMG[i] = null;
        recorded = images = next = 0;
        active = false;
    }
}
