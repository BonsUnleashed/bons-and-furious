package bons.furious.patch.justdirethings_coo49;

import com.direwolf20.justdirethings.common.blockentities.basebe.AreaAffectingBE;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switches justdirethings_area_preview_no_copy and justdirethings_area_preview_chunk_memo (since 1.0.49;
 * Just Dire Things [Forge] 1.1.11; client side). Helper of AreaPreviewNoCopyMixin and AreaPreviewChunkMemoMixin; no Just Dire
 * Things code is carried. SRG names.
 *
 * What the mod does. RenderLevelLast.renderAreaPreviews runs on every frame for every player: for each of the 13 x 13
 * chunks around the player it takes the loaded chunk (getChunkNow), copies its block entity map's values into a new
 * ArrayList and walks the copy; every AreaAffectingBE whose data has renderArea set gets its area drawn (lines and a
 * translucent box, twice when it has an offset area).
 *
 * justdirethings_area_preview_no_copy (exact): the walk iterates the map's values directly instead of a copy. The loop
 * body never adds or removes a block entity (it reads the block entity's data and position and writes vertices; checked
 * in the mod's bytecode), and a client chunk's block entity map changes only on the render thread, which is running this
 * loop, so the live values are visited in the order and with the elements the copy would have had.
 *
 * justdirethings_area_preview_chunk_memo (trade-off, off by default): per chunk the switch remembers for one second
 * whether the chunk holds an AreaAffectingBE whose area preview is switched on; while it holds none, the chunk's block
 * entities are not walked at all until the second is over. Difference: an area preview switched on, or a newly placed
 * block with its preview on, can appear up to one second later. A chunk that holds a shown preview is walked every frame
 * (switching a preview off shows at once).
 *
 * -Dbons_and_furious.justdirethingsAreaPreviewNoCopy=false / justdirethingsAreaPreviewChunkMemo=false: the original.
 * SHADOW MODE for the copy-free walk (verification only): -Dbons_and_furious.justdirethingsAreaPreviewNoCopy.shadow=true
 * walks the copy as before and checks, element by element, that the live values give the same sequence after the walk
 * (SHADOW_CHECKS / SHADOW_MISMATCHES).
 */
public final class AreaPreviews {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean noCopy = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.justdirethingsAreaPreviewNoCopy", "true"));
    public static volatile boolean chunkMemo = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.justdirethingsAreaPreviewChunkMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.justdirethingsAreaPreviewNoCopy.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** The clock of the chunk memo (offline proofs replace it). */
    public static volatile LongSupplier clock = System::nanoTime;
    static final long MEMO_NANOS = 1_000_000_000L;
    /** Statistics for probes (plain fields): copies avoided, chunk walks left out by the memo. */
    public static long copiesAvoided, walksLeftOut;
    private static volatile boolean announcedCopy, announcedMemo;
    /** Per chunk (identity, weak): when it was last looked at and whether it held a shown preview then. Render thread only. */
    private static final Map<Object, long[]> MEMO = new WeakHashMap<>();

    private AreaPreviews() {
    }

    /** The values of a block entity map, as a list whose iteration walks them live (no copy). */
    public static final class LiveValues extends ArrayList<Object> {
        private final Collection<?> source;

        LiveValues(Collection<?> source) {
            super(0);
            this.source = source;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Iterator<Object> iterator() {
            return (Iterator<Object>) this.source.iterator();
        }

        @Override
        public int size() {
            return this.source.size();
        }

        @Override
        public boolean isEmpty() {
            return this.source.isEmpty();
        }
    }

    /** renderAreaPreviews' new ArrayList(chunk.getBlockEntities().values()), wrapped (justdirethings_area_preview_no_copy). */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static ArrayList copyFree(Collection values, Operation<ArrayList> original) {
        if (!noCopy || values == null) return original.call(values);
        if (SHADOW) {
            ArrayList copy = original.call(values);
            SHADOW_CHECKS.incrementAndGet();
            Iterator live = values.iterator();
            boolean same = true;
            for (Object o : copy) same &= live.hasNext() && live.next() == o;
            if (!same || live.hasNext()) {
                long m = SHADOW_MISMATCHES.incrementAndGet();
                if (m <= 20) LOGGER.warn("Bons and Furious: justdirethings_area_preview_no_copy shadow mismatch #{}: a chunk's block entity values differ from their copy", m);
            }
            return copy;
        }
        if (!announcedCopy) {
            announcedCopy = true;
            LOGGER.info("Bons and Furious: justdirethings_area_preview_no_copy applies (the area preview walks each chunk's block entities without copying them)");
        }
        copiesAvoided++;
        return new LiveValues(values);
    }

    /** renderAreaPreviews' chunk.getBlockEntities(), wrapped (justdirethings_area_preview_chunk_memo). */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Map blockEntities(Object chunk, Operation<Map> original) {
        if (!chunkMemo || chunk == null) return original.call(chunk);
        long now = clock.getAsLong();
        long[] e = MEMO.get(chunk);
        if (e != null && now - e[0] < MEMO_NANOS && now - e[0] >= 0) {
            if (e[1] == 0) {
                walksLeftOut++;
                return Collections.emptyMap();
            }
            return original.call(chunk);
        }
        Map map = original.call(chunk);
        boolean shown = false;
        if (map != null) {
            for (Object be : map.values()) {
                if (be instanceof AreaAffectingBE a && a.getAreaAffectingData().renderArea) {
                    shown = true;
                    break;
                }
            }
        }
        if (e == null) {
            e = new long[2];
            MEMO.put(chunk, e);
        }
        e[0] = now;
        e[1] = shown ? 1 : 0;
        if (!announcedMemo) {
            announcedMemo = true;
            LOGGER.info("Bons and Furious: justdirethings_area_preview_chunk_memo applies (chunks without a shown area preview are looked at again after a second)");
        }
        return map;
    }

    /** Forgets the chunk memo (offline proof only). */
    public static void forgetForTests() {
        MEMO.clear();
    }
}
