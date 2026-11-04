package bons.furious.patch.manybows_coo49;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch manybows_origins_quiver_gate (Too Many Bows 4.1.2, both sides).
 *
 * Too Many Bows hooks ItemStack.shrink (its ItemStackQuiverSyncMixin, TAIL) and hands every shrink of every stack in the
 * game, on every thread, to OriginsQuiverCompat.onTrackedStackShrunk, which takes the lock of one static
 * Collections.synchronizedMap around a WeakHashMap and removes the stack from it (with WeakHashMap's stale-entry purge).
 * The map gets entries only in findProjectile, when an Origins (Apoli) inventory power holds an arrow; without Apoli, or
 * before any such arrow was used, it is empty and every remove finds nothing. Player.getProjectile also reaches
 * getInventoryPowers, which calls the synchronized initialize() on every call although it does its work only once.
 *
 * The switch notes, just before findProjectile's only put, that the map has ever had an entry (a flag that is never
 * cleared); until then onTrackedStackShrunk's remove is skipped: on an empty map it returns null, as the skipped call
 * would. Once initialize() has completed once, getInventoryPowers skips the synchronized call (it would return at once);
 * the fields initialize() set are published by that completion flag. Identical results for every call, on every thread.
 * Runtime flag -Dbons_and_furious.manybowsOriginsQuiverGate=false. Too Many Bows is All Rights Reserved: no code of it is
 * carried (the hooks only skip its own calls).
 */
public final class QuiverGate {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.manybowsOriginsQuiverGate", "true"));
    /** True once findProjectile is about to put a stack into the tracked map (never cleared). */
    public static volatile boolean everTracked;
    /** True once initialize() has returned (its fields are set by then). */
    public static volatile boolean initDone;
    private static volatile boolean announced;

    private QuiverGate() {
    }

    /** findProjectile, right before its put. */
    public static void tracked() {
        everTracked = true;
    }

    /** onTrackedStackShrunk: true when its remove would search a map that has never had an entry. */
    public static boolean skipRemove() {
        if (!enabled || everTracked) return false;
        if (!announced) announce();
        return true;
    }

    /** getInventoryPowers: true when initialize() has completed before (the call would return at once). */
    public static boolean skipInitialize() {
        return enabled && initDone;
    }

    /** getInventoryPowers, after initialize() returned. */
    public static void initialized() {
        initDone = true;
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: manybows_origins_quiver_gate: Too Many Bows skips its Origins quiver bookkeeping while no quiver arrow is tracked");
    }
}
