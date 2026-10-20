package bons.furious.patch.punchy;

import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch punchy_emf_trace_skip (Punchy 2.8g for Minecraft 1.20.1, client; our pack does not install Punchy,
 * the switch is for other packs, proven offline on the real classes).
 *
 * <p>Punchy wraps every LivingEntityRenderer.render in EmfEtfFreshAnimationsDebug.pushLivingEntityRender (render HEAD) and
 * popLivingEntityRender (render TAIL). With Entity Model Features / Entity Texture Features / CEM present, push builds a
 * RenderContext for every non-player living entity drawn, every frame: registry key and its text, the entity's display
 * name as a string, two class names, and a per-model part-name map behind a synchronized cache. pop takes it off a
 * thread-local ArrayDeque and removes the thread-local whenever the deque is empty, so the next push (and, without EMF,
 * every pop) creates a new ArrayDeque. The only consumers are recordResetPose / recordExplicitTransformApplication (no
 * caller anywhere in Punchy 2.8g) and pop's logging branch, which is empty in 2.8g.
 *
 * <p>With the switch on, push and pop return at once. What changes is Punchy's private debug state only (the context deque,
 * the part-name cache, the log throttle map), which nothing reads; the render itself, every value it draws and every call
 * it makes are unchanged. The guard fingerprints push, pop, both record methods and the class's method set, so a Punchy
 * whose trace does something (a non-empty log branch, a reader of the contexts) makes the switch step aside. The single
 * behavioural edge: an exception thrown inside push itself (from an entity's name or registry lookup) no longer reaches
 * the render call; no such exception exists for the classes tested.
 *
 * <p>-Dbons_and_furious.punchyEmfTraceSkip=false turns it off at runtime. SHADOW MODE for rigs:
 * -Dbons_and_furious.punchyEmfTraceSkip.shadow=true runs push and pop as stock, counts SHADOW_CHECKS (calls the switch would
 * have skipped) and SHADOW_MISMATCHES (calls of the two record methods, each of which would read the trace; expected 0 with
 * Punchy 2.8g; WARN for the first 20).
 */
public final class EmfTraceSkip {
    /** Runtime switch (the config switch acts when classes are transformed). */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.punchyEmfTraceSkip", "true"));
    /** Shadow mode (see the class comment). */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.punchyEmfTraceSkip.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static boolean announced;

    private EmfTraceSkip() {
    }

    /** HEAD of push / pop: true = return at once. */
    public static boolean skip() {
        if (!enabled) return false;
        if (SHADOW) {
            SHADOW_CHECKS.incrementAndGet();
            return false;
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: punchy_emf_trace_skip: Punchy's entity-model debug trace is skipped (nothing reads it)");
        }
        return true;
    }

    /** HEAD of the two record methods (shadow mode only): the trace was about to be read. */
    public static void recorded() {
        if (!SHADOW) return;
        long n = SHADOW_MISMATCHES.incrementAndGet();
        if (n <= 20) LOGGER.warn("Bons and Furious: punchy_emf_trace_skip SHADOW MISMATCH {}: Punchy recorded into its debug trace", n);
    }
}
