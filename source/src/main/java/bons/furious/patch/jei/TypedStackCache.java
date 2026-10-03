package bons.furious.patch.jei;

import com.google.common.base.Preconditions;
import com.google.common.cache.CacheLoader;
import com.google.common.util.concurrent.ExecutionError;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.mojang.logging.LogUtils;
import java.util.function.LongSupplier;
import org.slf4j.Logger;

/**
 * Bons and Furious switch jei_typed_stack_cache (JEI 15.59.0.212, client; Guava 31.1-jre). No JEI code here.
 *
 * JEI's TypedItemStack.getIngredient() is CACHE.getUnchecked(this) on one static Guava cache built with
 * expireAfterAccess(1 s) and concurrencyLevel(1) whose loader calls createItemStackUncached(). TypedItemStack and its
 * three sealed subclasses do not override equals/hashCode, so the cache is keyed by instance identity. While JEI indexes
 * recipes nearly every call is the first one for a freshly built ingredient: a miss that inserts an entry, and every insert
 * walks the expiry queue to unlink the entries whose second has run out (3.7% of the load window's main thread).
 *
 * The same contract, kept per instance (a field on the TypedItemStack) instead of in the shared cache - Guava 31.1's
 * LocalCache.Segment.get / lockedGetOrLoad / loadSync / waitForLoadingValue and LocalLoadingCache.getUnchecked for this
 * configuration:
 *  - hit iff now - lastAccess < 1 s (Guava: isExpired is now - accessTime >= expireAfterAccessNanos, ticker =
 *    System.nanoTime()); a hit sets lastAccess = now (recordRead);
 *  - a miss loads; the access time is the clock after the load (storeLoadedValue); a concurrent caller for the same
 *    instance waits for that load and gets its value (then its own access time) or its failure - it never loads again;
 *  - loader failures: Error -> ExecutionError(cause), anything else -> UncheckedExecutionException(cause), for the loading
 *    thread and for every waiter alike (Segment.get wraps the ExecutionException's cause; getUnchecked wraps checked ones);
 *    an InterruptedException from the loader re-sets the interrupt flag; the failed load leaves nothing behind;
 *  - a loader returning null -> InvalidCacheLoadException("CacheLoader returned null for key " + key + "."), to the loader
 *    and to waiters, nothing stored;
 *  - the loading thread asking for the same instance again -> IllegalStateException from
 *    Preconditions.checkState(false, "Recursive load of: %s", key) (Guava's waitForLoadingValue under the entry lock),
 *    thrown raw at the inner call;
 *  - waiting ignores interrupts and re-sets the flag afterwards (Uninterruptibles.getUninterruptibly).
 * So callers get the same object within a second of the last access and a new one after, the same exceptions and the same
 * loader calls. The one difference is memory: Guava drops an expired value lazily on later cache writes; here the last
 * stack stays referenced by its ingredient until that ingredient is next used or collected (JEI's ingredient list: a few MB
 * while JEI keeps it). That is not observable: an expired value is never returned.
 */
public final class TypedStackCache {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.jeiTypedStackCache=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.jeiTypedStackCache", "true"));
    /** CacheBuilder.expireAfterAccess(Duration.ofSeconds(1L)). */
    public static final long EXPIRE_NANOS = 1_000_000_000L;
    /** Guava's Ticker.systemTicker(); harnesses replace it to compare against Guava with the same fake ticker. */
    public static LongSupplier ticker = System::nanoTime;
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Guards the install of a load marker (misses only; hits never lock). */
    private static final Object INSTALL = new Object();
    private static volatile boolean announced;

    private TypedStackCache() {
    }

    /** Implemented on TypedItemStack by the mixin: the per-instance slot and the uncached loader. */
    public interface Holder {
        Object bons$cacheState();

        void bons$cacheState(Object state);

        Object bons$loadUncached();
    }

    /** A loaded value and its last access time. */
    static final class Entry {
        final Object value;
        volatile long access;

        Entry(Object value, long access) {
            this.value = value;
            this.access = access;
        }
    }

    /** A load in progress; waiters block on it. */
    static final class Loading {
        final Thread owner;
        boolean done;
        Entry entry;
        Throwable failure;

        Loading(Thread owner) {
            this.owner = owner;
        }
    }

    /** CACHE.getUnchecked(key) for the cache configuration described above. */
    public static Object get(Holder key) {
        long now = ticker.getAsLong();
        Object s = key.bons$cacheState();
        if (s instanceof Entry e && now - e.access < EXPIRE_NANOS) {
            e.access = now;
            return e.value;
        }
        if (s instanceof Loading l) return await(key, l);
        Loading mine = null;
        Loading theirs = null;
        synchronized (INSTALL) {
            s = key.bons$cacheState();
            if (s instanceof Loading l) {
                theirs = l;
            } else {
                if (s instanceof Entry e) {
                    long again = ticker.getAsLong();
                    if (again - e.access < EXPIRE_NANOS) {
                        e.access = again;
                        return e.value;
                    }
                }
                mine = new Loading(Thread.currentThread());
                key.bons$cacheState(mine);
            }
        }
        if (theirs != null) return await(key, theirs);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: jei_typed_stack_cache keeps JEI's typed-ingredient stacks per ingredient (same one-second rule)");
        }
        return load(key, mine);
    }

    private static Object load(Holder key, Loading mine) {
        Object value;
        try {
            value = key.bons$loadUncached();
        } catch (Throwable t) {
            finish(key, mine, null, t);
            if (t instanceof InterruptedException) Thread.currentThread().interrupt();
            throw wrap(t);
        }
        if (value == null) {
            finish(key, mine, null, null);
            throw new CacheLoader.InvalidCacheLoadException("CacheLoader returned null for key " + key + ".");
        }
        Entry e = new Entry(value, ticker.getAsLong());
        finish(key, mine, e, null);
        return value;
    }

    private static void finish(Holder key, Loading mine, Entry entry, Throwable failure) {
        synchronized (INSTALL) {
            if (key.bons$cacheState() == mine) key.bons$cacheState(entry);
        }
        synchronized (mine) {
            mine.entry = entry;
            mine.failure = failure;
            mine.done = true;
            mine.notifyAll();
        }
    }

    private static Object await(Holder key, Loading l) {
        Preconditions.checkState(l.owner != Thread.currentThread(), "Recursive load of: %s", key);
        boolean interrupted = false;
        synchronized (l) {
            while (!l.done) {
                try {
                    l.wait();
                } catch (InterruptedException ie) {
                    interrupted = true;
                }
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
        if (l.failure != null) throw wrap(l.failure);
        if (l.entry == null) throw new CacheLoader.InvalidCacheLoadException("CacheLoader returned null for key " + key + ".");
        l.entry.access = ticker.getAsLong();
        return l.entry.value;
    }

    private static RuntimeException wrap(Throwable t) {
        if (t instanceof Error err) throw new ExecutionError(err);
        return new UncheckedExecutionException(t);
    }
}
