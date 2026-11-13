package bons.furious.patch.subtleeffects_drain_c49;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch subtleeffects_ticker_removal_drain (Subtle Effects 1.15.1 for NeoForge 1.21.1, All Rights
 * Reserved; client side). Ours; no Subtle Effects code is carried.
 *
 * What Subtle Effects does. TickerManager.tick() collects every ticker that reports isRemoved() into REMOVE_QUEUE and then
 * runs REMOVE_QUEUE.forEach(TICKERS::remove): one ArrayList.remove(Object) per removed ticker, each a scan from the start of
 * the list plus an array copy of everything behind the hit. With a few thousand tickers and tens to hundreds of removals
 * per tick (farms, fights, many effect sources, chunks unloading) that is O(removals x tickers) work on the client thread.
 *
 * What the switch does. Each queued ticker's counter (our field, SeTickerFlag) goes up once per queue entry; one
 * ArrayList.removeIf pass over TICKERS drops an element while its object's counter is above zero and counts it down; the
 * counters are zeroed again afterwards; forEach then gets a consumer that does nothing.
 *
 * Why the result is identical. ArrayList.remove(o) removes the first element e with o.equals(e); for a ticker class that
 * does not override equals that is the first occurrence of o itself (identity), and removing one ticker never changes which
 * occurrences of another ticker come first. So the queue's k entries of o drop exactly o's first min(k, occurrences)
 * occurrences and keep every other element in its order. ArrayList.removeIf tests every element once, in index order, and
 * keeps the survivors in order: with the counter it drops exactly those first occurrences. The queue alone decides what is
 * removed, so it does not matter what isRemoved() answers now. The pass runs only when both lists are plain
 * java.util.ArrayList and every queued ticker is non-null and of a class that does not override equals (one ClassValue
 * check per class); otherwise, and for fewer than MIN_BATCH removals (where the stock loop is as fast), Subtle Effects' own
 * consumer runs. Not observable: ArrayList's modification counter moves once instead of once per removal (no iteration of
 * TICKERS is open at that point).
 *
 * -Dbons_and_furious.subtleeffectsTickerRemovalDrain=false switches it off at run time.
 * -Dbons_and_furious.subtleeffectsTickerRemovalDrain.shadow=true: the stock removal also runs on a copy and the two lists
 * are compared (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20).
 */
public final class TickerDrain {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.subtleeffectsTickerRemovalDrain", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.subtleeffectsTickerRemovalDrain.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /**
     * Below this many removals per tick the stock loop runs: ArrayList.remove(Object) is a tight identity scan plus one
     * array copy, and the one-pass removal only wins from about 24 removals per tick on (measured on Forge 1.20.1 at 1,000
     * and 3,000 tickers; see the group notes).
     */
    public static final int MIN_BATCH = 32;
    /** How many removal batches the pass handled (client thread; read by tests and debugging). */
    public static long passes;
    /** The consumer forEach gets after a pass: the removals are already done. */
    public static final Consumer<Object> DONE = t -> {};
    private static final Predicate<Object> TAKE = e -> e instanceof SeTickerFlag f && f.bons$seDrainC49Take();
    private static volatile boolean announced;

    /** True for a ticker class that does not override equals (so ArrayList.remove(Object) is by identity). */
    private static final ClassValue<Boolean> PLAIN = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            // the JVM's resolution of that one method (bons.furious.guard.Declared), not Class.getMethod: getMethod resolves the types of
            // every public method of the class and throws when one names a class that is not installed (bug sweep 2026-11-12). A ticker
            // class that cannot be asked leaves the batch to Subtle Effects' own loop.
            return bons.furious.guard.Declared.inherits(type, Object.class, "equals", java.lang.invoke.MethodType.methodType(boolean.class, Object.class));
        }
    };

    private TickerDrain() {}

    /**
     * Removes from {@code tickers} what {@code queue.forEach(tickers::remove)} would remove, in one pass. Returns false when
     * it did nothing and the caller must run the stock loop.
     */
    @SuppressWarnings("unchecked")
    public static boolean drain(List<?> tickers, List<?> queue) {
        int k = queue.size();
        if (k < MIN_BATCH || tickers.getClass() != ArrayList.class || queue.getClass() != ArrayList.class || tickers == queue) return false;
        Object[] q = queue.toArray();
        Class<?> last = null;
        for (Object t : q) {
            if (!(t instanceof SeTickerFlag)) return false;
            Class<?> c = t.getClass();
            if (c != last) {
                if (!PLAIN.get(c)) return false;
                last = c;
            }
        }
        List<Object> expected = SHADOW ? stockCopy(tickers, q) : null;
        for (Object t : q) ((SeTickerFlag) t).bons$seDrainC49Queue();
        try {
            ((List<Object>) tickers).removeIf(TAKE);
        } finally {
            for (Object t : q) ((SeTickerFlag) t).bons$seDrainC49Clear();
        }
        passes++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: subtleeffects_ticker_removal_drain applies (Subtle Effects removes finished tickers in one pass){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        if (expected != null) shadow(expected, (List<Object>) tickers);
        return true;
    }

    private static List<Object> stockCopy(List<?> tickers, Object[] queue) {
        List<Object> copy = new ArrayList<>(tickers);
        for (Object t : queue) copy.remove(t);
        return copy;
    }

    private static void shadow(List<Object> expected, List<Object> ours) {
        SHADOW_CHECKS.incrementAndGet();
        boolean same = expected.size() == ours.size();
        for (int i = 0; same && i < expected.size(); i++) same = expected.get(i) == ours.get(i);
        if (!same) {
            long m = SHADOW_MISMATCHES.incrementAndGet();
            if (m <= 20) LOGGER.warn("Bons and Furious: subtleeffects_ticker_removal_drain shadow mismatch #{}: stock keeps {} tickers, the pass {}", m, expected.size(), ours.size());
        }
    }
}
