package bons.furious.patch.vanilla_ticking_prune;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_ticking_tracker_prune (Minecraft 1.20.1 on Forge 47.4.16; server side incl. the
 * integrated server). SRG member names. Idea: Saturn 0.0.9 changelog ("Ticking Tracker memory leak fix"), idea text
 * only; the exact form is ours.
 *
 * What vanilla does. TickingTracker (the simulation-distance tracker of every level's DistanceManager) keeps chunk
 * levels in a Long2ByteOpenHashMap whose default answer is 33 (no ticking). setLevel (m_7351_) removes an entry only for
 * a level above 33, but the propagation base class (DynamicGraphMinFixedPoint) never passes more than levelCount - 1 =
 * 33: a chunk that stops ticking is written as 33 and stays in the map for good. Every chunk position a player's
 * simulation area ever covered stays there for the life of the level (about 440 entries per newly crossed chunk row at
 * simulation distance 8), so the map only grows, rehashes ever larger, and spreads the hot lookups of
 * inBlockTickingRange / inEntityTickingRange over more memory.
 *
 * What the switch does. setLevel's put(chunk, level) removes the entry instead when the level equals the map's default
 * answer (33).
 *
 * Why the result is identical. The map's get answers the default for an absent key, so every getLevel (the only read:
 * TickingTracker.getLevel / getLevel(ChunkPos)) returns the same number with or without the entry; no class in either
 * instance reads, sizes or iterates this map other than TickingTracker itself (census of every jar: only TickingTracker
 * names the field f_184141_). remove returns the previous value or the default exactly as put would, and setLevel drops
 * the result anyway. setLevel still runs with the same arguments, so Bons' version and epoch hooks at its HEAD and RETURN
 * (vanilla_block_ticking_range_memo, vanilla_ticker_range_memo) move exactly as before.
 *
 * -Dbons_and_furious.tickingTrackerPrune=false switches it off at run time.
 * -Dbons_and_furious.tickingTrackerPrune.shadow=true (verification runs only): after each pruning remove the map is asked
 * for the key again and must answer the level that would have been stored (SHADOW_CHECKS / SHADOW_MISMATCHES).
 */
public final class TickingPrune {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.tickingTrackerPrune", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.tickingTrackerPrune.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static volatile boolean announced;

    private TickingPrune() {
    }

    /** TickingTracker.setLevel (m_7351_): its f_184141_.put(chunk, (byte) level) call. */
    public static byte put(Long2ByteMap map, long chunk, byte level, Operation<Byte> original) {
        if (!enabled || level != map.defaultReturnValue()) return original.call(map, chunk, level);
        byte previous = map.remove(chunk);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_ticking_tracker_prune applies (chunks that stop ticking leave the ticking-level map){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        if (SHADOW) {
            SHADOW_CHECKS.incrementAndGet();
            if (map.get(chunk) != level) {
                long k = SHADOW_MISMATCHES.incrementAndGet();
                if (k <= 20) LOGGER.warn("Bons and Furious: vanilla_ticking_tracker_prune shadow mismatch #{} at chunk {}", k, chunk);
            }
        }
        return previous;
    }
}
