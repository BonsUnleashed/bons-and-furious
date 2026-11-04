package bons.furious.patch.forge_listener_guard;

import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.eventbus.EventBus;
import net.minecraftforge.eventbus.api.Event;
import org.objectweb.asm.tree.MethodNode;

/**
 * forge_listener_prologue_guard: entry points for the offline harnesses (tools/mixedproof/ForgeListenerGuard*.java).
 * Nothing in the game calls this class.
 */
public final class Harness {
    private Harness() {}

    /** Accepts a harness bus (after GuardedDispatch.harnessBus): false with the WARN when the shape check fails. */
    public static boolean prepare(EventBus bus) {
        GuardTable.keepCode = true;
        return GuardedDispatch.ready(bus);
    }

    /** Runs the guarded dispatch of a post site on the prepared harness bus. */
    public static boolean dispatch(int site, EventBus bus, Event event) {
        return GuardedDispatch.dispatch(site, bus, event);
    }

    /** The precomputed skip sets of this event's signature against the direct tests (0 = consistent, -1 = no table). */
    public static int setsMismatches(Event event) {
        return GuardedDispatch.setsMismatches(event);
    }

    /** The shadow check of the event's current table: {verdict, listener, armed} per guard whose clause holds now. */
    public static java.util.List<String[]> probeAll(Event event) {
        return GuardedDispatch.probeAll(event);
    }

    /** Counting of skips (GuardedDispatch.SKIPS) on or off. */
    public static void countSkips(boolean on) {
        GuardedDispatch.countSkips = on;
    }

    public static long skips() {
        return GuardedDispatch.SKIPS.get();
    }

    /** One analysed listener method, compiled for an event class: its form, clauses and test. */
    public static final class One {
        public final PrologueAnalyser.Result result;
        final GuardTable.Compiled compiled;
        final ClassValue<long[]> profile;
        final int subject;
        public final String why;

        One(PrologueAnalyser.Result result, GuardTable.Compiled compiled, ClassValue<long[]> profile, int subject, String why) {
            this.result = result;
            this.compiled = compiled;
            this.profile = profile;
            this.subject = subject;
            this.why = why;
        }

        public boolean guarded() {
            return compiled != null;
        }

        /** Whether the guard would skip this event (any clause holds; arming not considered). */
        public boolean skips(Event e) {
            if (compiled == null) return false;
            long bits = 0;
            if (profile != null) {
                Object s = EventFacts.read(subject, e);
                bits = s == null ? 0 : profile.get(s.getClass())[0];
            }
            return compiled.match(e, bits) >= 0;
        }

        /** The probe of the method, defined next to its class (owner must be loadable). */
        public MethodHandle probe(Class<?> owner, MethodNode m, boolean isStatic) throws Throwable {
            return PrologueProbe.define(owner, m, isStatic, result);
        }
    }

    /**
     * Analyses a method for a posted event, as the game would (audit as set by AuditTrail.harness): the analysis for the
     * sample's class, the constants checked and read against the sample (the event that would build the table).
     */
    public static One analyse(MethodNode m, boolean isStatic, Event sample) {
        EventFacts facts = new EventFacts(sample.getClass());
        PrologueAnalyser.Result r = PrologueAnalyser.analyse(m, isStatic, facts);
        if (!r.guarded()) return new One(r, null, null, 0, r.rejected != null ? r.rejected : "no early return");
        List<String> keys = new ArrayList<>();
        List<String[]> sets = new ArrayList<>();
        int subject = 0;
        for (PrologueAnalyser.Clause c : r.clauses) {
            for (PrologueAnalyser.Atom a : c.atoms) {
                if (!EventFacts.readable(a.fact)) return new One(r, null, null, 0, "fact not readable");
                if (a.kind != PrologueAnalyser.A_INST) continue;
                if (subject == 0) subject = a.fact;
                if (a.fact != subject) return new One(r, null, null, 0, "instance-of on two subjects");
                String k = GuardTable.key(a.names);
                if (!keys.contains(k)) {
                    keys.add(k);
                    sets.add(k.split(","));
                }
            }
        }
        if (keys.size() > 64) return new One(r, null, null, 0, "more than 64 instance-of sets");
        GuardTable.ListenerGuard lg = new GuardTable.ListenerGuard(new ListenerTargets.Target(null, isStatic, false, false, m.name), r, m);
        String[] why = new String[1];
        GuardTable.Compiled c = GuardTable.Compiled.of(lg, facts, keys, sample, why);
        return new One(r, c, sets.isEmpty() ? null : Profiles.profile(sets.toArray(new String[0][])), subject, why[0]);
    }

    /** The fingerprints of the running eventbus / LogicalSide methods (BusShape), and its verdict (null = all match). */
    public static java.util.Map<String, String> busShape() {
        return BusShape.actual();
    }

    public static String busShapeVerdict() {
        return BusShape.check();
    }

    /** True while BusShape has no tested fingerprint recorded (the first offline run prints them). */
    public static boolean busShapeUnrecorded() {
        return BusShape.unrecorded();
    }

    /** First offline run only: accept the running eventbus before its fingerprints are recorded in BusShape. */
    public static void acceptBusShape(boolean accept) {
        BusShape.harnessAccept = accept;
    }

    /** Site flags after ListenerGuardPlugin.inspect (harness: hooked / foreign per site). */
    public static boolean hooked(int site) {
        return Sites.HOOKED[site];
    }

    public static boolean foreign(int site) {
        return Sites.FOREIGN[site];
    }

    public static void resetSites() {
        for (int i = 0; i < Sites.COUNT; i++) {
            Sites.HOOKED[i] = false;
            Sites.FOREIGN[i] = false;
            Sites.FOREIGN_BY[i] = null;
        }
    }

    public static String foreignBy(int site) {
        return Sites.FOREIGN_BY[site];
    }

    /** {owner, name, descriptor} of a post site's Forge method. */
    public static String[] siteMethod(int site) {
        return Sites.METHODS[site].clone();
    }
}
