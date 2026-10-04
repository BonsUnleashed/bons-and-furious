package bons.furious.patch.embeddium_draw;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import me.jellysquid.mods.sodium.client.gl.device.MultiDrawBatch;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import me.jellysquid.mods.sodium.client.render.viewport.CameraTransform;
import me.jellysquid.mods.sodium.client.util.BitwiseMath;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch embeddium_draw_batch_cache (Embeddium 0.3.31+mc1.20.1; client only). Helper of
 * bons.furious.mixin.embeddium_draw.DefaultChunkRendererBatchCacheMixin, SectionRenderDataStorageVersionMixin and
 * ChunkRenderListAccessMixin.
 *
 * DefaultChunkRenderer.render fills one multi-draw command buffer (MultiDrawBatch) per region and terrain pass, every
 * frame and (with Oculus shadows) in both passes: fillCommandBuffer walks the region's list of sections with geometry
 * and, per section, writes one (base vertex, element count, index offset) command per mesh facing from the section's
 * render data (SectionRenderDataStorage), keeping the facings that face the camera when block-face culling applies.
 * 2.0% of the render thread in the 1.0.26 client recording (still camera), 1.6% of it in the shadow pass.
 *
 * The filled commands are a pure function of: the storage's render data (changed only by SectionRenderDataStorage's
 * setMeshes, removeMeshes, removeIndexBuffer, replaceIndexBuffer, onBufferResized and delete, each of which bumps the
 * storage's version), the list's section indices with geometry in order, the pass's reverse and sorted flags, the
 * face-culling flag the renderer passes in, the camera's integer block position relative to the sections of the region
 * (only when face culling applies, captured as the exact comparison bits Embeddium's getVisibleFaces makes for every
 * section coordinate of the region), and whether embeddium_merged_draws merged the commands. Each storage keeps the
 * commands of its last two fills (Oculus's shadow pass and the main view) with all of these inputs; a fill whose inputs
 * are all equal copies the remembered commands into the batch instead (same count, same bytes in the drawn range).
 * Bytes beyond the batch's size are never read (glMultiDrawElementsBaseVertex and getIndexBufferSize stop at size).
 */
public final class DrawBatchCache {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.embeddiumDrawBatchCache=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.embeddiumDrawBatchCache", "true"));
    /**
     * Rig self-check: -Dbons_and_furious.embeddiumDrawBatchCache.shadow=true refills every batch as shipped and, whenever
     * the cache would have answered, compares the remembered commands with the refilled ones byte for byte.
     */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.embeddiumDrawBatchCache.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Rig counters (render thread only, plain fields): fills answered from the cache, fills computed. */
    public static long hits, misses;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final sun.misc.Unsafe U;
    private static final long INT_BASE, LONG_BASE;
    private static final int POINTER_SIZE;
    private static volatile boolean announced;

    static {
        sun.misc.Unsafe u = null;
        try {
            Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            u = (sun.misc.Unsafe) f.get(null);
        } catch (Throwable t) {
            enabled = false;
            LOGGER.warn("Bons and Furious: embeddium_draw_batch_cache is inactive: no memory access ({})", t.toString());
        }
        U = u;
        INT_BASE = u == null ? 0 : u.arrayBaseOffset(int[].class);
        LONG_BASE = u == null ? 0 : u.arrayBaseOffset(long[].class);
        POINTER_SIZE = org.lwjgl.system.Pointer.POINTER_SIZE;
        if (POINTER_SIZE != 8) {
            enabled = false;
            LOGGER.warn("Bons and Furious: embeddium_draw_batch_cache is inactive: {}-byte pointers", POINTER_SIZE);
        }
    }

    private DrawBatchCache() {
    }

    static void announce() {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: embeddium_draw_batch_cache: a region's draw commands are reused while its render data, section list, pass and "
                    + "camera-facing faces are unchanged{}", SHADOW ? " (SHADOW mode: every batch is refilled; the cache is only compared)" : "");
        }
    }

    /**
     * The comparison bits Embeddium's getVisibleFaces computes for every section coordinate of the region: for each
     * x (8), y (4) and z (8) the "greater than min - 3" and "less than max + 3" tests of the camera's integer position.
     * Two cameras with equal bits give every section of the region the same visible-face mask.
     */
    public static long faceBits(RenderRegion region, CameraTransform camera) {
        long bits = 0L;
        int bit = 0;
        int cx = camera.intX, cy = camera.intY, cz = camera.intZ;
        int ox = region.getChunkX(), oy = region.getChunkY(), oz = region.getChunkZ();
        for (int i = 0; i < RenderRegion.REGION_WIDTH; i++) {
            int min = ox + i << 4;
            bits |= (long) BitwiseMath.greaterThan(cx, min - 3) << bit++;
            bits |= (long) BitwiseMath.lessThan(cx, min + 16 + 3) << bit++;
        }
        for (int i = 0; i < RenderRegion.REGION_HEIGHT; i++) {
            int min = oy + i << 4;
            bits |= (long) BitwiseMath.greaterThan(cy, min - 3) << bit++;
            bits |= (long) BitwiseMath.lessThan(cy, min + 16 + 3) << bit++;
        }
        for (int i = 0; i < RenderRegion.REGION_LENGTH; i++) {
            int min = oz + i << 4;
            bits |= (long) BitwiseMath.greaterThan(cz, min - 3) << bit++;
            bits |= (long) BitwiseMath.lessThan(cz, min + 16 + 3) << bit++;
        }
        return bits;
    }

    /** Per SectionRenderDataStorage: its render-data version and the commands of its last two fills. */
    public static final class Slots {
        public int version;
        final Entry a = new Entry(), b = new Entry();
        long uses;

        /** The entry whose inputs equal these, or null. */
        public Entry find(int listCount, byte[] list, boolean reverse, boolean sorted, boolean culling, boolean merged, long faceBits) {
            if (this.a.same(this.version, listCount, list, reverse, sorted, culling, merged, faceBits)) return this.a.touch(++this.uses);
            if (this.b.same(this.version, listCount, list, reverse, sorted, culling, merged, faceBits)) return this.b.touch(++this.uses);
            return null;
        }

        /** The entry to overwrite with a fresh fill (the least recently used one). */
        public Entry victim() {
            return (this.a.lastUse <= this.b.lastUse ? this.a : this.b).touch(++this.uses);
        }
    }

    /** One remembered fill: its inputs and the commands in [0, size). */
    public static final class Entry {
        boolean valid;
        int version, listCount;
        final byte[] list = new byte[RenderRegion.REGION_SIZE];
        boolean reverse, sorted, culling, merged;
        long faceBits;
        int size, sourceSize;
        int[] baseVertex = new int[16], elementCount = new int[16];
        long[] elementPointer = new long[16];
        long lastUse;

        Entry touch(long stamp) {
            this.lastUse = stamp;
            return this;
        }

        boolean same(int version, int listCount, byte[] list, boolean reverse, boolean sorted, boolean culling, boolean merged, long faceBits) {
            return this.valid && this.version == version && this.listCount == listCount && this.reverse == reverse && this.sorted == sorted
                    && this.culling == culling && this.merged == merged && this.faceBits == faceBits
                    && Arrays.equals(this.list, 0, listCount, list, 0, listCount);
        }

        /**
         * Copies the batch's commands [0, size) and the inputs they were filled from; sourceSize = the fill's command count
         * before embeddium_merged_draws merged it (rig counts only).
         */
        public void store(MultiDrawBatch batch, int version, int listCount, byte[] list, boolean reverse, boolean sorted, boolean culling, boolean merged,
                          long faceBits, int sourceSize) {
            int n = batch.size;
            if (n > this.baseVertex.length) {
                int cap = Math.max(n, this.baseVertex.length * 2);
                this.baseVertex = new int[cap];
                this.elementCount = new int[cap];
                this.elementPointer = new long[cap];
            }
            if (n > 0) {
                U.copyMemory(null, batch.pBaseVertex, this.baseVertex, INT_BASE, (long) n << 2);
                U.copyMemory(null, batch.pElementCount, this.elementCount, INT_BASE, (long) n << 2);
                U.copyMemory(null, batch.pElementPointer, this.elementPointer, LONG_BASE, (long) n << 3);
            }
            this.size = n;
            this.sourceSize = sourceSize;
            this.version = version;
            this.listCount = listCount;
            System.arraycopy(list, 0, this.list, 0, listCount);
            this.reverse = reverse;
            this.sorted = sorted;
            this.culling = culling;
            this.merged = merged;
            this.faceBits = faceBits;
            this.valid = true;
        }

        /** Writes the remembered commands into the batch: batch.size and [0, size) of its three arrays. */
        public void load(MultiDrawBatch batch) {
            int n = this.size;
            if (n > 0) {
                U.copyMemory(this.baseVertex, INT_BASE, null, batch.pBaseVertex, (long) n << 2);
                U.copyMemory(this.elementCount, INT_BASE, null, batch.pElementCount, (long) n << 2);
                U.copyMemory(this.elementPointer, LONG_BASE, null, batch.pElementPointer, (long) n << 3);
            }
            batch.size = n;
        }

        /** SHADOW mode: are the batch's commands [0, size) byte for byte the remembered ones? */
        public boolean matches(MultiDrawBatch batch) {
            int n = batch.size;
            if (n != this.size) return false;
            for (int i = 0; i < n; i++) {
                if (U.getInt(batch.pBaseVertex + ((long) i << 2)) != this.baseVertex[i]
                        || U.getInt(batch.pElementCount + ((long) i << 2)) != this.elementCount[i]
                        || U.getLong(batch.pElementPointer + ((long) i << 3)) != this.elementPointer[i]) return false;
            }
            return true;
        }

        public int size() {
            return this.size;
        }

        /** The number of commands the fill produced (before merging, for a merged entry). */
        public int sourceSize() {
            return this.sourceSize;
        }
    }

    public static void mismatch(String what) {
        if (SHADOW_MISMATCHES.incrementAndGet() <= 20) LOGGER.warn("Bons and Furious: embeddium_draw_batch_cache SHADOW mismatch: {}", what);
    }

    /** Host interface the storage mixin adds to SectionRenderDataStorage. */
    public interface Host {
        Slots bons$drawSlots();
    }

    public static void hit() {
        hits++;
        if (!announced) announce();
    }

    public static void miss() {
        misses++;
    }
}
