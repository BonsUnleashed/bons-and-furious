package bons.furious.patch.embeddium_cache;

import it.unimi.dsi.fastutil.longs.Long2ReferenceLinkedOpenHashMap;
import java.util.function.Predicate;
import org.embeddedt.embeddium.impl.world.cloned.ClonedChunkSection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch embeddium_section_cache_prefix_cleanup (Embeddium 0.3.31+mc1.20.1, client). No Embeddium code is
 * carried here; the mixin passes in Embeddium's own map and its own expiry predicate.
 *
 * ClonedChunkSectionCache keeps up to 512 cloned sections in a Long2ReferenceLinkedOpenHashMap. Every frame cleanup() sets
 * time = System.nanoTime() and walks the whole map with values().removeIf(entry -> time > entry.lastUsed + 5 s). The map's
 * linked order is sorted by lastUsed: acquire() moves the section it hands out to the end and stamps it with the cache's
 * time, time changes only in cleanup() and only grows, and invalidate() / removeFirst() only take entries out. So the expired
 * entries are always a prefix of the linked order, and removing entries from the front while the predicate holds removes
 * exactly the entries removeIf removes, in the same order, and leaves the same remaining linked order.
 *
 * The mixin uses the prefix walk only while that ordering is known to hold ("trusted"): the first cleanup of a cache, a
 * cleanup after the switch was turned off at runtime, and any cleanup whose time is smaller than the previous one (a clock
 * that went backwards) use the original removeIf, followed by one check of the whole order (stamps non-decreasing from
 * first to last and none newer than the current time); only a passing check makes the cache trusted again.
 */
public final class SectionCacheCleanup {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.sectionCachePrefixCleanup=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.sectionCachePrefixCleanup", "true"));
    /**
     * In-game self-check for rigs: -Dbons_and_furious.sectionCachePrefixCleanupVerify=true runs the original removeIf after
     * every prefix walk as well (so the cache always ends exactly as the original leaves it) and counts the walks after which
     * it still found an expired section.
     */
    public static final boolean VERIFY = Boolean.getBoolean("bons_and_furious.sectionCachePrefixCleanupVerify");
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;
    private static volatile boolean warnedClock;
    private static volatile boolean warnedOrder;
    /** Cleanups by path (for the harness and the probe): prefix walk, original removeIf, failed order checks, backwards clocks. */
    public static long prefixCleanups, fullCleanups, orderCheckFailures, clockWentBack;
    /** Verify mode only: prefix walks checked, and walks after which the original removeIf still removed something. */
    public static long verifiedCleanups, verifyFailures;

    private SectionCacheCleanup() {
    }

    /**
     * Removes the entries the predicate calls expired from the front of the linked order, stopping at the first entry it
     * keeps. Only valid while the order is trusted (see the class comment). Returns what removeIf would return.
     */
    public static boolean removeExpiredPrefix(Long2ReferenceLinkedOpenHashMap<ClonedChunkSection> map, Predicate<? super ClonedChunkSection> expired) {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: embeddium_section_cache_prefix_cleanup: Embeddium's section cache drops expired sections from the oldest end "
                    + "instead of checking all of them every frame");
        }
        prefixCleanups++;
        boolean removed = false;
        while (!map.isEmpty() && expired.test(map.get(map.firstLongKey()))) {
            map.removeFirst();
            removed = true;
        }
        return removed;
    }

    /** Counts a cleanup that used the original removeIf. */
    public static void full() {
        fullCleanups++;
    }

    /** Verify mode: the original removeIf ran after a prefix walk; extra = it removed something the walk had left. */
    public static void verified(boolean extra, int size) {
        verifiedCleanups++;
        if (extra && verifyFailures++ < 10) {
            LOGGER.warn("Bons and Furious: embeddium_section_cache_prefix_cleanup VERIFY: the original cleanup removed sections the prefix walk had kept "
                    + "({} sections left); please report this", size);
        }
    }

    /**
     * O(1) tripwire before every prefix walk: in a sorted map the oldest entry is not newer than the newest one. It cannot
     * fail while only Embeddium's own methods touch the cache (they keep the order); if anything else ever broke the order,
     * this catches the usual shape of it (entries stamped with a smaller time appended after older ones) and the caller
     * falls back to the original removeIf and the full order check.
     */
    public static boolean endsInOrder(Long2ReferenceLinkedOpenHashMap<ClonedChunkSection> map) {
        if (map.size() < 2 || map.get(map.firstLongKey()).getLastUsedTimestamp() <= map.get(map.lastLongKey()).getLastUsedTimestamp()) return true;
        return fail();
    }

    /** The cache's time went from previous down to now: the order is no longer trusted. Logs one WARN per game. */
    public static void clockWentBack(long now, long previous) {
        clockWentBack++;
        if (!warnedClock) {
            warnedClock = true;
            LOGGER.warn("Bons and Furious: embeddium_section_cache_prefix_cleanup: System.nanoTime went back by {} ns; Embeddium's section cache uses its "
                    + "original full cleanup until its order is checked again", previous - now);
        }
    }

    /**
     * True when the map's stamps are non-decreasing in linked order and none is newer than now, i.e. when the prefix walk
     * removes exactly what removeIf removes for this and every later cleanup whose time does not go back.
     */
    public static boolean ordered(Long2ReferenceLinkedOpenHashMap<ClonedChunkSection> map, long now) {
        long last = Long.MIN_VALUE;
        for (ClonedChunkSection s : map.values()) {
            long t = s.getLastUsedTimestamp();
            if (t < last) return fail();
            last = t;
        }
        return last <= now || fail();
    }

    private static boolean fail() {
        orderCheckFailures++;
        if (!warnedOrder) {
            warnedOrder = true;
            LOGGER.warn("Bons and Furious: embeddium_section_cache_prefix_cleanup: Embeddium's section cache is not ordered by last use; it keeps the "
                    + "original full cleanup until it is");
        }
        return false;
    }
}
