package bons.furious.patch.chunksending;

import com.chunksending.chunk.IChunkPacketCache;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Bons and Furious switch chunksending_expiry_queue (ChunkSending 3.7). No ChunkSending code here.
 *
 * ChunkSending caches each chunk's packet and registers the cache in a WeakHashMap with a deadline of nanoTime + 5 minutes
 * (addToClear). At the start of every server tick, clearExpiredPacketCaches walked the whole map to clear and drop the few
 * caches whose deadline had passed, so its cost grew with the number of cached chunks.
 *
 * addToClear now also appends (cache, deadline) to a FIFO; the map stays the authority on each cache's current deadline.
 * Deadlines are nanoTime plus a constant, so the FIFO is in deadline order, and the tick sweep only pops the records whose
 * deadline has passed (the same overflow-safe comparison). A popped record clears and drops its cache only when the map
 * still holds exactly that deadline for it; a cache added again carries a newer record further back, a collected cache is
 * skipped exactly as the map's iterator skips it, and a null key (which the original drops on every sweep) is dropped too.
 * So every tick clears the same set of caches as the original walk; only their order within the tick differs (each clear
 * nulls one field of a different chunk). If the switch is turned off at runtime the original walk runs, and the FIFO is
 * rebuilt from the map when it is turned back on.
 */
public final class ChunkSendingExpiry {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.chunkSendingExpiry=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.chunkSendingExpiry", "true"));
    private static final ConcurrentLinkedQueue<Record> QUEUE = new ConcurrentLinkedQueue<>();
    private static volatile boolean rebuild;

    private record Record(WeakReference<IChunkPacketCache> cache, long deadline) {
    }

    private ChunkSendingExpiry() {
    }

    /** End of addToClear: note the deadline the map now holds for this cache. */
    public static void added(Map<IChunkPacketCache, Long> map, IChunkPacketCache cache) {
        if (!enabled) {
            rebuild = true;
            return;
        }
        if (cache == null) return;                         // the sweep drops a null key directly
        Long deadline = map.get(cache);
        if (deadline != null) QUEUE.add(new Record(new WeakReference<>(cache), deadline));
    }

    /** Start of clearExpiredPacketCaches: true when the sweep was done here (the original walk is then skipped). */
    public static boolean expire(Map<IChunkPacketCache, Long> map) {
        return enabled && expire(map, System.nanoTime());
    }

    /** The sweep at a given nanoTime (the tick's own reading of the clock). */
    public static boolean expire(Map<IChunkPacketCache, Long> map, long now) {
        if (!enabled) return false;
        if (rebuild) {
            rebuild = false;
            QUEUE.clear();
            List<Record> all = new ArrayList<>();
            for (Map.Entry<IChunkPacketCache, Long> e : map.entrySet()) {
                if (e.getKey() != null) all.add(new Record(new WeakReference<>(e.getKey()), e.getValue()));
            }
            all.sort(Comparator.comparingLong(Record::deadline));
            QUEUE.addAll(all);
        }
        if (map.isEmpty()) {
            QUEUE.clear();                                 // nothing to clear; stale records only
            return true;
        }
        map.remove(null);                                  // the original walk drops a null key whatever its deadline
        Record r;
        while ((r = QUEUE.peek()) != null) {
            if (now - r.deadline() < 0L) break;            // the first record not yet due: all later ones are later still
            QUEUE.poll();
            IChunkPacketCache cache = r.cache().get();
            if (cache == null) continue;                   // collected: the map has dropped it, nothing to clear
            Long current = map.get(cache);
            if (current == null || current != r.deadline()) continue;   // already dropped, or added again with a later deadline
            cache.clearCachedPacket();
            map.remove(cache);
        }
        return true;
    }
}
