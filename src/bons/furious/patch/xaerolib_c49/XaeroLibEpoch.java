package bons.furious.patch.xaerolib_c49;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Bons and Furious switches xaerolib_current_profile_memo and xaerolib_enforcement_read_memo (XaeroLib 1.7.3, nested in
 * Xaero's Minimap 26.6.0 and Xaero's World Map 1.47.0, tested build; client; since 1.0.49): the change counter both memos
 * are validated against. Idea: Collections Of Optimizations (xaerolib.cacheConfigProfile, xaerolib.cacheEnforcementCheck),
 * idea text only.
 *
 * Every XaeroLib method that can change an input of the two memoised answers reports here, through the tracking mixins
 * of xaerolib_current_profile_memo (XlConfigTrackMixin and the manager mixins): Config.set / reset /
 * addFailedSerializedValue (every config value and failed-value change goes through them: the value maps are created
 * inside Config$Builder.build and private to Config), SingleConfigManager.setConfig / setRedirectorManager,
 * ConfigProfileManager.add / remove / reset, and the redirector registrations. enter() runs right before the method's first
 * change (after its argument checks, which throw before changing anything), exit() at its normal return. The only Configs
 * left out are the redirector managers' cache configs (quiet), which XaeroLib writes on many reads and which feed neither
 * memo.
 *
 * One atomic word: the changes finished so far (the epoch, upper bits) and the changes in progress (lower 20 bits). enter()
 * adds one change in progress; exit() finishes it (epoch + 1, in progress - 1) in the same atomic add. A memo keeps the word
 * it was computed at, taken while no change was in progress and unchanged across the computation; it is valid while the
 * word is still exactly that (no change started or finished since). A change that throws after enter() leaves its "in
 * progress" count: both memos then stand down for the rest of the session (the stock answer every time), never a stale
 * answer.
 */
public final class XaeroLibEpoch {
    public static final AtomicLong STATE = new AtomicLong();
    private static final long PENDING_ONE = 1L, EPOCH_ONE = 1L << 20, PENDING_MASK = EPOCH_ONE - 1L;

    private XaeroLibEpoch() {
    }

    public static void enter() {
        STATE.getAndAdd(PENDING_ONE);
    }

    public static void exit() {
        STATE.getAndAdd(EPOCH_ONE - PENDING_ONE);
    }

    /** The word to keep with a memo computed from now on, or -1 when no memo may be kept (a change is in progress). */
    public static long stable() {
        long s = STATE.get();
        return (s & PENDING_MASK) == 0L ? s : -1L;
    }

    /** True while nothing changed since the word s was taken (s came from stable()). */
    public static boolean holds(long s) {
        return STATE.get() == s;
    }
}
