package bons.furious.patch.forge_listener_guard;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraftforge.eventbus.EventBus;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.IEventListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch forge_listener_prologue_guard (Forge 1.20.1-47.4.28 with eventbus 6.2.33; both sides).
 * Idea: Collections Of Optimizations (vanilla.guardedEvents), idea text only.
 *
 * What Forge does. Every tick phase (client, server, level, player, render), every RenderLevelStageEvent stage and every
 * living entity's tick is posted to MinecraftForge.EVENT_BUS, and EventBus.post calls every listener of that event in
 * turn. Most @SubscribeEvent listeners open with a test such as "if (event.phase != TickEvent.Phase.END) return;",
 * "if (event.getStage() != Stage.AFTER_PARTICLES) return;" or MCreator's "if (entity instanceof A) {...} if (entity
 * instanceof B) {...}" and return at once for most events, after two megamorphic calls (the ASMEventHandler and its
 * generated wrapper).
 *
 * What the switch does. The twelve Forge methods that post those events (ForgeHooksClient.dispatchRenderStage's
 * RenderType overload calls its Stage overload) hand the post to {@link #post} (MixinExtras @WrapOperation of their
 * IEventBus.post call). For the Forge bus it runs EventBus.post's own loop (eventbus 6.2.33, LGPL-2.1; this is a modified
 * copy of it): the shutdown test (a volatile read), the dispatch type check, the stock listener array of the event's
 * ListenerList for the bus id, the phase-marker skip when phases are not tracked, every listener's invoke in array
 * order, the bus's exception handler with the same array and index followed by the rethrow, and event.isCanceled() as
 * the result. The one difference: a listener whose opening instructions are proven to return at once on this event
 * (GuardTable / PrologueAnalyser: pure reads of final event fields, verified getters and constants, ending in the
 * method's void return) is not called. Calling it would have done nothing: the same path, the same reads, no effect. Its
 * first such call happens for real (the class's static initialiser, the resolution of every class, field and method its
 * test names), and only after that call returned normally is that test armed (per early-return path; a path with an
 * instance-of test only after a call with a non-null subject, because instanceof resolves its class only then).
 * Never skipped: phase markers, lambda listeners, generic-filtered and other-factory handlers, classes any mixin or
 * coremod changed (ModLauncher's audit trail), methods the analysis does not fully understand, and every listener at a
 * post site another mod also changes (ListenerGuardPlugin).
 *
 * Identical for every input: listeners run in the same order with the same event; exceptions reach the same handler
 * with the same index and propagate; the result is the same; a skipped listener's own code would only have read final
 * fields and returned. The call stack of a listener has this class's frame instead of EventBus.post's two frames.
 *
 * -Dbons_and_furious.forgeListenerPrologueGuard=false switches it off at run time (every post then goes through the
 * original operation). -Dbons_and_furious.forgeListenerPrologueGuard.shadow=true (verification runs only): nothing is
 * skipped; each call that would have been skipped is counted (SHADOW_CHECKS) and checked against the listener's own
 * opening instructions run as a probe (PrologueProbe) before the listener is called as usual: a probe that does not end
 * in the early return is a mismatch (SHADOW_MISMATCHES, WARN for the first 20). SHADOW_UNPROBED counts checks without a
 * probe. One INFO line with the three counters at 1,000, 10,000, 100,000 ... checks.
 */
public final class GuardedDispatch {
    static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.forgeListenerPrologueGuard", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.forgeListenerPrologueGuard.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong(), SHADOW_UNPROBED = new AtomicLong();
    /** Diagnostics (shadow runs and harnesses only; never counted on the normal path): skips taken. */
    public static final AtomicLong SKIPS = new AtomicLong();

    /** The bus whose posts are replayed (MinecraftForge.EVENT_BUS), null until accepted. */
    private static volatile IEventBus bus;
    private static volatile boolean initDone;
    private static BusInternals BI;
    /** Harness only: the bus that stands for MinecraftForge.EVENT_BUS in a plain JVM. */
    static volatile java.util.function.Supplier<IEventBus> testBus;
    /** Harness only: count skips (SKIPS). */
    static boolean countSkips;
    /** per site: the last array seen there and its table (immutable pair, published by a plain write) */
    private static final Slot[] SLOTS = new Slot[Sites.COUNT];
    private static final Object LOCK = new Object();
    private static final java.util.Map<IEventListener[], GuardTable> TABLES = new java.util.WeakHashMap<>();
    private static volatile boolean announced;

    /**
     * Since 1.0.49.2 the array is held weakly: eventbus drops its cached array at once when a listener is (un)registered, and a
     * site that stops posting (a client's level, player, living, server and render-stage sites on the title screen) must not
     * keep the listeners of its last array.
     */
    private record Slot(java.lang.ref.WeakReference<IEventListener[]> array, GuardTable table) {}

    private GuardedDispatch() {}

    /** Harness only (plain JVM): replay posts on this bus instead of MinecraftForge.EVENT_BUS. */
    public static synchronized void harnessBus(IEventBus b) {
        testBus = () -> b;
        bus = null;
        initDone = false;
        BI = null;
        enabled = true;
        for (int i = 0; i < SLOTS.length; i++) SLOTS[i] = null;
        synchronized (LOCK) {
            TABLES.clear();
        }
        GuardTable.resetMemo();
        ListenerCensus.reset();
    }

    /** The wrapped MinecraftForge.EVENT_BUS.post(event) of one post site. */
    public static boolean post(int site, IEventBus b, Event event, Operation<Boolean> original) {
        if (!enabled || Sites.FOREIGN[site] || b != bus && !ready(b)) return original.call(b, event);
        return dispatch(site, (EventBus) b, event);
    }

    /** First call: checks the bus and eventbus' shape and resolves the bus's fields. False = always stock. */
    static synchronized boolean ready(IEventBus b) {
        if (b == bus) return true;
        if (initDone) return false;
        initDone = true;
        String[] why = new String[1];
        IEventBus forge = BusInternals.forgeBus();
        BusInternals bi = b == forge ? BusInternals.of(b, why) : null;
        if (b != forge) why[0] = "posted to another bus";
        if (bi == null) {
            enabled = false;
            LOGGER.warn("Bons and Furious: forge_listener_prologue_guard stays off ({}); every listener is called as shipped", why[0]);
            return false;
        }
        BI = bi;
        bus = b;
        return true;
    }

    /** eventbus 6.2.33 EventBus.post(event, IEventListener::invoke), except the proven early returns (see the class comment). */
    static boolean dispatch(int site, EventBus b, Event event) {
        BusInternals bi = BI;
        if ((boolean) BusInternals.SHUTDOWN.getVolatile(b)) return false;
        if (bi.checkTypes && !bi.baseType.isInstance(event)) {
            throw new IllegalArgumentException("Cannot post event of type " + event.getClass().getSimpleName() + " to this event. Must match type: "
                    + bi.baseType.getSimpleName());
        }
        IEventListener[] listeners = event.getListenerList().getListeners(bi.busId);
        GuardTable table = table(site, listeners, event, bi);
        GuardTable.Compiled[] guards = table.guards;
        // This post's signature (the facts every test of the table reads: phase, side, type, level side, stage, the
        // subject's class) selects precomputed skip sets, so each listener costs one bit test. If the signature cannot be
        // read, nothing is skipped in this post.
        long[] skip = null, pend = null;
        GuardTable.Holder holder = null;
        int ix = -1;
        if (table.guarded > 0) {
            try {
                ix = table.index(event);
                if (ix >= 0) {
                    holder = table.holder(event);
                    GuardTable.Sets s = table.sets(holder, ix, event);
                    skip = s.skip;
                    pend = s.pend;
                }
            } catch (Throwable t) {
                skip = pend = null;
            }
        }
        boolean trackPhases = bi.trackPhases, stale = false;
        int index = 0;
        try {
            for (index = 0; index < listeners.length; ++index) {
                IEventListener l = listeners[index];
                if (!trackPhases && l.getClass() == EventPriority.class) continue;
                if (skip != null) {
                    int w = index >>> 6;
                    long bit = 1L << index;
                    if ((skip[w] & bit) != 0L) {
                        if (!SHADOW) {
                            if (countSkips) SKIPS.incrementAndGet();
                            continue;
                        }
                        GuardTable.Compiled g = guards[index];
                        int c = g.match(event, holder.bits);
                        if (c >= 0) shadow(g.g, l, event);
                    } else if ((pend[w] & bit) != 0L) {
                        // this post's test holds but has not been armed: call it for real, arm it if it returned
                        GuardTable.Compiled g = guards[index];
                        int c = g.match(event, holder.bits);
                        if (c >= 0) {
                            GuardTable.ListenerGuard lg = g.g;
                            if (lg.armed[c]) {
                                stale = true;   // armed meanwhile (another thread, another signature): refresh this signature
                                if (!SHADOW) continue;
                                shadow(lg, l, event);
                            } else {
                                boolean entered = !lg.cancelCheck || !(event.isCanceled() && !lg.receiveCanceled);
                                l.invoke(event);
                                if (entered && (holder.subject || !g.inst[c])) {
                                    arm(lg, c);
                                    stale = true;
                                }
                                continue;
                            }
                        }
                    }
                }
                l.invoke(event);
            }
        } catch (Throwable t) {
            bi.handler.handleException(b, event, listeners, index, t);
            throw GuardedDispatch.<RuntimeException>sneaky(t);
        }
        if (stale) table.refresh(holder, ix, event);
        return event.isCanceled();
    }

    private static void arm(GuardTable.ListenerGuard lg, int c) {
        lg.armed[c] = true;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: forge_listener_prologue_guard applies (Forge event listeners that return at once are no longer called; first: {}){}",
                    lg.name, SHADOW ? " - shadow verification on" : "");
        }
    }

    /** Shadow mode: the call this guard would skip, checked against the listener's own opening instructions. */
    private static void shadow(GuardTable.ListenerGuard lg, IEventListener l, Event event) {
        long n = SHADOW_CHECKS.incrementAndGet();
        if (n >= 1000 && isPowerOfTen(n)) {
            LOGGER.info("Bons and Furious: forge_listener_prologue_guard shadow: {} checks, {} mismatches, {} without a probe", n,
                    SHADOW_MISMATCHES.get(), SHADOW_UNPROBED.get());
        }
        java.lang.invoke.MethodHandle p = PrologueProbe.probeFor(lg);
        if (p == null) {
            SHADOW_UNPROBED.incrementAndGet();
            return;
        }
        boolean early;
        try {
            early = PrologueProbe.run(p, lg, l, event);
        } catch (Throwable t) {
            early = false;
        }
        if (!early) {
            long m = SHADOW_MISMATCHES.incrementAndGet();
            if (m <= 20) LOGGER.warn("Bons and Furious: forge_listener_prologue_guard shadow mismatch #{}: {} would have been skipped for {} but its own code does not return at once",
                    m, lg.name, event.getClass().getSimpleName());
        }
    }

    private static boolean isPowerOfTen(long n) {
        while (n >= 10 && n % 10 == 0) n /= 10;
        return n == 1;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> T sneaky(Throwable t) throws T {
        throw (T) t;
    }

    /** The table of this listener array (per-site one-entry cache, then a weak map; built once per array and event class). */
    private static GuardTable table(int site, IEventListener[] listeners, Event event, BusInternals bi) {
        Slot s = SLOTS[site];
        if (s != null && s.array.get() == listeners && s.table.eventClass == event.getClass()) return s.table;
        GuardTable t;
        synchronized (LOCK) {
            t = TABLES.get(listeners);
            if (t == null || t.eventClass != event.getClass()) {
                t = GuardTable.build(listeners, event, bi.factory);
                TABLES.put(listeners, t);
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Bons and Furious: forge_listener_prologue_guard: {} ({} listeners, {} guarded):\n  {}", event.getClass().getName(),
                            listeners.length, t.guarded, String.join("\n  ", t.report));
                }
            }
        }
        SLOTS[site] = new Slot(new java.lang.ref.WeakReference<>(listeners), t);
        return t;
    }

    /** Harness: the table the dispatch would use for this event (built if needed). */
    public static java.util.List<String> report(Event event) {
        BusInternals bi = BI;
        IEventListener[] listeners = event.getListenerList().getListeners(bi.busId);
        synchronized (LOCK) {
            GuardTable t = TABLES.get(listeners);
            if (t == null || t.eventClass != event.getClass()) {
                t = GuardTable.build(listeners, event, bi.factory);
                TABLES.put(listeners, t);
            }
            return t.report;
        }
    }

    /**
     * Harness: for every guard of the event's current table whose clause holds on this event: {"early" | "runs" |
     * "unprobed", listener name, "armed" | "unarmed"} from its probe (the shadow check, without skipping anything).
     */
    static java.util.List<String[]> probeAll(Event event) {
        BusInternals bi = BI;
        IEventListener[] listeners = event.getListenerList().getListeners(bi.busId);
        GuardTable t;
        synchronized (LOCK) {
            t = TABLES.get(listeners);
        }
        java.util.List<String[]> out = new java.util.ArrayList<>();
        if (t == null || t.eventClass != event.getClass()) return out;
        long bits = 0L;
        if (t.profile != null) {
            Object s = EventFacts.read(t.subjectFact, event);
            bits = s == null ? 0L : t.profile.get(s.getClass())[0];
        }
        for (int i = 0; i < t.guards.length; i++) {
            GuardTable.Compiled g = t.guards[i];
            if (g == null) continue;
            int c = g.match(event, bits);
            if (c < 0) continue;
            java.lang.invoke.MethodHandle p = PrologueProbe.probeFor(g.g);
            String verdict;
            if (p == null) verdict = "unprobed";
            else {
                boolean early;
                try {
                    early = PrologueProbe.run(p, g.g, listeners[i], event);
                } catch (Throwable e) {
                    early = false;
                }
                verdict = early ? "early" : "runs";
            }
            out.add(new String[] {verdict, g.g.name, g.g.armed[c] ? "armed" : "unarmed"});
        }
        return out;
    }

    /**
     * Harness: the precomputed skip sets of this event's signature against the direct tests: a skip bit without a holding
     * armed test, a pending bit without a holding test, or a holding test with neither bit is a mismatch (an armed test
     * may still show as pending in sets computed before it was armed: the dispatch then calls match, skips and refreshes).
     * -1 = no table yet.
     */
    static int setsMismatches(Event event) {
        IEventListener[] listeners = event.getListenerList().getListeners(BI.busId);
        GuardTable t;
        synchronized (LOCK) {
            t = TABLES.get(listeners);
        }
        if (t == null || t.eventClass != event.getClass()) return -1;
        if (t.guarded == 0) return 0;
        int ix = t.index(event);
        if (ix < 0) return 0;
        GuardTable.Holder h = t.holder(event);
        GuardTable.Sets s = t.sets(h, ix, event);
        int bad = 0;
        for (int i = 0; i < t.guards.length; i++) {
            GuardTable.Compiled g = t.guards[i];
            int c = g == null ? -1 : g.match(event, h.bits);
            boolean sk = (s.skip[i >>> 6] & (1L << i)) != 0L, pe = (s.pend[i >>> 6] & (1L << i)) != 0L;
            boolean holds = c >= 0, armed = holds && g.g.armed[c];
            if (sk && !armed) bad++;
            if (pe && !holds) bad++;
            if (holds && !sk && !pe) bad++;
        }
        return bad;
    }

    /** Harness: how many listeners of the event's current table are guarded (-1 = no table yet). */
    public static int guardedCount(Event event) {
        IEventListener[] listeners = event.getListenerList().getListeners(BI.busId);
        synchronized (LOCK) {
            GuardTable t = TABLES.get(listeners);
            return t == null ? -1 : t.guarded;
        }
    }
}
