package bons.furious.patch.xaeroworldmap_c49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import xaero.map.misc.ConsistentBitArray;

/**
 * Bons and Furious switch xaeroworldmap_lazy_tile_heights (Xaero's World Map 1.47.0 for NeoForge 1.21.1, tested build;
 * client; since 1.0.49). Exact. Idea: Collections Of Optimizations (xaeroworldmap.lazyTileHeights), idea text only. Xaero's
 * World Map is All Rights Reserved: none of its code is carried; the injections only decide which height table object a
 * texture uses.
 *
 * What World Map does. Every RegionTexture (every map tile texture: MapWriter creates one for every in-range tile position
 * of a loaded region and drops it at once when nothing was written) allocates two height tables in its constructor,
 * new ConsistentBitArray(13, 4096) for heightValues and topHeightValues: 1,024 longs (8 KiB) each, all zero.
 *
 * What the switch does (WmLazyHeightsMixin). Both constructor allocations of a (13, 4096) table hand out SHARED, one all-zero
 * table, instead. Every write that can make a bit non-zero first gives the texture its own table: in putHeight(index, h) and
 * putTopHeight(index, h) (the three-argument forms call them) the field read in front of the shipped set hands over a fresh
 * ConsistentBitArray(13, 4096), assigned to the field first (synchronized on the texture, re-checked), when the field holds
 * SHARED; readCacheData's three setData calls on the two fields assign a fresh table built from the read data
 * (new ConsistentBitArray(13, 4096, data): the same object state as the shipped new-then-setData, the data being 1,024 longs
 * on those paths; any other length keeps the shipped new + setData). removeHeight / removeTopHeight run as shipped: on
 * SHARED they write 0 over 0 (set clears the entry's bits and ors the value in), which leaves it all zero and throws exactly
 * as on a texture's own zero table. Reads (getHeight, getTopHeight, writeCacheMapData's getData, LeafRegionTexture.resetHeights'
 * arraycopy of zeros) need no change.
 * Why identical: SHARED never receives a non-zero bit (every reader and writer of the two fields is in RegionTexture or
 * LeafRegionTexture, constant-pool scan of the NeoForge 1.21.1 1.47.0 jar; ConsistentBitArray.setData and getData are
 * called from those two classes only: the guard lines pin each of them), so every read of a table no one wrote returns what
 * the shipped fresh zero table returns, and every write lands in the texture's own table as before.
 *
 * -Dbons_and_furious.xaeroworldmapLazyTileHeights=false allocates both tables again (emergency off).
 * -Dbons_and_furious.xaeroworldmapLazyTileHeights.shadow=true (verification runs only): the tables are handed out as above
 * and SHADOW_CHECKS counts privatisations; SHADOW_MISMATCHES counts the times SHARED was found holding a non-zero bit (checked
 * at every privatisation; WARN, at most 20).
 */
public final class LazyHeights {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.xaeroworldmapLazyTileHeights", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.xaeroworldmapLazyTileHeights.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Tables handed out shared, and tables a texture had to make its own. */
    public static final AtomicLong SHARED_HANDED = new AtomicLong(), PRIVATISED = new AtomicLong();
    public static final ConsistentBitArray SHARED = new ConsistentBitArray(13, 4096);
    private static volatile boolean announced;

    private LazyHeights() {
    }

    /** RegionTexture.<init>: its two new ConsistentBitArray(13, 4096). */
    public static ConsistentBitArray create(int bits, int entries, Operation<ConsistentBitArray> original) {
        if (!enabled || bits != 13 || entries != 4096) return original.call(bits, entries);
        if (!announced) announce();
        SHARED_HANDED.incrementAndGet();
        return SHARED;
    }

    /** A texture's own fresh table (called under the texture's monitor). */
    public static ConsistentBitArray fresh() {
        PRIVATISED.incrementAndGet();
        if (SHADOW) check();
        return new ConsistentBitArray(13, 4096);
    }

    /** A texture's own table holding data read from the cache (called under the texture's monitor). */
    public static ConsistentBitArray fresh(long[] data) {
        PRIVATISED.incrementAndGet();
        if (SHADOW) check();
        if (data != null && data.length == 1024) return new ConsistentBitArray(13, 4096, data);
        ConsistentBitArray own = new ConsistentBitArray(13, 4096);
        own.setData(data);
        return own;
    }

    private static void check() {
        SHADOW_CHECKS.incrementAndGet();
        long[] d = SHARED.getData();
        for (long v : d) {
            if (v != 0L) {
                if (SHADOW_MISMATCHES.incrementAndGet() <= 20)
                    LogManager.getLogger("Bons and Furious").warn("Bons and Furious: xaeroworldmap_lazy_tile_heights shadow mismatch: the shared zero height table holds a non-zero value");
                return;
            }
        }
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LogManager.getLogger("Bons and Furious").info("Bons and Furious: xaeroworldmap_lazy_tile_heights applies (World Map tile textures share one empty height table until they get a height)"
                + (SHADOW ? " [shadow mode: the shared table is checked at every hand-over]" : ""));
    }
}
