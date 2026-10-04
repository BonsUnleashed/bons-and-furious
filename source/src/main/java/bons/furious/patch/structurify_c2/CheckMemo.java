package bons.furious.patch.structurify_c2;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch structurify_check_memo_slim (Structurify, CC BY-NC-ND 4.0; 1.21.1 tested build
 * structurify-neoforge-2.0.42+mc1.21.1; both sides, worldgen threads). No Structurify code is carried: the mixin wraps
 * three call sites, the logic below is our own.
 *
 * StructureChecker.checkStructure(start, ...) decides whether a structure start may stay (overlap, biome and flatness
 * checks). It keeps its answer per (structure, chunk) in a ConcurrentHashMap on the chunk generator:
 * {@code structureChecks.computeIfAbsent(id, id -> { overlap = overlapChecks.computeIfAbsent(id, ...); biome =
 * biomeChecks.computeIfAbsent(id, ...); flatness = flatnessChecks.computeIfAbsent(id, ...); ... })}, and none of those
 * maps is ever cleared, so every start checked during a session stays in all four maps until the server stops.
 *
 * The three inner maps are write-only: their getters are referenced by no class but Structurify's ChunkGeneratorMixin
 * (which only declares them), its StructurifyChunkGenerator interface and StructureChecker's lambda (byte scan of every
 * pinned 1.21.1 target jar, jar-in-jar included), and the inner computeIfAbsent calls run only inside the outer mapping
 * function for the same id, which ConcurrentHashMap runs at most once per id (the function never returns null, and the
 * three checks catch every Throwable themselves). So every inner call is a miss that computes the check, stores it and
 * returns it. The inner calls now apply their check directly and return its result: the same checks, in the same order,
 * on the same thread, with the same results (including the overlap check's section claims); the three map entries (each
 * with its own boxed key) are no longer kept. Concurrency is unchanged: the outer computeIfAbsent still serializes each id
 * across worker threads. Whether Structurify's checks are switched on or off does not matter.
 *
 * Ported to 1.21.1: Structurify 2.0.42 added releaseOverlapClaims(data, generator) after a failed biome or flatness
 * check, inside the outer function and outside the checks' catch-all. It reads only the start's pieces, the structure
 * data and the section-claim map (never the three inner maps), and can throw only where the overlap check's own
 * computation of the same sections threw (that check caught it and answered true): the outer function then throws in both
 * arms, at the same point, and stores no outer answer. Should the same id be checked again, the original finds its stored
 * overlap/biome answers where the switch recomputes them; the overlap check throws again before claiming anything, and the
 * biome and flatness checks only sample, so both arms reach the same answer and the same exception. The shadow mode counts
 * exactly that case (an inner id already present). Its new testStructure / debugCheckStructure paths call the checks
 * directly and do not touch the maps.
 *
 * Runtime flag: -Dbons_and_furious.structurifyCheckMemoSlim=false runs the original on every call. Shadow mode for rigs:
 * -Dbons_and_furious.structurifyCheckMemoSlim.shadow=true runs the original inner calls and counts any that found its id
 * already present, the one case the switch relies on never happening (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN <= 20).
 */
public final class CheckMemo {
    /** Runtime switch (the config switch acts when the class is transformed). */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.structurifyCheckMemoSlim", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.structurifyCheckMemoSlim.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong();
    public static final AtomicLong SHADOW_MISMATCHES = new AtomicLong();
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private CheckMemo() {
    }

    /** The handler for each of the three inner computeIfAbsent calls inside the outer mapping function. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Object inner(Map map, Object id, Function check, Operation<Object> original) {
        if (!enabled) return original.call(map, id, check);
        if (!announced) announce();
        if (SHADOW) {
            SHADOW_CHECKS.incrementAndGet();
            if (map.containsKey(id) && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
                LOGGER.warn("Bons and Furious: structurify_check_memo_slim SHADOW MISMATCH: structure check {} was already present in an inner map", id);
            }
            return original.call(map, id, check);
        }
        return check.apply(id);
    }

    private static void announce() {
        announced = true;
        LOGGER.info(SHADOW ? "Bons and Furious: structurify_check_memo_slim SHADOW MODE: Structurify keeps its four check maps; inner hits are counted"
                : "Bons and Furious: structurify_check_memo_slim: Structurify's structure checks keep one answer per start instead of four");
    }
}
