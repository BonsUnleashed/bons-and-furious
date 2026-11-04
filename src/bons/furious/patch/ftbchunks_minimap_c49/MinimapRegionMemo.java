package bons.furious.patch.ftbchunks_minimap_c49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.ftb.mods.ftbchunks.client.map.MapDimension;
import dev.ftb.mods.ftbchunks.client.map.MapRegion;
import dev.ftb.mods.ftblibrary.math.XZ;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch ftbchunks_minimap_region_memo (FTB Chunks 2001.3.8, client; exact variant). Ours; no FTB Chunks
 * code is carried.
 *
 * What FTB Chunks does. Each minimap rebuild (FTBChunksClient.renderHud, 15 x 15 chunk tiles) calls
 * MapDimension.getRegion(XZ) and MapRegion.getRenderedMapImage() for every tile: 450 synchronized blocks on the map
 * manager's lock per rebuild, although the window covers at most four regions. FTB Chunks' map thread holds the same lock
 * once per pixel while it renders a region image (262,144 times per region), so each of those 450 entries can wait.
 *
 * What the switch does. Within one rebuild loop (from the loop's start, renderHud's write of updateMinimapScheduled, to
 * its end, the write of currentPlayerChunkX), the first getRegion of each region and the first getRenderedMapImage of each
 * region run as before; the other tiles of that region get the same MapRegion and NativeImage objects back. At most four
 * regions are kept, and only for the duration of the loop.
 *
 * Exact variant. Same objects, same region creation order, same render submissions: getRegion returns the region its map
 * holds for that position (it never replaces one), and once a region's image exists and its render is submitted, the later
 * calls of the same loop only re-check two flags. The one difference: if FTB Chunks' map thread changes those flags of a
 * region while the loop runs (region.update() from a chunk update task, or a render finishing with another update
 * pending), stock may submit that region's re-render at a later tile of the same loop; with the switch it is submitted at
 * the next rebuild, one frame later, which that update or render completion schedules itself (stock produces the same
 * order with other thread timings).
 *
 * -Dbons_and_furious.ftbchunksMinimapRegionMemo=false switches it off at run time.
 */
public final class MinimapRegionMemo {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.ftbchunksMinimapRegionMemo", "true"));
    /** Calls answered from the memo (regions, images) and loops served (harness and rig counters). */
    public static long regionHits, imageHits, loops;
    private static volatile boolean announced;

    private static final int[] RX = new int[4], RZ = new int[4];
    private static final MapDimension[] DIM = new MapDimension[4];
    private static final MapRegion[] REGION = new MapRegion[4];
    private static final NativeImage[] IMAGE = new NativeImage[4];
    private static int n;
    private static boolean active;

    private MinimapRegionMemo() {}

    public static void loopStart() {
        clear();
        active = enabled && RenderSystem.isOnRenderThread();
        if (active) loops++;
    }

    public static void loopEnd() {
        clear();
        active = false;
    }

    private static void clear() {
        for (int i = 0; i < n; i++) {
            DIM[i] = null;
            REGION[i] = null;
            IMAGE[i] = null;
        }
        n = 0;
    }

    public static MapRegion region(MapDimension dim, XZ pos, Operation<MapRegion> original) {
        if (!active) return original.call(dim, pos);
        int x = pos.x(), z = pos.z();
        for (int i = 0; i < n; i++) {
            if (RX[i] == x && RZ[i] == z && DIM[i] == dim) {
                regionHits++;
                return REGION[i];
            }
        }
        MapRegion r = original.call(dim, pos);
        if (n < 4 && r != null) {
            RX[n] = x;
            RZ[n] = z;
            DIM[n] = dim;
            REGION[n] = r;
            IMAGE[n] = null;
            n++;
        }
        return r;
    }

    public static NativeImage image(MapRegion region, Operation<NativeImage> original) {
        if (!active) return original.call(region);
        for (int i = 0; i < n; i++) {
            if (REGION[i] == region) {
                NativeImage im = IMAGE[i];
                if (im != null) {
                    imageHits++;
                    if (!announced) {
                        announced = true;
                        LOGGER.info("Bons and Furious: ftbchunks_minimap_region_memo applies (FTB Chunks' minimap rebuild looks each region up once)");
                    }
                    return im;
                }
                im = original.call(region);
                IMAGE[i] = im;
                return im;
            }
        }
        return original.call(region);
    }
}
