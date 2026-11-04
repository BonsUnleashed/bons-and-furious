package bons.furious.patch.neobackports_c49;

import com.google.common.collect.ImmutableMap;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch neobackports_component_defaults_memo (NeoBackports 1.4.3, LGPL-2.1; byte-identical 1.4.2 is
 * bundled twice in Starcatcher 3.3.10; both sides). Ours; no NeoBackports code is carried.
 *
 * What NeoBackports does. DataDefault.getDefaults() returns ImmutableMap.copyOf of its static HashMap item -> defaults on
 * every call; DataComponentRegistry asks it up to four times per has / get / getOrDefault on an item stack (Starcatcher:
 * once per item drawn per frame through its item render hook).
 *
 * What the switch does. The copy is made once and handed out again until the HashMap can have changed: every write goes
 * through the private overrideDefault (registerDefault and the instance methods register / override funnel into it), and
 * a generation is bumped at its head and at its return; a copy made at another generation is never handed out (a new one
 * is made by the original call).
 *
 * Exact variant: callers get the SAME ImmutableMap instance between two registrations instead of a new, equal one each
 * call. Content and iteration order are those of a fresh copy: the copy is shallow (the per-item value maps are the live
 * HashMaps, as in every stock copy) and the outer HashMap is unchanged since the copy was made.
 *
 * -Dbons_and_furious.neobackportsComponentDefaultsMemo=false switches it off at run time.
 * -Dbons_and_furious.neobackportsComponentDefaultsMemo.shadow=true: every hand-out is compared with a fresh copy
 * (entries in order, values by identity; SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20).
 */
public final class DefaultsCopy {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.neobackportsComponentDefaultsMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.neobackportsComponentDefaultsMemo.shadow");
    public static final java.util.concurrent.atomic.AtomicLong SHADOW_CHECKS = new java.util.concurrent.atomic.AtomicLong(),
            SHADOW_MISMATCHES = new java.util.concurrent.atomic.AtomicLong();
    private static final AtomicInteger GENERATION = new AtomicInteger();
    private static volatile Held held;
    private static volatile boolean announced;

    private record Held(int generation, ImmutableMap<?, ?> copy) {
    }

    private DefaultsCopy() {
    }

    /** DataDefault.overrideDefault HEAD and RETURN. */
    public static void bump() {
        GENERATION.incrementAndGet();
    }

    /** In place of the ImmutableMap.copyOf(DEFAULT_DATA_COMPONENTS_REGISTERED) call in DataDefault.getDefaults. */
    public static ImmutableMap<?, ?> copyOf(Map<?, ?> registered, Operation<ImmutableMap<?, ?>> original) {
        if (!enabled) return original.call(registered);
        int g = GENERATION.get();
        Held h = held;
        if (h != null && h.generation == g) {
            if (SHADOW) shadow(h.copy, original.call(registered), g);
            return h.copy;
        }
        ImmutableMap<?, ?> copy = original.call(registered);
        if (GENERATION.get() == g) held = new Held(g, copy);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: neobackports_component_defaults_memo applies (NeoBackports' component defaults are copied once per change){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        return copy;
    }

    private static void shadow(ImmutableMap<?, ?> kept, ImmutableMap<?, ?> fresh, int g) {
        if (GENERATION.get() != g) return;                 // a write ran meanwhile: nothing to compare
        SHADOW_CHECKS.incrementAndGet();
        boolean same = kept.size() == fresh.size();
        if (same) {
            var a = kept.entrySet().iterator();
            var b = fresh.entrySet().iterator();
            while (same && a.hasNext()) {
                var x = a.next();
                var y = b.next();
                same = x.getKey() == y.getKey() && x.getValue() == y.getValue();
            }
        }
        if (!same) {
            long m = SHADOW_MISMATCHES.incrementAndGet();
            if (m <= 20) LOGGER.warn("Bons and Furious: neobackports_component_defaults_memo shadow mismatch #{}: kept copy of {} entries vs fresh copy of {}", m, kept.size(), fresh.size());
        }
    }
}
