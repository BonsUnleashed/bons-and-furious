package bons.furious.patch.forge_listener_guard;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.IEventListenerFactory;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventListener;
import org.objectweb.asm.tree.MethodNode;

/**
 * forge_listener_prologue_guard: the guards of one listener array (one event class on the Forge bus), parallel to it.
 * Built once per array object (ListenerList hands out a new array whenever a listener is added or removed). The
 * analysis and the armed state of a listener live in its {@link ListenerGuard}, kept across rebuilds; the compiled tests
 * ({@link Compiled}: constants resolved, instance-of bits of THIS table's profile) are immutable and belong to one table,
 * so a thread still posting with an older table never mixes its bits with another table's masks.
 */
final class GuardTable {
    final Class<?> eventClass;
    final Compiled[] guards;
    final int guarded, subjectFact;
    final ClassValue<long[]> profile;
    final List<String> report;
    // ---- the post signature: every fact the table's tests read, as one small index (plus the subject's class)
    private final boolean usesPhase, usesSide, usesType, usesLevel, usesStage, usesSubject;
    private final int nPhase, nSide, nType, nStage, size, words;
    private final Object[] stages;
    /** tables without a subject: the one holder; with a subject: null subject / per subject class (bits = its profile) */
    private final Holder plain, nullSubject;
    private final ClassValue<Holder> bySubject;

    /** The skip sets of one signature: bit i of skip = listener i's test holds and is armed; of pend = holds, not armed yet. */
    static final class Sets {
        final long[] skip, pend;

        Sets(long[] skip, long[] pend) {
            this.skip = skip;
            this.pend = pend;
        }
    }

    /** One subject class (or none): its instance-of profile bits and the sets per signature index (filled lazily). */
    static final class Holder {
        final long bits;
        final boolean subject;
        final Sets[] sets;

        Holder(long bits, boolean subject, int size) {
            this.bits = bits;
            this.subject = subject;
            this.sets = new Sets[size];
        }
    }

    private GuardTable(int listenerCount, Class<?> eventClass, Compiled[] guards, int guarded, int subjectFact,
                       ClassValue<long[]> profile, List<String> report) {
        this.eventClass = eventClass;
        this.guards = guards;
        this.guarded = guarded;
        this.subjectFact = subjectFact;
        this.profile = profile;
        this.report = report;
        boolean p = false, s = false, t = false, l = false, st = false, sub = false;
        for (Compiled g : guards) {
            if (g == null) continue;
            for (int[] facts : g.fact) {
                for (int f : facts) {
                    switch (f) {
                        case PrologueAnalyser.F_PHASE -> p = true;
                        case PrologueAnalyser.F_SIDE -> s = true;
                        case PrologueAnalyser.F_TYPE -> t = true;
                        case PrologueAnalyser.F_LEVEL -> l = true;
                        case PrologueAnalyser.F_STAGE -> st = true;
                        case PrologueAnalyser.F_LIVING, PrologueAnalyser.F_PLAYER -> sub = true;
                        default -> { }
                    }
                }
            }
        }
        usesPhase = p;
        usesSide = s;
        usesType = t;
        usesLevel = l;
        usesStage = st;
        usesSubject = sub && subjectFact != 0;
        nPhase = p ? TickEvent.Phase.values().length + 1 : 1;
        nSide = s ? net.minecraftforge.fml.LogicalSide.values().length + 1 : 1;
        nType = t ? TickEvent.Type.values().length + 1 : 1;
        stages = st ? EventFacts.stageConstants(eventClass) : new Object[0];
        nStage = st ? stages.length + 2 : 1;
        size = nPhase * nSide * nType * (l ? 3 : 1) * nStage;
        words = (listenerCount + 63) >>> 6;
        plain = new Holder(0L, false, size);
        nullSubject = new Holder(0L, false, size);
        ClassValue<long[]> prof = profile;
        int sz = size;
        bySubject = usesSubject ? new ClassValue<>() {
            @Override
            protected Holder computeValue(Class<?> type) {
                return new Holder(prof == null ? 0L : prof.get(type)[0], true, sz);
            }
        } : null;
    }

    private static int ord(Enum<?> v) {
        return v == null ? 0 : v.ordinal() + 1;
    }

    /** The signature index of this post (every fact the tests read except the subject), -1 if out of range. */
    int index(Event e) {
        int ix = 0;
        if (usesPhase || usesSide || usesType) {
            TickEvent t = (TickEvent) e;
            if (usesPhase) ix = ix * nPhase + ord(t.phase);
            if (usesSide) ix = ix * nSide + ord(t.side);
            if (usesType) ix = ix * nType + ord(t.type);
        }
        if (usesLevel) {
            Level level = ((TickEvent.LevelTickEvent) e).level;
            ix = ix * 3 + (level == null ? 0 : level.f_46443_ ? 1 : 2);
        }
        if (usesStage) {
            // a stage that is none of the Stage constants (cannot happen: its constructor is private) is never skipped,
            // so two such stages never share one set of answers
            Object v = EventFacts.read(PrologueAnalyser.F_STAGE, e);
            int si = -1;
            if (v == null) si = 0;
            else for (int i = 0; i < stages.length; i++) if (stages[i] == v) { si = i + 2; break; }
            if (si < 0) return -1;
            ix = ix * nStage + si;
        }
        return ix < size ? ix : -1;
    }

    /** The holder of this post's subject (the table's one holder when the tests read no subject). */
    Holder holder(Event e) {
        if (!usesSubject) return plain;
        Object s = EventFacts.read(subjectFact, e);
        return s == null ? nullSubject : bySubject.get(s.getClass());
    }

    /** The skip sets of this signature, computed on first need from this event (any event of the signature gives the same). */
    Sets sets(Holder h, int ix, Event e) {
        Sets s = h.sets[ix];
        if (s == null) h.sets[ix] = s = compute(e, h.bits);
        return s;
    }

    /** After an arming (or a stale pending bit): this signature's sets again. */
    void refresh(Holder h, int ix, Event e) {
        h.sets[ix] = compute(e, h.bits);
    }

    private Sets compute(Event e, long bits) {
        long[] skip = new long[words], pend = new long[words];
        for (int i = 0; i < guards.length; i++) {
            Compiled g = guards[i];
            if (g == null) continue;
            int c = g.match(e, bits);
            if (c < 0) continue;
            if (g.g.armed[c]) skip[i >>> 6] |= 1L << i;
            else pend[i >>> 6] |= 1L << i;
        }
        return new Sets(skip, pend);
    }

    /** listener -> its guard (or NO_GUARD) per event class; weak, so removed listeners go. Guarded by the build lock. */
    private static final Map<Class<?>, WeakHashMap<IEventListener, Object>> MEMO = new HashMap<>();
    private static final Object NO_GUARD = new Object();
    private static final WeakHashMap<IEventListener, String> WHY = new WeakHashMap<>();
    /** Harness only: keep each guard's method code (for the probes) even when shadow mode is off. */
    static volatile boolean keepCode;

    /** Harness: forget every analysis (a new bus). */
    static void resetMemo() {
        synchronized (MEMO) {
            MEMO.clear();
            WHY.clear();
        }
    }

    /** Builds the table; never throws (an unexpected problem gives a table without guards). Caller holds the lock. */
    static GuardTable build(IEventListener[] listeners, Event sample, IEventListenerFactory factory) {
        Class<?> eventClass = sample.getClass();
        try {
            synchronized (MEMO) {
                return build0(listeners, sample, factory);
            }
        } catch (Throwable t) {
            GuardedDispatch.LOGGER.warn("Bons and Furious: forge_listener_prologue_guard could not build the guards for {} ({}); its listeners are all called",
                    eventClass.getName(), t.toString());
            return new GuardTable(listeners.length, eventClass, new Compiled[listeners.length], 0, 0, null, List.of("failed: " + t));
        }
    }

    private static GuardTable build0(IEventListener[] listeners, Event sample, IEventListenerFactory factory) {
        Class<?> eventClass = sample.getClass();
        WeakHashMap<IEventListener, Object> memo = MEMO.computeIfAbsent(eventClass, k -> new WeakHashMap<>());
        EventFacts facts = new EventFacts(eventClass);
        ListenerGuard[] found = new ListenerGuard[listeners.length];
        List<String> report = new ArrayList<>();
        for (int i = 0; i < listeners.length; i++) {
            IEventListener l = listeners[i];
            Object g = memo.get(l);
            if (g == null) {
                String[] why = new String[1];
                ListenerGuard lg = analyse(l, factory, facts, why);
                g = lg != null ? lg : NO_GUARD;
                memo.put(l, g);
                if (lg == null) WHY.put(l, why[0]);
            }
            if (g instanceof ListenerGuard lg) found[i] = lg;
        }
        // one bit per distinct instance-of name set; one subject fact per table
        List<String> setKeys = new ArrayList<>();
        List<String[]> sets = new ArrayList<>();
        int subject = 0;
        for (int i = 0; i < found.length; i++) {
            ListenerGuard lg = found[i];
            if (lg == null) continue;
            boolean ok = true;
            int mySubject = subject;
            List<String> mine = new ArrayList<>();
            for (PrologueAnalyser.Clause c : lg.source) {
                for (PrologueAnalyser.Atom a : c.atoms) {
                    // the subject (entity / player) is part of the post signature: null tests and instance-of tests alike
                    if (a.fact != PrologueAnalyser.F_LIVING && a.fact != PrologueAnalyser.F_PLAYER) continue;
                    if (mySubject == 0) mySubject = a.fact;
                    if (a.fact != mySubject) ok = false;
                    if (a.kind != PrologueAnalyser.A_INST) continue;
                    String key = key(a.names);
                    if (!setKeys.contains(key) && !mine.contains(key)) mine.add(key);
                }
            }
            if (!ok || setKeys.size() + mine.size() > 64) {
                found[i] = null;
                WHY.put(listeners[i], ok ? "more than 64 instance-of sets in one event" : "instance-of on two subjects");
                continue;
            }
            subject = mySubject;
            for (String k : mine) {
                setKeys.add(k);
                sets.add(k.split(","));
            }
        }
        Compiled[] guards = new Compiled[listeners.length];
        int guarded = 0;
        for (int i = 0; i < found.length; i++) {
            ListenerGuard lg = found[i];
            String why = null;
            if (lg != null) {
                String[] w = new String[1];
                guards[i] = Compiled.of(lg, facts, setKeys, sample, w);
                if (guards[i] != null) guarded++;
                else why = w[0];
            }
            report.add(String.format("%3d %-5s %s", i, guards[i] != null ? "GUARD" : "run",
                    guards[i] != null ? lg.name + " : " + lg.describe : describe(listeners[i]) + (why != null ? " (" + why + ")" : "")));
        }
        ClassValue<long[]> profile = sets.isEmpty() ? null : Profiles.profile(sets.toArray(new String[0][]));
        return new GuardTable(listeners.length, eventClass, guards, guarded, subject, profile, report);
    }

    private static String describe(IEventListener l) {
        String s = l instanceof net.minecraftforge.eventbus.api.EventPriority p ? "phase marker " + p : String.valueOf(l);
        return s + " : " + WHY.getOrDefault(l, "-");
    }

    static String key(String[] names) {
        String[] sorted = names.clone();
        Arrays.sort(sorted);
        return String.join(",", sorted);
    }

    private static ListenerGuard analyse(IEventListener l, IEventListenerFactory factory, EventFacts facts, String[] why) {
        if (l instanceof net.minecraftforge.eventbus.api.EventPriority) {
            why[0] = "phase marker (always called)";
            return null;
        }
        ListenerTargets.Target t = ListenerTargets.resolve(l, factory, why);
        if (t == null) return null;
        Class<?> owner = t.impl().getDeclaringClass();
        if (!AuditTrail.untransformed(owner.getName(), false)) {
            why[0] = "class changed by a mixin or transformer";
            return null;
        }
        MethodNode m = ListenerTargets.methodNode(t.impl());
        if (m == null) {
            why[0] = "class file not readable";
            return null;
        }
        PrologueAnalyser.Result r = PrologueAnalyser.analyse(m, t.isStatic(), facts);
        if (!r.guarded()) {
            why[0] = r.rejected != null ? r.rejected : "no early return";
            return null;
        }
        for (PrologueAnalyser.Clause c : r.clauses) {
            for (PrologueAnalyser.Atom a : c.atoms) {
                if (!EventFacts.readable(a.fact)) {
                    why[0] = "fact not readable";
                    return null;
                }
            }
        }
        return new ListenerGuard(t, r, GuardedDispatch.SHADOW || keepCode ? m : null);
    }

    /** One listener's analysis and armed clauses, shared by every table it appears in. */
    static final class ListenerGuard {
        final String name, describe;
        final boolean cancelCheck, receiveCanceled;
        final List<PrologueAnalyser.Clause> source;
        final PrologueAnalyser.Result result;
        /** the method's code, kept only for shadow probes (null otherwise) */
        final MethodNode code;
        final Method impl;
        final boolean isStatic;
        /**
         * armed[c]: clause c has been seen to hold on a real call that entered the method and returned normally (for a
         * clause with an instance-of test: with a non-null subject). Only ever set to true; a racing thread that does
         * not see it yet simply calls the listener (the stock path).
         */
        final boolean[] armed;
        /** false after an unexpected problem while testing (never skips then). */
        volatile boolean alive = true;
        /** shadow mode: the probe (the method's own opening instructions, true at its early returns), or null. */
        volatile java.lang.invoke.MethodHandle probe;
        volatile boolean probeTried;

        ListenerGuard(ListenerTargets.Target t, PrologueAnalyser.Result r, MethodNode code) {
            this.name = t.name();
            this.cancelCheck = t.cancelCheck();
            this.receiveCanceled = t.receiveCanceled();
            this.source = r.clauses;
            this.result = r;
            this.code = code;
            this.impl = t.impl();
            this.isStatic = t.isStatic();
            this.describe = r.form + " " + r.clauses;
            this.armed = new boolean[r.clauses.size()];
        }
    }

    /** A listener's tests compiled for one table: immutable. */
    static final class Compiled {
        final ListenerGuard g;
        final int[][] kind, fact;
        final Object[][] constant;
        final long[][] mask;
        final boolean[][] expect;
        /** inst[c]: clause c has an instance-of test (armed only after a call with a non-null subject) */
        final boolean[] inst;

        private Compiled(ListenerGuard g, int[][] kind, int[][] fact, Object[][] constant, long[][] mask, boolean[][] expect, boolean[] inst) {
            this.g = g;
            this.kind = kind;
            this.fact = fact;
            this.constant = constant;
            this.mask = mask;
            this.expect = expect;
            this.inst = inst;
        }

        /**
         * Resolves the constants and the instance-of bits of this table; null (reason in why[0]) when a constant cannot be
         * read without initialising its class. A constant is read only when the sample event (the post that builds the
         * table) holds an instance of the constant's class, so that class is already initialised.
         */
        static Compiled of(ListenerGuard g, EventFacts facts, List<String> setKeys, Event sample, String[] why) {
            int n = g.source.size();
            int[][] kind = new int[n][], fact = new int[n][];
            Object[][] constant = new Object[n][];
            long[][] mask = new long[n][];
            boolean[][] expect = new boolean[n][];
            boolean[] inst = new boolean[n];
            for (int c = 0; c < n; c++) {
                PrologueAnalyser.Atom[] atoms = g.source.get(c).atoms;
                kind[c] = new int[atoms.length];
                fact[c] = new int[atoms.length];
                constant[c] = new Object[atoms.length];
                mask[c] = new long[atoms.length];
                expect[c] = new boolean[atoms.length];
                for (int i = 0; i < atoms.length; i++) {
                    PrologueAnalyser.Atom a = atoms[i];
                    kind[c][i] = a.kind;
                    fact[c][i] = a.fact;
                    expect[c][i] = a.expect;
                    if (a.constant != null) {
                        if (!EventFacts.constantClassInitialised(a.constant, sample)) {
                            why[0] = "constant " + a.constant + " of a class this event does not hold";
                            return null;
                        }
                        try {
                            constant[c][i] = facts.constantValue(a.constant);
                        } catch (Throwable e) {
                            why[0] = "constant not readable";
                            return null;
                        }
                        if (constant[c][i] == null) {
                            why[0] = "null constant";
                            return null;
                        }
                    }
                    if (a.kind == PrologueAnalyser.A_INST) {
                        inst[c] = true;
                        int bit = setKeys.indexOf(key(a.names));
                        if (bit < 0) {
                            why[0] = "instance-of set without a bit";
                            return null;
                        }
                        mask[c][i] = 1L << bit;
                    }
                }
            }
            return new Compiled(g, kind, fact, constant, mask, expect, inst);
        }

        /** The first clause that holds on this event (-1 = none). Never throws; a problem disables the guard. */
        int match(Event e, long bits) {
            if (!g.alive) return -1;
            try {
                clauses:
                for (int c = 0; c < kind.length; c++) {
                    int[] k = kind[c];
                    for (int i = 0; i < k.length; i++) if (!holds(k[i], fact[c][i], constant[c][i], mask[c][i], expect[c][i], e, bits)) continue clauses;
                    return c;
                }
                return -1;
            } catch (Throwable t) {
                g.alive = false;
                GuardedDispatch.LOGGER.warn("Bons and Furious: forge_listener_prologue_guard: testing {} failed ({}); it is always called from now on", g.name, t.toString());
                return -1;
            }
        }

        private static boolean holds(int kind, int fact, Object constant, long mask, boolean expect, Event e, long bits) {
            switch (kind) {
                case PrologueAnalyser.A_EQ:
                    return (EventFacts.read(fact, e) == constant) == expect;
                case PrologueAnalyser.A_EQNN: {
                    Object v = EventFacts.read(fact, e);
                    return v != null && ((v == constant) == expect);
                }
                case PrologueAnalyser.A_NULL:
                    return (EventFacts.read(fact, e) == null) == expect;
                case PrologueAnalyser.A_LEVEL_CLIENT: {
                    Level level = ((TickEvent.LevelTickEvent) e).level;
                    return level != null && (level.f_46443_ == expect);
                }
                case PrologueAnalyser.A_INST:
                    return ((bits & mask) != 0L) == expect;
                default:
                    throw new IllegalStateException("atom " + kind);
            }
        }
    }
}
