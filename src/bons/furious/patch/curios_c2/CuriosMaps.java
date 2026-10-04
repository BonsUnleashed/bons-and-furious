package bons.furious.patch.curios_c2;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious fix curios_thread_safe_caches (Curios API 5.14.1+1.20.1, Forge; both sides, matters wherever two threads
 * share the classes: the client with its integrated server). Curios is LGPL-3.0-or-later; no Curios code is carried.
 *
 * Two of Curios' static caches are plain HashMaps filled with computeIfAbsent from whichever thread asks first:
 * SlotAttribute.SLOT_ATTRIBUTES (slot id -> the slot's attribute; SlotAttribute.getOrCreate, which Curios' own
 * MixinSlotAttribute answers at HEAD with SLOT_ATTRIBUTES.computeIfAbsent(id, SlotAttributeWrapper::new)) and
 * CuriosImplMixinHooks.UUIDS (slot id + index -> the slot's modifier UUID; getUuid). In single player both are reached from
 * the render thread (item tooltips build curio attribute modifiers) and from the server thread (equipment changes, slot
 * modifiers, loot functions). HashMap is not thread-safe: since JDK 9 a computeIfAbsent whose map is changed by another
 * thread meanwhile throws ConcurrentModificationException, concurrent inserts can be lost (two SlotAttribute objects for
 * one slot id, so modifiers are added under one attribute and removed under the other) and a resize race can corrupt the
 * table.
 *
 * Each map has exactly one accessor (byte scan of every installed jar: the private fields are only read in getOrCreate,
 * by Curios' own handler merged into it, and in getUuid), so the fix runs each accessor, whole, under a lock of its own
 * (MixinExtras @WrapMethod; Curios' HEAD inject is part of the wrapped method). The maps stay the same HashMaps and see the
 * same calls in the same order, so a single thread gets exactly the same results, null keys and exceptions included (a
 * ConcurrentHashMap would reject null keys); two threads can no longer interleave inside them.
 *
 * Runtime flag: -Dbons_and_furious.curiosThreadSafeCaches=false runs both methods without the locks. There is no shadow
 * mode: there is nothing to compare at run time (the answers are the HashMaps' own).
 */
public final class CuriosMaps {
    /** Runtime switch (the config switch acts when the classes are transformed). */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.curiosThreadSafeCaches", "true"));
    private static final Object SLOT_ATTRIBUTES_LOCK = new Object();
    private static final Object UUIDS_LOCK = new Object();
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private CuriosMaps() {
    }

    /** The @WrapMethod handler body for SlotAttribute.getOrCreate(id). */
    public static <T> T slotAttribute(String id, Operation<T> original) {
        if (!enabled) return original.call(id);
        announce();
        synchronized (SLOT_ATTRIBUTES_LOCK) {
            return original.call(id);
        }
    }

    /** The @WrapMethod handler body for CuriosImplMixinHooks.getUuid(slotContext). */
    public static <T> T slotUuid(Object slotContext, Operation<T> original) {
        if (!enabled) return original.call(slotContext);
        announce();
        synchronized (UUIDS_LOCK) {
            return original.call(slotContext);
        }
    }

    private static void announce() {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: curios_thread_safe_caches: Curios' shared slot-attribute and slot-UUID caches are filled under a lock");
        }
    }
}
