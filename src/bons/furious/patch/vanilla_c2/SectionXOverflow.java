package bons.furious.patch.vanilla_c2;

import it.unimi.dsi.fastutil.longs.LongSortedSet;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch vanilla_entity_section_x_overflow (a FIX; Minecraft 1.20.1, both sides; helper of
 * bons.furious.mixin.vanilla_c2.EntitySectionXOverflowMixin). No Mojang code here.
 *
 * EntitySectionStorage keeps its section keys (SectionPos.asLong: 22 bits x, 22 bits z, 20 bits y, signed long) in a sorted
 * set and reads one x column at a time as {@code subSet(asLong(x, 0, 0), asLong(x, -1, -1) + 1)}
 * (forEachAccessibleNonEmptySection, every entity box query) and one chunk column as
 * {@code subSet(asLong(x, 0, z), asLong(x, -1, z) + 1)} (getChunkSections, entity chunk load/unload/status changes).
 * For packed section x 2^21-1 (block X 33,554,416..33,554,431, repeating every 67,108,864 blocks; the first negative column
 * is block X -33,554,448..-33,554,433) the inclusive end asLong(x, -1, -1) is Long.MAX_VALUE, "+ 1" wraps to
 * Long.MIN_VALUE and fastutil throws IllegalArgumentException("Start element (..) is larger than end element (..)"), which
 * crashes the querying thread (a runaway Relics essence is one way to get there; any mod's oversized box query is another).
 * getChunkSections overflows the same way for the chunk with packed x 2^21-1 and packed z -1.
 *
 * The fix: exactly when the exclusive end wrapped (end == Long.MIN_VALUE while the start is larger, i.e. only in those
 * columns, where the original always throws), the column is read as {@code tailSet(start)}: every key >= start, which is
 * every key up to the intended inclusive end Long.MAX_VALUE. Every other call is the original subSet call, unchanged, so
 * every input the original handles gives the identical result; the overflowing ones get the sections the code meant
 * instead of an exception. Valkyrien Skies 2.4.11 wraps its shipyard re-query of this method in
 * catch (IllegalArgumentException) { printStackTrace }; with the fix such a query returns its full result instead of a
 * stack trace and a truncated one.
 */
public final class SectionXOverflow {
    /** Runtime switch. -Dbons_and_furious.entitySectionXOverflow=false keeps the original call (and its exception). */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.entitySectionXOverflow", "true"));
    /** How often the fix answered a column the original would have thrown on (read by probes and the harness). */
    public static final AtomicLong FIXED = new AtomicLong();
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private SectionXOverflow() {
    }

    /**
     * The overflowing column only (the redirect checked toExclusive == Long.MIN_VALUE, from > toExclusive and the switch):
     * every key from {@code from} up, i.e. up to the intended inclusive end Long.MAX_VALUE. Kept out of the redirect so the
     * ordinary call stays small enough to inline.
     */
    public static LongSortedSet overflow(LongSortedSet keys, long from) {
        FIXED.incrementAndGet();
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_entity_section_x_overflow: an entity section query reached the column of packed section x 2^21-1 "
                    + "(block X 33554416..33554431, or a column a multiple of 67108864 blocks away); it is read up to the last key instead of "
                    + "throwing \"Start element is larger than end element\"");
        }
        return keys.tailSet(from);
    }
}
