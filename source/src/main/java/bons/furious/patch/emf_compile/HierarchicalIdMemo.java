package bons.furious.patch.emf_compile;

import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;

/**
 * Bons and Furious switch emf_hierarchical_id_memo (Entity Model Features 3.2.4, client). No EMF code here.
 *
 * EMFManager.setupAnimationsFromJemToModel builds one part map per model variant (a fresh local HashMap: "root" plus every
 * part under its own id and its "parent:child" paths) and then calls the static getModelFromHierarchicalId(id, map) for
 * the prefix of every animation line, and ModelPartVariableFactory calls it for every part reference in an expression.
 * In the Fresh Animations player variants 470-578 of 728-836 lines are var.* / varb.* - not parts - so after two map
 * misses the method scans every entry (endsWith with two concatenations, a split and a stream per entry) and returns
 * null, hundreds of times per variant.
 *
 * Within one setupAnimationsFromJemToModel call (a scope, opened and closed around the call on its thread) the answers
 * for that call's map are remembered by id, null included. Identical because the map is created by that call, filled
 * before the first lookup and only read afterwards (AnimSetupContext and ModelPartVariableFactory read it, close() only
 * drops the reference), so for that map the result - the first match in its iteration order, or null - cannot change, and
 * the lookup has no side effects. The scope binds to the first map it sees; other maps, null or blank ids, a disabled
 * switch and calls outside a scope go to the original. As a belt-and-braces check the scope stands down (WARN) if the
 * map's size ever changes.
 */
public final class HierarchicalIdMemo {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.emfHierarchicalIdMemo=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.emfHierarchicalIdMemo", "true"));
    /** Shadow mode for rigs: every remembered answer is also computed the original way and compared by identity. */
    public static final boolean VERIFY = Boolean.getBoolean("bons_and_furious.emfHierarchicalIdMemo.verify");
    /** Returned by {@link #cached} when the caller must run the original lookup. */
    public static final Object MISS = new Object();
    private static final Object NONE = new Object();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadLocal<Scope> SCOPE = new ThreadLocal<>();
    private static volatile boolean announced;
    private static volatile boolean warned;
    /** Counters (read by probes and the harness). */
    public static long standDowns;
    public static long hits;
    public static long stored;
    public static long verified;
    public static long mismatches;

    private HierarchicalIdMemo() {
    }

    /** One setupAnimationsFromJemToModel call. */
    public static final class Scope {
        Map<?, ?> map;
        int size;
        boolean off;
        final HashMap<String, Object> answers = new HashMap<>();
    }

    /** Opens a scope for the current thread; returns the previous one for {@link #leave}. */
    public static Scope enter() {
        Scope previous = SCOPE.get();
        SCOPE.set(enabled ? new Scope() : null);
        return previous;
    }

    public static void leave(Scope previous) {
        if (previous == null) SCOPE.remove();
        else SCOPE.set(previous);
    }

    /** The remembered answer for (id, map) in the current scope, or MISS. */
    public static Object cached(String id, Map<?, ?> map) {
        Scope s = SCOPE.get();
        if (s == null || s.off || id == null || map == null || !enabled) return MISS;
        if (s.map == null) {
            s.map = map;
            s.size = map.size();
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: emf_hierarchical_id_memo remembers EMF's model-part lookups during each model setup");
            }
            return MISS;
        }
        if (s.map != map) return MISS;
        if (map.size() != s.size) {
            s.off = true;
            standDowns++;
            if (!warned) {
                warned = true;
                LOGGER.warn("Bons and Furious: emf_hierarchical_id_memo: a model-part map changed size during setup; EMF's own lookup answers for the rest of that setup (later cases are counted, not logged)");
            }
            return MISS;
        }
        Object a = s.answers.get(id);
        if (a == null) return MISS;
        hits++;
        return a == NONE ? null : a;
    }

    /** Remembers the original's answer for (id, map) when the current scope is bound to that map. */
    public static void store(String id, Map<?, ?> map, Object answer) {
        Scope s = SCOPE.get();
        if (s == null || s.off || id == null || s.map != map) return;
        s.answers.put(id, answer == null ? NONE : answer);
        stored++;
    }

    /** Shadow mode: the remembered answer against a fresh original answer. */
    public static void verify(String id, Object remembered, Object fresh) {
        verified++;
        if (!Objects.equals(remembered, fresh) || remembered != fresh) {
            if (mismatches++ < 20) LOGGER.warn("Bons and Furious: emf_hierarchical_id_memo shadow check differs for [{}]", id);
        }
    }
}
