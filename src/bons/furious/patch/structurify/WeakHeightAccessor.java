package bons.furious.patch.structurify;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Map;
import net.minecraft.world.level.LevelHeightAccessor;

/**
 * The LevelHeightAccessor of one Structurify height-cache key, held weakly (switch structurify_height_cache).
 *
 * The key keeps this reference instead of a strong field, so a cached height no longer keeps the chunk or region it
 * was computed for in memory. Once the accessor has been garbage collected, the reference sits in the queue of the
 * thread that created the key; the cache map is per thread too, and its next lookup or insert on that thread removes
 * the key through {@link #drainCollectedAccessors}.
 *
 * Structurify's key class cannot become a WeakReference itself (a mixin cannot change a superclass), which is what the
 * 1.0.19 script did; this object carries the reference and a link back to its key instead.
 */
public final class WeakHeightAccessor extends WeakReference<LevelHeightAccessor> {
    /** Collected accessors of the keys created on this thread. Same name as the 1.0.19 field. */
    private static final ThreadLocal<ReferenceQueue<LevelHeightAccessor>> HEIGHT_ACCESSOR_QUEUE = ThreadLocal.withInitial(ReferenceQueue::new);

    /** The cache key that holds this reference; it is removed from its cache once the accessor is collected. */
    private final Object key;

    private WeakHeightAccessor(LevelHeightAccessor accessor, Object key) {
        super(accessor, HEIGHT_ACCESSOR_QUEUE.get());
        this.key = key;
    }

    /** The reference for a new key, or null for a key built without an accessor (nothing to collect, never queued). */
    public static WeakHeightAccessor of(LevelHeightAccessor accessor, Object key) {
        return accessor == null ? null : new WeakHeightAccessor(accessor, key);
    }

    /**
     * The accessor part of key equality: both keys were built without an accessor, or both still refer to the same
     * accessor object. A key whose accessor has been collected is equal to no other key.
     */
    public static boolean sameAccessor(WeakHeightAccessor mine, WeakHeightAccessor theirs) {
        if (mine == null || theirs == null) {
            return mine == theirs;
        }
        LevelHeightAccessor accessor = mine.get();
        return accessor != null && accessor == theirs.get();
    }

    /** Removes from this thread's cache every key whose accessor has been collected since the last call. */
    public static void drainCollectedAccessors(Map<?, ?> cache) {
        ReferenceQueue<LevelHeightAccessor> queue = HEIGHT_ACCESSOR_QUEUE.get();
        Reference<? extends LevelHeightAccessor> collected;
        while ((collected = queue.poll()) != null) {
            cache.remove(((WeakHeightAccessor) collected).key);
        }
    }
}
