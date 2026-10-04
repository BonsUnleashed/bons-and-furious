package bons.furious.patch.forge_holders;

import bons.furious.mixin.forge_holders.RegistryObjectAccessor;
import bons.furious.mixin.forge_holders.RegistryObjectHandlerAccessor;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch forge_object_holder_pass_index (Forge 47.4.16, both sides). No Forge code here.
 *
 * GameData.postRegisterEvents fires the register event of every registry (about 300 in this pack) and after each one calls
 * ObjectHolderRegistry.applyObjectHolders(registryKey.location()::equals). That runs objectHolders.forEach over EVERY
 * object holder (about 100,000 here: one handler per DeferredRegister entry) and each handler tests the predicate, so a
 * launch makes ~30 million handler visits that do nothing except for the few hundred holders of that one registry
 * (7.0% of the dedicated server's boot main thread, 2.7% of the client's, srv10_jfr1 / cli10_jfr1).
 *
 * What a holder does with the predicate R::equals:
 *  - RegistryObject$1 (the handler every DeferredRegister entry gets): returns at once once invalidRegistry is set; before
 *    its registry check has passed once (registryExists false, not an optional registry) it checks that the registry
 *    exists (may throw, then sets invalidRegistry); after that (registryExists true, or an optional registry) it only
 *    calls updateReference when R.equals(its final registry name). Once settled it stays settled (the two flags are only
 *    ever set to true, inside accept), so for every other registry its accept is a pure no-op.
 *  - every other handler (ObjectHolderRef, other mods' lambdas): unknown, always called.
 *
 * This class keeps a snapshot of the set in its own iteration order (built with the set's iterator) with one int per
 * holder: the id of a settled holder's registry name (ids by ResourceLocation equals/hashCode, so id equality is exactly
 * R.equals), NEVER for an invalid one, UNSETTLED / ALWAYS for the rest. A registry pass then calls, in snapshot order,
 * exactly the holders whose accept could do anything for that registry, through the original per-holder lambda of
 * applyObjectHolders (same try/catch, same aggregate exception, same order of suppressed exceptions). An UNSETTLED holder
 * is re-read after its call. The snapshot is rebuilt whenever the set changed structurally since it was built (every
 * successful add/remove through addHandler/removeHandler bumps a counter; a size change is checked too, which also covers
 * ModernFix's reflective removal at the end of postRegisterEvents). A structural change DURING a pass behaves as the
 * set's iterator does: ConcurrentModificationException before the next holder if there is one, else the pass ends.
 * The snapshot is dropped when postRegisterEvents returns. Other applyObjectHolders callers (revertTo, injectSnapshot:
 * predicate key -> true) always run the original loop.
 */
public final class HolderPassIndex {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.forgeObjectHolderPassIndex=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.forgeObjectHolderPassIndex", "true"));
    /** Shadow mode for rigs: before every indexed pass the live set is walked and every snapshot position and skip decision re-derived. */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.forgeObjectHolderPassIndex.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Counters (read by probes): indexed passes, holders called, holders skipped, snapshot builds. */
    public static final AtomicLong PASSES = new AtomicLong(), CALLED = new AtomicLong(), SKIPPED = new AtomicLong(), BUILDS = new AtomicLong();

    static final int ALWAYS = -1;      // not a RegistryObject handler: always called
    static final int UNSETTLED = -2;   // RegistryObject handler whose registry check has not passed yet: called, then re-read
    static final int NEVER = -3;       // RegistryObject handler with invalidRegistry set: accept returns at once
    private static final int NO_ID = Integer.MIN_VALUE;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    /** Structural version of ObjectHolderRegistry.objectHolders (written under its class lock: addHandler/removeHandler are static synchronized). */
    private static volatile long version;

    // the registry pass in progress (set by the GameData hook around its applyObjectHolders call)
    private static Thread passThread;
    private static Predicate<?> passFilter;
    private static ResourceLocation passRegistry;
    // the predicate of the applyObjectHolders call that runs now (noted at the start of the method)
    private static Thread applyThread;
    private static Predicate<?> applyFilter;

    // the snapshot (only touched by the pass thread)
    private static Object[] holders;
    private static int[] codes;
    private static HashMap<ResourceLocation, Integer> ids;
    private static long builtVersion = -1;
    private static int builtSize = -1;

    private HolderPassIndex() {
    }

    /** addHandler / removeHandler changed the set. */
    public static void structureChanged() {
        version++;
    }

    /** GameData.postRegisterEvents is about to call applyObjectHolders(filter) with filter = registry::equals. */
    public static void begin(Predicate<?> filter, ResourceLocation registry) {
        passThread = Thread.currentThread();
        passFilter = filter;
        passRegistry = registry;
    }

    public static void end() {
        passThread = null;
        passFilter = null;
        passRegistry = null;
        applyThread = null;
        applyFilter = null;
    }

    /** applyObjectHolders(filter) starts (any caller). */
    public static void noteApply(Predicate<?> filter) {
        applyThread = Thread.currentThread();
        applyFilter = filter;
    }

    /**
     * True at applyObjectHolders' forEach when the predicate of the running call is the one the GameData hook announced
     * (same object, same thread). A call from anywhere else (revertTo, injectSnapshot, a nested call) has another predicate.
     */
    public static boolean isRegistryPass() {
        Thread t = Thread.currentThread();
        Predicate<?> f = passFilter;
        return enabled && f != null && applyFilter == f && applyThread == t && passThread == t;
    }

    /** postRegisterEvents returned: drop the snapshot (it is rebuilt if ever needed again). */
    public static void release() {
        if (holders != null)
            LOGGER.debug("Bons and Furious: forge_object_holder_pass_index: {} passes, {} holders called, {} skipped, {} snapshot builds",
                    PASSES.get(), CALLED.get(), SKIPPED.get(), BUILDS.get());
        holders = null;
        codes = null;
        ids = null;
        builtVersion = -1;
        builtSize = -1;
    }

    /**
     * The replacement of objectHolders.forEach(action) for a registry pass: calls action.accept(holder) for the holders the
     * original would have found acting, in the set's iteration order; action is applyObjectHolders' own per-holder lambda.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void forEach(Set<?> set, Consumer action) {
        if (holders == null || builtVersion != version || builtSize != set.size()) build(set);
        if (SHADOW) shadowCheck(set);
        Integer idObj = ids.get(passRegistry);
        int id = idObj == null ? NO_ID : idObj;
        Object[] hs = holders;
        int[] cs = codes;
        int n = hs.length;
        long v = builtVersion;
        long called = 0;
        PASSES.incrementAndGet();
        for (int i = 0; i < n; i++) {
            int c = cs[i];
            if (c >= 0 ? c != id : c == NEVER) continue;
            called++;
            action.accept(hs[i]);
            if (version != v) {
                // the set changed during this holder's call: the original iterator throws on its next step, if any
                CALLED.addAndGet(called);
                SKIPPED.addAndGet(i + 1 - called);
                release();
                if (i < n - 1) throw new ConcurrentModificationException();
                return;
            }
            if (c == UNSETTLED) cs[i] = classify(hs[i], ids);
        }
        CALLED.addAndGet(called);
        SKIPPED.addAndGet(n - called);
    }

    private static void build(Set<?> set) {
        int expected = set.size();
        Object[] hs = new Object[expected];
        int k = 0;
        for (Object h : set) {
            if (k == hs.length) hs = Arrays.copyOf(hs, k * 2 + 16);
            hs[k++] = h;
        }
        if (k != hs.length) hs = Arrays.copyOf(hs, k);
        HashMap<ResourceLocation, Integer> idMap = new HashMap<>();
        int[] cs = new int[k];
        for (int i = 0; i < k; i++) cs[i] = classify(hs[i], idMap);
        holders = hs;
        codes = cs;
        ids = idMap;
        builtVersion = version;
        builtSize = expected;
        BUILDS.incrementAndGet();
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: forge_object_holder_pass_index calls only the matching object holders in each registry pass ({} holders)", k);
        }
    }

    /** What accept(registry::equals) can do for this holder, from its current state (see the class comment). */
    static int classify(Object holder, HashMap<ResourceLocation, Integer> idMap) {
        if (!(holder instanceof RegistryObjectHandlerAccessor h)) return ALWAYS;
        if (h.bons$invalidRegistry()) return NEVER;
        Object owner = h.bons$owner();
        if (!(owner instanceof RegistryObjectAccessor o)) return ALWAYS;
        if (!o.bons$optionalRegistry() && !h.bons$registryExists()) return UNSETTLED;
        ResourceLocation name = h.bons$registryName();
        Integer id = idMap.get(name);
        if (id == null) {
            id = idMap.size();
            idMap.put(name, id);
        }
        return id;
    }

    /** Shadow: the live iteration order equals the snapshot and every code re-derived from the live state equals the stored one. */
    private static void shadowCheck(Set<?> set) {
        SHADOW_CHECKS.incrementAndGet();
        Object[] hs = holders;
        int[] cs = codes;
        HashMap<ResourceLocation, Integer> probe = new HashMap<>(ids);
        Iterator<?> it = set.iterator();
        int i = 0;
        while (it.hasNext()) {
            Object live = it.next();
            if (i >= hs.length || live != hs[i]) {
                mismatch("snapshot order differs from the set at position " + i);
                return;
            }
            int c = classify(live, probe);
            if (c != cs[i]) mismatch("holder " + live.getClass().getName() + " at " + i + ": stored " + cs[i] + ", live " + c);
            i++;
        }
        if (i != hs.length) mismatch("snapshot has " + hs.length + " holders, the set " + i);
    }

    private static void mismatch(String what) {
        if (SHADOW_MISMATCHES.incrementAndGet() <= 20)
            LOGGER.warn("Bons and Furious: forge_object_holder_pass_index shadow check: {}", what);
    }
}
