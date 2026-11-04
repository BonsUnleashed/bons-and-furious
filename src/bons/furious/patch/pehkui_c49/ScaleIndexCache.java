package bons.furious.patch.pehkui_c49;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import virtuoel.pehkui.api.ScaleData;

/**
 * Bons and Furious switch pehkui_scale_cache_all_types (Pehkui 3.8.2+1.20.1-forge, MIT; both sides). Ours; no Pehkui code is
 * carried.
 *
 * What Pehkui does. Every entity answers getScaleData(type) (Pehkui's pehkui_getScaleData, merged into Entity) from a
 * nine-slot array for nine of its 32 built-in scale types (ScaleCachingUtils.CACHED, a linear search) and for every other
 * type from its per-entity map, which by default is a synchronized map (one lock per lookup). Pehkui's own tick asks every
 * registered type of every entity on every tick, and most scale reads ask through the same method, so 23 built-in types
 * and every type another mod registers pay the search plus a locked map lookup each time.
 *
 * What the switch does. Every ScaleType gets a dense index at construction (ScaleTypeIndexMixin) and every entity one more
 * array, indexed by it (EntityScaleIndexMixin). Where Pehkui, after its nine-slot search found nothing, asks its map, the
 * slot answers when it is filled; otherwise the map answers and a non-null answer fills the slot (never a null, never an
 * object the map has not returned). Pehkui's nine cached types return before that point and run Pehkui's code unchanged.
 *
 * Why the result is identical. Pehkui puts a type's ScaleData into its map once, when it constructs it (after a null
 * placeholder that marks the construction in progress), and nothing in Pehkui replaces or removes a map entry; its own
 * nine-slot cache relies on the same rule. So the object a slot holds is the object Pehkui's map returns for that type for
 * the rest of the entity's life, and a lookup during construction (the placeholder) still finds an empty slot and gets
 * Pehkui's own answer. A slot is published by a volatile write of the array field after it is set; a reader that misses
 * a write asks Pehkui's map as before.
 *
 * -Dbons_and_furious.pehkuiScaleCacheAllTypes=false switches it off at run time.
 * -Dbons_and_furious.pehkuiScaleCacheAllTypes.shadow=true: every slot answer is also compared with the entity's map
 * (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20).
 */
public final class ScaleIndexCache {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.pehkuiScaleCacheAllTypes", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.pehkuiScaleCacheAllTypes.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static final AtomicInteger NEXT = new AtomicInteger();
    private static volatile boolean announced;

    private ScaleIndexCache() {}

    /** The next ScaleType index (called once per ScaleType construction). */
    public static int nextIndex() {
        return NEXT.getAndIncrement();
    }

    /** {@code slots} with {@code data} at {@code index}, grown when needed (room for every type constructed so far). */
    public static ScaleData[] store(ScaleData[] slots, int index, ScaleData data) {
        ScaleData[] a = slots;
        if (a == null || index >= a.length) {
            int n = Math.max(index + 1, NEXT.get());
            a = a == null ? new ScaleData[n] : Arrays.copyOf(a, n);
        }
        a[index] = data;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: pehkui_scale_cache_all_types applies (Pehkui answers every scale type from a per-entity array){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        return a;
    }

    /** Shadow mode: the slot's object must be the one the entity's own map holds for that type. */
    public static void shadow(ScaleData slot, Map<?, ?> scales, Object type) {
        SHADOW_CHECKS.incrementAndGet();
        Object fromMap = scales.get(type);
        if (fromMap != slot) {
            long m = SHADOW_MISMATCHES.incrementAndGet();
            if (m <= 20) LOGGER.warn("Bons and Furious: pehkui_scale_cache_all_types shadow mismatch #{}: type {} slot {} map {}", m, type, slot, fromMap);
        }
    }
}
