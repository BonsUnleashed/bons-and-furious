package bons.furious.patch.farmersdelight_c2;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.GuiOverlayManager;
import net.minecraftforge.client.gui.overlay.NamedGuiOverlay;
import org.slf4j.Logger;

/**
 * Bons and Furious switch farmersdelight_overlay_lookup_memo (Farmer's Delight 1.3.4 on Forge 47.4.16, client only).
 * No Farmer's Delight or Forge code is carried here.
 *
 * Farmer's Delight's two HUD listeners (NourishmentHungerOverlay and ComfortHealthOverlay.onRenderGuiOverlayPost) run
 * for every HUD overlay Forge draws, every frame, and each starts with
 * {@code event.getOverlay() == GuiOverlayManager.findOverlay(ID)}: a hash lookup of the same id in Forge's overlay table
 * (OVERLAYS_BY_NAME). The table is cold by the time the HUD is drawn, so the lookups showed up at 0.26-0.46% of the
 * render thread in review 8's client profiles.
 *
 * Each call site (one instance of this class per listener) now remembers its last answer together with the two things
 * the answer is a function of: the id object it was asked for and the table object it came from. A later call with the
 * very same id object while Forge's field still holds the very same table returns the remembered overlay. Identical
 * because findOverlay (fingerprinted) is exactly {@code (NamedGuiOverlay) OVERLAYS_BY_NAME.get(id)}: the table is a
 * Guava ImmutableMap (no mutation is possible, so the same table always maps the same key to the same value) and the id
 * is remembered only when its class is exactly ResourceLocation, whose namespace and path are final strings (so its
 * hash and equality never change). A re-initialised table (Forge's init() always builds a new map) or a reassigned id
 * field (Farmer's Delight's fields are not final) is a different object, so the lookup runs again. A null result is
 * remembered like any other; a null id, an id of another class, or a table that is still null (before Forge's init(),
 * where the original throws) always go to the original. An answer is stored only when the table read before and after
 * the original call is the same object, so it always belongs to the table it is stored with. The remembered entry has
 * final fields and is published with one reference write: any entry a thread sees is internally consistent, and any
 * entry whose id and table match is correct whichever thread stored it.
 *
 * The table is read through a getter MethodHandle on Forge's private static field (as other switches read private
 * fields); if the field is not there the switch stays inactive and logs one WARN.
 * SHADOW MODE for rigs: -Dbons_and_furious.farmersdelightOverlayLookupMemo.shadow=true runs the original on every
 * would-be hit, returns the original's answer and counts answers that differ from the remembered one (WARN, at most 20).
 */
public final class OverlayLookupMemo {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.farmersdelightOverlayLookupMemo=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.farmersdelightOverlayLookupMemo", "true"));
    /** Shadow mode (see the class comment). */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.farmersdelightOverlayLookupMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong();
    public static final AtomicLong SHADOW_MISMATCHES = new AtomicLong();
    /** The call site in NourishmentHungerOverlay.onRenderGuiOverlayPost. */
    public static final OverlayLookupMemo NOURISHMENT = new OverlayLookupMemo("NourishmentHungerOverlay");
    /** The call site in ComfortHealthOverlay.onRenderGuiOverlayPost. */
    public static final OverlayLookupMemo COMFORT = new OverlayLookupMemo("ComfortHealthOverlay");
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Getter of GuiOverlayManager.OVERLAYS_BY_NAME, type ()Object; null when the field is not the tested layout. */
    private static final MethodHandle TABLE;
    private static volatile boolean announced;

    static {
        MethodHandle table = null;
        try {
            Field f = GuiOverlayManager.class.getDeclaredField("OVERLAYS_BY_NAME");
            if (!Modifier.isStatic(f.getModifiers())) throw new NoSuchFieldException("OVERLAYS_BY_NAME is not static");
            f.setAccessible(true);
            table = MethodHandles.lookup().unreflectGetter(f).asType(MethodType.methodType(Object.class));
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: farmersdelight_overlay_lookup_memo is inactive because Forge's overlay table is not the supported 47.4.16 layout ({})", t.toString());
        }
        TABLE = table;
    }

    /** One remembered answer. Final fields: a thread that sees the reference sees the whole entry. */
    private static final class Entry {
        final ResourceLocation id;
        final Object table;
        final NamedGuiOverlay result;

        Entry(ResourceLocation id, Object table, NamedGuiOverlay result) {
            this.id = id;
            this.table = table;
            this.result = result;
        }
    }

    private final String site;
    private Entry last;

    private OverlayLookupMemo(String site) {
        this.site = site;
    }

    /** Forge's current overlay table (the object findOverlay reads). */
    private static Object table() {
        try {
            return (Object) TABLE.invokeExact();
        } catch (Throwable t) {
            throw new IllegalStateException(t);   // a static getter cannot fail once resolved
        }
    }

    /** In place of GuiOverlayManager.findOverlay(id) at this call site. */
    public NamedGuiOverlay find(ResourceLocation id, Operation<NamedGuiOverlay> original) {
        if (!enabled || TABLE == null) return original.call(id);
        Object table = table();
        Entry e = last;
        if (e != null && e.id == id && e.table == table) {          // stored entries always have a non-null table
            return SHADOW ? shadow(e, original) : e.result;
        }
        NamedGuiOverlay result = original.call(id);
        if (table != null && id != null && id.getClass() == ResourceLocation.class && table() == table) {
            last = new Entry(id, table, result);
            if (!announced) {
                announced = true;
                LOGGER.info(SHADOW ? "Bons and Furious: farmersdelight_overlay_lookup_memo SHADOW MODE: every remembered overlay lookup is checked against Forge's"
                        : "Bons and Furious: farmersdelight_overlay_lookup_memo: Farmer's Delight's HUD listeners reuse their overlay lookup while Forge's overlay table is unchanged");
            }
        }
        return result;
    }

    private NamedGuiOverlay shadow(Entry e, Operation<NamedGuiOverlay> original) {
        NamedGuiOverlay fresh = original.call(e.id);
        SHADOW_CHECKS.incrementAndGet();
        if (fresh != e.result) {
            long n = SHADOW_MISMATCHES.incrementAndGet();
            if (n <= 20) LOGGER.warn("Bons and Furious: farmersdelight_overlay_lookup_memo SHADOW MISMATCH {} at {}: remembered {} but Forge answers {} for {}",
                    n, site, e.result, fresh, e.id);
        }
        return fresh;
    }
}
