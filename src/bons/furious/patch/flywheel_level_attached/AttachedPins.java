package bons.furious.patch.flywheel_level_attached;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.mojang.logging.LogUtils;
import java.lang.ref.WeakReference;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;

/**
 * Bons and Furious switch flywheel_level_attached_weak_keys (Flywheel 1.0.5 inside Create 6.0.8, client only). [FIX] A
 * level that nothing else references any more is no longer kept in memory by Flywheel's per-level caches.
 *
 * Flywheel's LevelAttached keeps one value per level in a Guava LoadingCache with strong keys and values; entries leave
 * only through remove / invalidateLevel / reset. Its one user, VisualizationManagerImpl.MANAGERS, holds a visualization
 * manager per level, and the manager keeps its level (the final field level). Create 6.0.8 gives every client contraption
 * its own VirtualRenderWorld (ClientContraption$1, which also references its ClientContraption, the Contraption, the
 * contraption entity and through VirtualRenderWorld.level the real ClientLevel); setting up the contraption's block
 * entities there makes Flywheel create a manager for that virtual level, and nothing ever removes it: invalidateLevel
 * runs for the real level only, and resetAll only on a resource reload. So every contraption the client has seen stays
 * in memory with its entity, and after a dimension change the old ClientLevel (with its LocalPlayer) stays too (Create
 * #9858, AllTheLeaks #78). Flywheel's 1.20.1/dev branch switched the cache to weak keys (PR #320, idea text only). That
 * alone frees nothing here: a cache's values are strong, and a value that references its own key keeps that key
 * reachable (the WeakHashMap caveat); VisualizationManagerImpl does.
 *
 * Here each LevelAttached cache gets weak keys AND weak values, and the value is pinned on its key instead: an array field
 * that a mixin adds to Level (for any key that is not a Level, a strong side table, which is what the cache did before).
 * While the key is reachable from anywhere but the cache, its value is reachable through the key, so the cache always has
 * it: every lookup returns the same object, the factory runs once per key, removal runs the finalizer, in the same order,
 * on the same thread. Keys are compared by identity (weak keys), as Level and VirtualRenderWorld compare (neither
 * overrides equals or hashCode, so their hash, bucket and iteration order are unchanged). Once the key is unreachable
 * from outside, key and value are unreachable together, the GC clears both references at once, and Guava drops the entry
 * (cause COLLECTED, key and value null). Its finalizer is not called (the value no longer exists); Flywheel 1.0.5 never
 * finalized such an entry either before the next resource reload, and the managers of virtual levels never create an
 * engine (only ClientLevel managers are ticked, rendered and lit), so there is nothing to free.
 *
 * Runtime switch -Dbons_and_furious.flywheelLevelAttachedWeakKeys=false (read when a LevelAttached is built): the cache is
 * built exactly as Flywheel builds it. -Dbons_and_furious.flywheelLevelAttachedWeakKeys.shadow=true (verification runs):
 * every key is also held strongly in a side map with a weak reference to its value, and every load checks that the key
 * was never given a value that was lost while the key lived (SHADOW_CHECKS per load, SHADOW_MISMATCHES, the first 20
 * logged; expect 0). Shadow runs keep their levels, by design.
 */
public final class AttachedPins {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.flywheelLevelAttachedWeakKeys", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.flywheelLevelAttachedWeakKeys.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Values loaded, entries removed explicitly, entries released because key and value were collected (tests, rigs). */
    public static final AtomicLong LOADED = new AtomicLong(), REMOVED = new AtomicLong(), RELEASED = new AtomicLong();
    private static final Object LOCK = new Object();
    /** Keys that are not a Level: their pins, strongly (the cache held them strongly before). Guarded by LOCK. */
    private static final Map<Object, Object[]> OTHER_KEYS = new IdentityHashMap<>();
    /** SHADOW only: per cache, every key it ever loaded -> its value, weakly. Guarded by LOCK. */
    private static final Map<Token, Map<Object, WeakReference<Object>>> SHADOW_SEEN = new IdentityHashMap<>();
    private static volatile boolean announced, announcedRelease;

    private AttachedPins() {
    }

    /** The identity of one LevelAttached cache in the pins of its keys (made while its constructor runs). */
    public static final class Token {
        private final RemovalListener<Object, Object> flywheel;

        Token(RemovalListener<Object, Object> flywheel) {
            this.flywheel = flywheel;
        }
    }

    /** Duck interface the Level mixin implements: the pins of a level (pairs token, value), copy-on-write under LOCK. */
    public interface Holder {
        Object[] bons$attachedPins();

        void bons$setAttachedPins(Object[] pins);
    }

    /**
     * The token of the cache whose constructor is between its removalListener and its build call on this thread, with
     * that constructor's CacheBuilder (in between, LevelAttached's constructor only creates its loader).
     */
    private static final ThreadLocal<Object[]> PENDING = new ThreadLocal<>();

    /**
     * LevelAttached.<init>, around CacheBuilder.removalListener: with the switch on, Flywheel's listener is wrapped and a
     * token is made for this cache and handed to the build call; off, nothing changes.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static RemovalListener listener(CacheBuilder builder, RemovalListener flywheel) {
        if (!enabled) {
            PENDING.remove();
            return flywheel;
        }
        Token token = new Token(flywheel);
        PENDING.set(new Object[]{builder, token});
        return (RemovalListener<Object, Object>) n -> onRemoval(token, n);
    }

    /** LevelAttached.<init>, at the build call: the token the removalListener call made for this same builder, or null. */
    public static Token token(CacheBuilder<?, ?> builder) {
        Object[] pending = PENDING.get();
        PENDING.remove();
        return pending != null && pending[0] == builder ? (Token) pending[1] : null;
    }

    /**
     * LevelAttached.<init>, before CacheBuilder.build: when this cache has a token, weak keys and weak values (pinned on
     * the key by the loader below); without one the builder is left exactly as Flywheel made it.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static CacheBuilder builder(CacheBuilder builder, Token token) {
        if (token == null) return builder;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: flywheel_level_attached_weak_keys applies (Flywheel's per-level caches keep a level's entry only while the level is "
                    + "reachable from outside the cache){}", SHADOW ? " - shadow verification on" : "");
        }
        // A Flywheel that already asks for weak keys (its 1.20.1/dev change, accepted by this switch's guard as an
        // alternative build) has set the key strength; Guava refuses to set it twice. Weak values the same way.
        try {
            builder.weakKeys();
        } catch (IllegalStateException alreadySet) {
            // keys already weak (Guava allows no other key strength)
        }
        try {
            builder.weakValues();
        } catch (IllegalStateException alreadySet) {
            // value strength already chosen by Flywheel: the value is pinned on its key either way
        }
        return builder;
    }

    /** LevelAttached.<init>, the loader passed to CacheBuilder.build: Flywheel's own loader, its value pinned on the key. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static CacheLoader loader(CacheLoader flywheel, Token token) {
        return token == null ? flywheel : new PinningLoader(flywheel, token);
    }

    static final class PinningLoader extends CacheLoader<Object, Object> {
        private final CacheLoader<Object, Object> flywheel;
        private final Token token;

        PinningLoader(CacheLoader<Object, Object> flywheel, Token token) {
            this.flywheel = flywheel;
            this.token = token;
        }

        @Override
        public Object load(Object key) throws Exception {
            Object value = flywheel.load(key);      // Flywheel's loader: factory.apply(key); exceptions propagate unchanged
            if (value != null) {                     // null: Guava throws InvalidCacheLoadException, as before
                pin(key, token, value);
                LOADED.incrementAndGet();
                if (SHADOW) shadowLoad(token, key, value);
            }
            return value;
        }
    }

    private static void onRemoval(Token token, RemovalNotification<Object, Object> n) {
        Object value = n.getValue();
        if (n.getCause() == RemovalCause.COLLECTED || value == null) {
            // key and value were unreachable together (the value is pinned on its key): nothing left to finalize
            long r = RELEASED.incrementAndGet();
            if (!announcedRelease) {
                announcedRelease = true;
                LOGGER.info("Bons and Furious: flywheel_level_attached_weak_keys released the first Flywheel level entry whose level was no longer "
                        + "reachable ({} so far)", r);
            }
            return;
        }
        Object key = n.getKey();
        if (key != null) {
            unpin(key, token, value);
            if (SHADOW) shadowRemove(token, key);
        }
        REMOVED.incrementAndGet();
        token.flywheel.onRemoval(n);                 // Flywheel's listener: finalizer.accept(value)
    }

    static void pin(Object key, Token token, Object value) {
        synchronized (LOCK) {
            Object[] old = key instanceof Holder h ? h.bons$attachedPins() : OTHER_KEYS.get(key);
            Object[] pins;
            int at = indexOf(old, token);
            if (at >= 0) {
                pins = old.clone();
                pins[at + 1] = value;
            } else {
                int n = old == null ? 0 : old.length;
                pins = new Object[n + 2];
                if (n > 0) System.arraycopy(old, 0, pins, 0, n);
                pins[n] = token;
                pins[n + 1] = value;
            }
            store(key, pins);
        }
    }

    static void unpin(Object key, Token token, Object value) {
        synchronized (LOCK) {
            Object[] old = key instanceof Holder h ? h.bons$attachedPins() : OTHER_KEYS.get(key);
            int at = indexOf(old, token);
            if (at < 0 || old[at + 1] != value) return;   // another value was pinned since (a reload after a removal)
            Object[] pins = null;
            if (old.length > 2) {
                pins = new Object[old.length - 2];
                System.arraycopy(old, 0, pins, 0, at);
                System.arraycopy(old, at + 2, pins, at, old.length - at - 2);
            }
            store(key, pins);
        }
    }

    private static void store(Object key, Object[] pins) {
        if (key instanceof Holder h) h.bons$setAttachedPins(pins);
        else if (pins == null) OTHER_KEYS.remove(key);
        else OTHER_KEYS.put(key, pins);
    }

    private static int indexOf(Object[] pins, Token token) {
        if (pins != null) for (int i = 0; i < pins.length; i += 2) if (pins[i] == token) return i;
        return -1;
    }

    /** For tests: the value pinned on a key for a cache, or null. */
    public static Object pinned(Object key, Token token) {
        synchronized (LOCK) {
            Object[] pins = key instanceof Holder h ? h.bons$attachedPins() : OTHER_KEYS.get(key);
            int at = indexOf(pins, token);
            return at < 0 ? null : pins[at + 1];
        }
    }

    private static void shadowLoad(Token token, Object key, Object value) {
        SHADOW_CHECKS.incrementAndGet();
        WeakReference<Object> before;
        synchronized (LOCK) {
            before = SHADOW_SEEN.computeIfAbsent(token, t -> new IdentityHashMap<>()).put(key, new WeakReference<>(value));
        }
        if (before != null) {
            long n = SHADOW_MISMATCHES.incrementAndGet();
            if (n <= 20)
                LOGGER.warn("Bons and Furious: flywheel_level_attached_weak_keys shadow mismatch #{}: {} was loaded again for a live key {} (the earlier "
                        + "value {} was lost without a removal)", n, value, key, before.get());
        }
    }

    private static void shadowRemove(Token token, Object key) {
        synchronized (LOCK) {
            Map<Object, WeakReference<Object>> seen = SHADOW_SEEN.get(token);
            if (seen != null) seen.remove(key);
        }
    }
}
