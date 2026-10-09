package bons.furious.patch.mekanism_radiation;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import mekanism.api.Chunk3D;
import org.slf4j.Logger;

/**
 * Bons and Furious switch mekanism_radiation_source_range (Mekanism 1.20.1-10.4.13.69; logical server, including the
 * integrated server). SRG member names (ChunkPos.x = f_45578_, ChunkPos.z = f_45579_).
 *
 * What Mekanism does. Every living entity draws level.getRandom().nextInt(20) each tick; on 0 (and for players through
 * tickServer) RadiationManager.getRadiationLevelAndMaxMagnitude(Coord4D) runs
 *   for (chunk : new Chunk3D(coord).expand(radiationChunkCheckRadius))         // 121 Chunk3D + a HashSet at radius 5
 *     for (entry : radiationTable.row(chunk).entrySet()) { level += exposure; maxMagnitude = max(...) }
 * so each check builds (2r+1)^2 Chunk3D objects, a HashSet and a Guava row view per chunk, almost always to find nothing:
 * radiation sources exist only where radioactive gas was dumped or a reactor melted down (2.96% of the integrated server
 * thread's allocation in a steady-play profile). Chunk3D.expand(1) returns only the centre chunk (a 10.4.13 quirk,
 * fixed upstream later); expand(0) also gives the centre chunk only.
 *
 * What the switch does. It answers the expand call inside getRadiationLevelAndMaxMagnitude (and only that call) with the
 * chunks of that set that hold a row of the radiation table, when there are none or exactly one:
 *   - table empty, or no row key of the same dimension inside the box expand would build -> the empty set;
 *   - exactly one such row key -> that key alone;
 *   - anything else (two or more rows in range, more than SCAN_LIMIT rows - SCAN_LIMIT_VIEW when Guava's row map cannot
 *     be read directly -, a table that is not a HashBasedTable, a Chunk3D subclass in range, a negative radius,
 *     coordinates where expand's loop would overflow, a null dimension, the table changing during the scan) -> the
 *     original expand.
 * The row keys are read from the table's row map (one pass, no allocation per row) and compared with the box by
 * dimension identity and Chebyshev distance; nothing is cached between calls.
 * The original loop then runs unchanged over that set: with no rows it returns new LevelAndMaxMagnitude(1.0E-7, 1.0E-7),
 * exactly what it returns when every visited row is empty; with one row it visits the same entries of the same row view
 * in the same order, with the same distanceTo / MAX_RANGE / computeExposure / Math.max calls, so level and maxMagnitude
 * are bit-identical. Why: a chunk C of expand's set reaches a non-empty row only when the table holds a key K with
 * C.equals(K) (Chunk3D.equals: same x, same z, the same dimension object); every other chunk's row view is empty and its
 * loop does nothing. The per-entity RNG draw, the capability lookup, radiate/decay/update and the client packets are
 * outside this method and stay as they are (Mekanism: Overclocked's skip removes the RNG draw; this switch does not).
 *
 * -Dbons_and_furious.mekanismRadiationSourceRange=false switches it off at run time.
 * -Dbons_and_furious.mekanismRadiationSourceRange.shadow=true (verification runs only): the original expand runs and is
 * returned; SHADOW_CHECKS counts the comparisons of its row-holding chunks (in its own order) with the switch's answer,
 * SHADOW_MISMATCHES the differences (WARN for the first 20).
 */
public final class RadiationSourceRange {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mekanismRadiationSourceRange", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.mekanismRadiationSourceRange.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /**
     * The table's row map (Guava StandardTable.backingMap, a LinkedHashMap for HashBasedTable): its key set is
     * rowKeySet() without the Row view and map entry Guava's public view builds per key. Null when the field cannot be
     * reached (another Guava build, a closed module); the public rowKeySet() is used then.
     */
    private static final VarHandle BACKING = backingMapHandle();
    /** Rows are scanned through BACKING when it exists (the harness switches it off to prove the fallback too). */
    public static boolean directRows = BACKING != null;
    /**
     * Above this many rows (all dimensions) the original's point lookups run instead: the scan costs ~5 ns per row direct
     * and ~16 ns through the public view (harness, Ryzen 9 3900X), the original ~5.8 us per call at radius 5, so both limits
     * keep a clear gain (measured 1.6x and 2.7x at the limits).
     */
    public static final int SCAN_LIMIT = 768, SCAN_LIMIT_VIEW = 128;
    private static volatile boolean announced;

    private static VarHandle backingMapHandle() {
        try {
            Class<?> standard = Class.forName("com.google.common.collect.StandardTable", false, Table.class.getClassLoader());
            if (!standard.isAssignableFrom(HashBasedTable.class)) return null;
            VarHandle h = MethodHandles.privateLookupIn(standard, MethodHandles.lookup()).findVarHandle(standard, "backingMap", Map.class);
            Table<String, String, String> probe = HashBasedTable.create();
            probe.put("r", "c", "v");
            Object m = h.get(probe);
            return m instanceof Map<?, ?> map && map.keySet().equals(probe.rowKeySet()) ? h : null;
        } catch (Throwable t) {
            LOGGER.debug("Bons and Furious: mekanism_radiation_source_range scans rows through Guava's public view ({})", t.toString());
            return null;
        }
    }

    private RadiationSourceRange() {
    }

    /** RadiationManager.getRadiationLevelAndMaxMagnitude(Coord4D): its new Chunk3D(coord).expand(radius) call. */
    public static Set<Chunk3D> chunksToVisit(Table<?, ?, ?> table, Chunk3D centre, int radius, Operation<Set<Chunk3D>> original) {
        if (!enabled) return original.call(centre, radius);
        Set<Chunk3D> ours = rowChunksInRange(table, centre, radius);
        if (ours == null) return original.call(centre, radius);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: mekanism_radiation_source_range applies (radiation checks visit only the chunks that hold a radiation source){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        if (SHADOW) {
            Set<Chunk3D> full = original.call(centre, radius);
            verify(table, full, ours);
            return full;
        }
        return ours;
    }

    /**
     * The chunks of centre.expand(radius) that are row keys of the table when there are none (empty set) or exactly one
     * (that key); null = run the original expand.
     */
    public static Set<Chunk3D> rowChunksInRange(Table<?, ?, ?> table, Chunk3D centre, int radius) {
        if (table == null || table.getClass() != HashBasedTable.class || centre == null || centre.getClass() != Chunk3D.class
                || centre.dimension == null || radius < 0) return null;
        int r = radius == 1 ? 0 : radius;   // 10.4.13: expand(1) = Collections.singleton(this)
        long x = centre.f_45578_, z = centre.f_45579_;
        if (radius != 1 && (x - r < Integer.MIN_VALUE || x + r >= Integer.MAX_VALUE || z - r < Integer.MIN_VALUE || z + r >= Integer.MAX_VALUE))
            return null;   // expand's int loop would wrap (or never end): leave it to the original
        if (table.isEmpty()) return Collections.emptySet();
        Set<?> rows;
        if (directRows && BACKING != null) {
            rows = ((Map<?, ?>) BACKING.get(table)).keySet();
            if (rows.size() > SCAN_LIMIT) return null;
        } else {
            rows = table.rowKeySet();
            if (rows.size() > SCAN_LIMIT_VIEW) return null;
        }
        Chunk3D found = null;
        try {
            for (Object o : rows) {
                // a key that is not a Chunk3D never equals one of expand's chunks (Chunk3D.equals needs instanceof)
                if (!(o instanceof Chunk3D k) || k.dimension != centre.dimension) continue;
                if (Math.abs(k.f_45578_ - x) > r || Math.abs(k.f_45579_ - z) > r) continue;
                if (found != null || k.getClass() != Chunk3D.class) return null;   // two rows in range, or a subclass: original
                found = k;
            }
        } catch (ConcurrentModificationException e) {
            return null;
        }
        return found == null ? Collections.emptySet() : Collections.singleton(found);
    }

    /** Shadow mode: the row-holding chunks of the original set, in its order, must be exactly the switch's answer. */
    private static void verify(Table<?, ?, ?> table, Set<Chunk3D> full, Set<Chunk3D> ours) {
        List<Chunk3D> hits = new ArrayList<>(2);
        for (Chunk3D c : full) if (table.containsRow(c)) hits.add(c);
        boolean same = hits.size() == ours.size() && (hits.isEmpty() || hits.get(0).equals(ours.iterator().next()));
        SHADOW_CHECKS.incrementAndGet();
        if (!same) {
            long m = SHADOW_MISMATCHES.incrementAndGet();
            if (m <= 20) LOGGER.warn("Bons and Furious: mekanism_radiation_source_range shadow mismatch #{}: original visits rows {}, the switch {}", m, hits, ours);
        }
    }
}
