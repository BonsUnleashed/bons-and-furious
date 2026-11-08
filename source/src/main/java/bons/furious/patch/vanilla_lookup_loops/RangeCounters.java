package bons.furious.patch.vanilla_lookup_loops;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_block_range_counters (Minecraft 1.21.1, both sides): runtime switch of
 * BlockPosRangeCounterMixin. No Mojang code here.
 *
 * What vanilla does. BlockPos.betweenClosed(int...) returns an iterable whose iterator (BlockPos$4) keeps a running index
 * and, for every position it hands out, splits that index into x, y and z with two integer divisions and two remainders
 * (index % width, index / width, then % height and / height) before moving its mutable cursor there. Block scans of every
 * kind use it (block entity searches, explosions, fluid and fire spread, mod area scans).
 *
 * What the switch does. When an iterator starts (index 0) with a positive width and height and a positive position count,
 * it keeps the three digits as counters instead: x steps by one, carrying into y at the width and into z at the height.
 * For every index from 0 up to the count, those counters are exactly the digits the divisions give (a mixed-radix counter
 * is that decomposition of 0, 1, 2, ...; the divisions are exact floor divisions because the index never goes negative
 * below a positive count), so the iterator moves its cursor to the same positions in the same order, through the same
 * MutableBlockPos.set call, and ends at the same point. Every other iterator (a width or height of zero or less, or a
 * position count that overflowed to a negative int, where vanilla's index runs through negative values) is marked at its
 * first step and runs the original code unchanged. The decision is made once per iterator, at its first position, so
 * -Dbons_and_furious.blockRangeCounters=false applies to iterators started afterwards.
 */
public final class RangeCounters {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.blockRangeCounters", "true"));
    /** Marks an iterator that runs the original code (never a counter value: z counts up from 0). */
    public static final int STOCK = Integer.MIN_VALUE;
    private static volatile boolean announced;

    private RangeCounters() {
    }

    /** Counter mode for an iterator at its first position: switch on, positive width and height, positive count. */
    public static boolean counterMode(int width, int height, int count) {
        if (!enabled || width <= 0 || height <= 0 || count <= 0) return false;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_block_range_counters applies (BlockPos.betweenClosed steps counters instead of dividing)");
        }
        return true;
    }
}
