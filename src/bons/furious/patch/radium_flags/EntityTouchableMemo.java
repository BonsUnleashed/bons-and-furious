package bons.furious.patch.radium_flags;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch radium_entity_touchable_memo (Radium Re-Reforged 0.14.3, both sides). No Radium code here.
 *
 * Radium computes a set of flags for every block state when the state's cache is built. Its ENTITY_TOUCHABLE flag
 * (common/block/BlockStateFlags$5) asks ReflectionUtil.hasMethodOverride whether the state's block class overrides
 * entityInside: getDeclaredMethod on each class from the block's class up to BlockBehaviour, a NoSuchMethodException for
 * every level without the method, and on a dedicated server a NoClassDefFoundError / RuntimeException (logged as
 * "Radium Class Analysis Error", ~38,800 lines per boot in this pack) for blocks whose method signatures name client-only
 * classes. It does this per STATE, although the answer depends only on the block's CLASS: test(state) reads nothing but
 * state.getBlock().getClass() and two constants (BlockBehaviour.class and the SRG name m_7892_ with its four parameter
 * types, set in the constructor). A pack has ~40,000 states and a few thousand block classes.
 *
 * The first state of each block class runs Radium's code; its answer is kept per class and every later state of that
 * class gets the same boolean. An exception from Radium's code is not remembered (it propagates as before and the next
 * state asks again). Not identical, documented: the "Radium Class Analysis Error" warning is written once per class
 * instead of once per state. Classes are never unloaded in a running game, so the map holds no stale entry.
 */
public final class EntityTouchableMemo {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.radiumEntityTouchableMemo=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.radiumEntityTouchableMemo", "true"));
    /** Shadow mode for rigs: every remembered answer is also computed by Radium's code and compared (WARN on a difference). */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.radiumEntityTouchableMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Counters (read by probes): states answered from the map, classes computed by Radium's code. */
    public static final AtomicLong HITS = new AtomicLong(), CLASSES = new AtomicLong();
    private static final ConcurrentHashMap<Class<?>, Boolean> BY_CLASS = new ConcurrentHashMap<>();
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private EntityTouchableMemo() {
    }

    /** The remembered answer for this block class, or null when Radium's code has not answered for it yet. */
    public static Boolean known(Class<?> blockClass) {
        Boolean v = BY_CLASS.get(blockClass);
        if (v != null) HITS.incrementAndGet();
        return v;
    }

    /** Radium's answer for the first state of this class. */
    public static void remember(Class<?> blockClass, boolean answer) {
        if (BY_CLASS.putIfAbsent(blockClass, answer) == null) {
            CLASSES.incrementAndGet();
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: radium_entity_touchable_memo answers Radium's entity-touchable flag once per block class");
            }
        }
    }

    public static void shadow(Class<?> blockClass, boolean remembered, boolean original) {
        SHADOW_CHECKS.incrementAndGet();
        if (remembered != original && SHADOW_MISMATCHES.incrementAndGet() <= 20)
            LOGGER.warn("Bons and Furious: radium_entity_touchable_memo shadow check: {} remembered {} but Radium answered {}",
                    blockClass.getName(), remembered, original);
    }
}
