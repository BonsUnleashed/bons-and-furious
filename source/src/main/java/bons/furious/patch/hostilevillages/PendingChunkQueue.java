package bons.furious.patch.hostilevillages;

import com.mojang.logging.LogUtils;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.Mob;
import org.slf4j.Logger;

/**
 * Bons and Furious switch hostilevillages_pending_chunk_queue (Hostile Villages 1.20.1-5.7). No Hostile Villages code here.
 *
 * Hostile Villages replaces the villagers a structure template creates. Worldgen threads put (mob, live ServerLevel) into
 * EventHandler.toAdd, and at the end of every level tick addToWorld empties the whole list: it adds each mob and calls
 * finalizeSpawn with level.getCurrentDifficultyAt(mob.blockPosition()). Vanilla reads the chunk there whenever
 * hasChunkAt(pos) is true, which on a server means the chunk holds a full-status ticket, not that it is finished. For a
 * village chunk that a pregenerator or a player's view distance has asked for but that is still generating, the server
 * thread then waits for that chunk and its neighbours behind the whole worker queue: on 2026-09-30 a 41 s freeze and
 * 2-12 s stalls every one to three minutes during a pregen with C2ME off, every server-thread chunk wait in a 10-minute
 * recording in this call.
 *
 * The loop now stops at a queue head whose chunk is in exactly that state (hasChunkAt true, no finished chunk yet), and the
 * entry, with everything behind it, is processed on a later tick: once the chunk is finished, or once nothing asks for it
 * any more (the original reads no chunk then either). Queue order, the mobs and their setup are unchanged. An entry that
 * has held the queue for five minutes is processed with the original call. The list itself becomes a synchronized list:
 * worldgen threads add to it while the server thread removes from it, which the plain ArrayList did not allow.
 */
public final class PendingChunkQueue {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.hostileVillagesPendingChunk=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.hostileVillagesPendingChunk", "true"));
    /** How long one entry may hold the queue before the original call runs (and waits) anyway. */
    public static final long LIMIT_NANOS = 300_000_000_000L;
    private static final Logger LOGGER = LogUtils.getLogger();

    // Server thread only (addToWorld runs in the level tick).
    private static Object held;
    private static long heldSince;
    private static int heldX;
    private static int heldZ;
    private static String heldLevel;
    private static boolean announced;
    /** Finished holds, their total and longest duration, and holds that reached the limit (for tests and the log). */
    public static long holds;
    public static long heldNanos;
    public static long maxHeldNanos;
    public static long limitReached;

    private PendingChunkQueue() {
    }

    /** End of EventHandler's static initializer: the queue that worldgen threads and the server thread share. */
    public static <T> List<T> synchronizedQueue(List<T> queue) {
        return queue == null ? null : Collections.synchronizedList(queue);
    }

    /**
     * Wraps the loop condition of addToWorld after its own isEmpty() returned false. Runs on the server thread, the only
     * thread that removes from the queue, so the head cannot change during the call. True stops the loop for this tick.
     */
    public static boolean holdBack(List<?> queue) {
        long now = System.nanoTime();
        if (!enabled) {
            release(now, false);
            return false;
        }
        Object head = queue.get(0);
        if (!mustWait(head)) {
            release(now, false);
            return false;
        }
        if (head != held) {
            release(now, false);
            Tuple<?, ?> t = (Tuple<?, ?>) head;
            BlockPos pos = ((Mob) t.getA()).blockPosition();
            held = head;
            heldSince = now;
            heldX = pos.getX() >> 4;
            heldZ = pos.getZ() >> 4;
            heldLevel = String.valueOf(((ServerLevel) t.getB()).dimension().location());
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: hostilevillages_pending_chunk_queue: Hostile Villages' spawn queue waits for chunk [{}, {}] in {} "
                        + "to finish generating instead of the server thread waiting for it (later waits are logged at DEBUG)", heldX, heldZ, heldLevel);
            }
            return true;
        }
        if (now - heldSince >= LIMIT_NANOS) {
            limitReached++;
            LOGGER.warn("Bons and Furious: hostilevillages_pending_chunk_queue: chunk [{}, {}] in {} has not finished generating after {} s; "
                    + "the queue continues with the original call", heldX, heldZ, heldLevel, (now - heldSince) / 1_000_000_000L);
            release(now, true);
            return false;
        }
        return true;
    }

    /**
     * True exactly when the original call would wait for chunk generation: getCurrentDifficultyAt reads the mob's chunk
     * when hasChunkAt(pos) (hasChunkAt) is true, and that read waits when no finished chunk is there yet.
     */
    static boolean mustWait(Object head) {
        if (!(head instanceof Tuple<?, ?> t) || !(t.getA() instanceof Mob mob) || !(t.getB() instanceof ServerLevel level)) {
            return false;
        }
        BlockPos pos = mob.blockPosition();
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null;
    }

    private static void release(long now, boolean limit) {
        if (held == null) {
            return;
        }
        long d = now - heldSince;
        holds++;
        heldNanos += d;
        maxHeldNanos = Math.max(maxHeldNanos, d);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Bons and Furious: hostilevillages_pending_chunk_queue: queue held {} ms for chunk [{}, {}] in {}{}",
                    d / 1_000_000L, heldX, heldZ, heldLevel, limit ? " (limit reached)" : "");
        }
        held = null;
        heldLevel = null;
    }
}
