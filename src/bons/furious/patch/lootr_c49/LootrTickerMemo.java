package bons.furious.patch.lootr_c49;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import noobanidus.mods.lootr.block.entities.TileTicker;
import noobanidus.mods.lootr.event.HandleChunk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch lootr_ticker_blocker_memo (Lootr 0.7.35.94, MIT; server side incl. the integrated server). Ours;
 * no Lootr code is carried (the tests below are Lootr's own calls, made on the same objects).
 *
 * What Lootr does. TileTicker.serverTick (ServerTickEvent END) walks every chest waiting to become a Lootr chest. For each
 * one that is still valid (level present, not too old, inside the world border) it asks hasChunk for the chest's own chunk,
 * then for every chunk of the 5 x 5 area around it until one is missing, then, under the LOADED_CHUNKS lock, whether each of
 * those chunks has ever been loaded (again until one is missing). A chest is converted only when every test passes; while
 * a player explores, hundreds of chests wait and each costs up to 26 hasChunk calls and 25 set lookups per tick.
 *
 * What the switch does, at Lootr's own-chunk test (after its level / age / border checks; that test itself always runs):
 * - an entry Lootr has not evaluated yet is marked and left to Lootr (a chest that can convert at once costs nothing more);
 * - an entry that is still waiting after Lootr's evaluation gets the same chunk tests from us, in Lootr's order (area
 *   hasChunk, then LOADED_CHUNKS under its lock); the first failing one is remembered and the chest waits; if none fails,
 *   Lootr evaluates again;
 * - an entry with a remembered failing test repeats only that test: still failing -> the chest waits; passing -> the memo
 *   is cleared and Lootr evaluates (and converts if everything passes).
 *
 * Why the result is identical. Lootr converts only when ALL tests pass, so one failing test is enough to know it waits; our
 * tests are the same calls on the same objects (the entry's level chunk source, LOADED_CHUNKS under its lock) for chunks of
 * the entry's own set, which Lootr never changes after addEntry. hasChunk and the set lookups only read, so leaving out the
 * other calls changes nothing. The tick's copy of the entries, their order, every removal and every conversion happen
 * exactly as before, in the same tick.
 *
 * -Dbons_and_furious.lootrTickerBlockerMemo=false switches it off at run time.
 * -Dbons_and_furious.lootrTickerBlockerMemo.shadow=true: beside every "wait" we answer, Lootr's full set of chunk tests runs
 * and must fail too (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20).
 */
public final class LootrTickerMemo {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.lootrTickerBlockerMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.lootrTickerBlockerMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    public static final int NONE = 0, HAS_CHUNK = 1, LOADED = 2;
    private static volatile boolean announced;

    /** The entry whose tests run now (server thread: TileTicker.serverTick holds its world lock). */
    private static Object current;

    private LootrTickerMemo() {}

    /** Lootr asked this entry's age: its chunk tests may follow. */
    public static void entry(Object entry) {
        current = entry;
    }

    /**
     * Lootr's own-chunk hasChunk answered {@code loaded}. Returns what Lootr's code then sees: false makes the chest wait
     * (Lootr's "continue"), which this returns only when one of Lootr's own tests fails now.
     */
    public static boolean ownChunk(ServerChunkCache cache, boolean loaded) {
        if (!loaded || !enabled || !(current instanceof LootrEntryMemo memo) || !(current instanceof TileTicker.Entry entry)) return loaded;
        int kind = memo.bons$lootrC49Kind();
        if (kind != NONE) {
            boolean stillFails = kind == HAS_CHUNK ? !cache.m_5563_(memo.bons$lootrC49X(), memo.bons$lootrC49Z())
                    : loadedMiss(entry.getDimension(), memo.bons$lootrC49Pos());
            if (stillFails) return waits(cache, entry);
            memo.bons$lootrC49Remember(NONE, 0, 0, null);
            return true;
        }
        if (!memo.bons$lootrC49Seen()) {
            memo.bons$lootrC49SetSeen(true);
            return true;
        }
        // Lootr's evaluation left this chest waiting: find the failing test ourselves, in Lootr's order
        for (ChunkPos p : entry.getChunkPositions()) {
            if (!cache.m_5563_(p.f_45578_, p.f_45579_)) {
                memo.bons$lootrC49Remember(HAS_CHUNK, p.f_45578_, p.f_45579_, null);
                return waits(cache, entry);
            }
        }
        Map<ResourceKey<Level>, Set<ChunkPos>> map = HandleChunk.LOADED_CHUNKS;
        synchronized (map) {
            Set<ChunkPos> set = map.get(entry.getDimension());
            if (set != null) {
                for (ChunkPos p : entry.getChunkPositions()) {
                    if (!set.contains(p)) {
                        memo.bons$lootrC49Remember(LOADED, 0, 0, p);
                        return waits(cache, entry);
                    }
                }
            }
        }
        return true;
    }

    private static boolean waits(ServerChunkCache cache, TileTicker.Entry entry) {
        if (SHADOW) shadow(cache, entry);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: lootr_ticker_blocker_memo applies (Lootr's waiting chests repeat only the chunk test that held them back){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        return false;
    }

    /** Lootr's LOADED_CHUNKS test for one chunk, as serverTick makes it: true when the dimension's set exists and lacks it. */
    private static boolean loadedMiss(ResourceKey<Level> dimension, Object pos) {
        Map<ResourceKey<Level>, Set<ChunkPos>> map = HandleChunk.LOADED_CHUNKS;
        synchronized (map) {
            Set<ChunkPos> loaded = map.get(dimension);
            return loaded != null && !loaded.contains(pos);
        }
    }

    /** Shadow mode: Lootr's full chunk tests for the entry must fail too when we say it waits. */
    private static void shadow(ServerChunkCache cache, TileTicker.Entry entry) {
        SHADOW_CHECKS.incrementAndGet();
        boolean fails = false;
        for (ChunkPos p : entry.getChunkPositions()) {
            if (!cache.m_5563_(p.f_45578_, p.f_45579_)) {
                fails = true;
                break;
            }
        }
        if (!fails) {
            Map<ResourceKey<Level>, Set<ChunkPos>> map = HandleChunk.LOADED_CHUNKS;
            synchronized (map) {
                Set<ChunkPos> loaded = map.get(entry.getDimension());
                if (loaded != null) {
                    for (ChunkPos p : entry.getChunkPositions()) {
                        if (!loaded.contains(p)) {
                            fails = true;
                            break;
                        }
                    }
                }
            }
        }
        if (!fails) {
            long m = SHADOW_MISMATCHES.incrementAndGet();
            if (m <= 20) LOGGER.warn("Bons and Furious: lootr_ticker_blocker_memo shadow mismatch #{}: the chest at {} in {} would pass every chunk test",
                    m, entry.getPosition(), entry.getDimension());
        }
    }
}
