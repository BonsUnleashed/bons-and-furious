package bons.furious.patch.curios_c2;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious fix curios_thread_safe_caches (Curios API, NeoForge; 1.21.1 tested build curios-neoforge-9.5.1+1.21.1;
 * both sides, matters wherever two threads share the classes: the client with its integrated server). Curios is
 * LGPL-3.0-or-later; no Curios code is carried.
 *
 * Curios keeps the slot attributes in a static plain HashMap, SlotAttribute.SLOT_ATTRIBUTES (slot id -> the slot's
 * attribute holder), filled by SlotAttribute.getOrCreate with SLOT_ATTRIBUTES.computeIfAbsent(id, k -> new
 * Holder.Direct(new SlotAttribute(id))) from whichever thread asks first. In single player it is reached from the render
 * thread (item tooltips build curio attribute modifiers: CuriosImplMixinHooks.getAttributeModifiers -> addSlotModifier ->
 * getOrCreate) and from the server thread (equipment changes, slot modifiers, the set_curio_attributes loot function,
 * CurioStacksHandler, CuriosEventHandler). HashMap is not thread-safe: a computeIfAbsent whose map is changed by another
 * thread meanwhile throws ConcurrentModificationException, concurrent inserts can be lost (two attribute holders for one
 * slot id, so modifiers are added under one attribute and removed under the other) and a resize race can corrupt the
 * table.
 *
 * The map has exactly one accessor: the private field is read only in getOrCreate (byte scan of every pinned 1.21.1
 * target jar, jar-in-jar included: SLOT_ATTRIBUTES appears only in SlotAttribute.class; no mixin targets SlotAttribute),
 * so the fix runs that accessor, whole, under a lock of its own (MixinExtras @WrapMethod). The map stays the same HashMap
 * and sees the same calls in the same order, so a single thread gets exactly the same results, null keys and exceptions
 * included (a ConcurrentHashMap would reject null keys); two threads can no longer interleave inside it. Inside the lock
 * only Curios' mapping function runs (lambda$getOrCreate$0: one SlotAttribute and one Holder.Direct; it never calls back
 * into getOrCreate and takes no other lock), so the lock cannot deadlock.
 *
 * Ported to 1.21.1: the 1.20.1 key also locked CuriosImplMixinHooks.getUuid around a second static HashMap (UUIDS, slot
 * id + index -> modifier UUID). Curios 9.5.1 identifies slot modifiers by ResourceLocation (CuriosImplMixinHooks.getSlotId
 * builds it per call, no map); getUuid is gone and UUIDS is only assigned in &lt;clinit&gt; and never read, so that half
 * has nothing left to protect and is not ported. getOrCreate now returns a Holder&lt;Attribute&gt;.
 *
 * Runtime flag: -Dbons_and_furious.curiosThreadSafeCaches=false runs getOrCreate without the lock. There is no shadow
 * mode: there is nothing to compare at run time (the answers are the HashMap's own).
 */
public final class CuriosMaps {
    /** Runtime switch (the config switch acts when the class is transformed). */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.curiosThreadSafeCaches", "true"));
    private static final Object SLOT_ATTRIBUTES_LOCK = new Object();
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

    private static void announce() {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: curios_thread_safe_caches: Curios' shared slot-attribute cache is filled under a lock");
        }
    }
}
