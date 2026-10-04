package bons.furious.patch.module_resources;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Bons and Furious switch iceandfire_tabula_lookup_index (Ice and Fire 2.1.13-1.20.1-beta-5, client). No Ice and Fire
 * code here.
 *
 * Ice and Fire's client setup loads every dragon and sea serpent pose model with
 * TabulaModelHandlerHelper.loadTabulaModel, which asks Citadel's class loader for "/assets/iceandfire/models/tabula/...
 * .tbl"; each lookup makes the game layer's class loader ask all ~530 jars (72 main-thread samples of our own
 * client load window). The lookup goes through GameLayerResources (same answer, see there); with this switch off, or
 * where it declines, Ice and Fire's call runs unchanged.
 */
public final class IceAndFireTabulaLookup {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.iceandfireTabulaLookupIndex=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.iceandfireTabulaLookupIndex", "true"));
    /** Shadow mode for rigs: every indexed answer is compared with the class loader's own getResource. */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.iceandfireTabulaLookupIndex.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Counters (read by probes): lookups answered from the index, lookups left to the original call. */
    public static final AtomicLong ANSWERED = new AtomicLong(), DECLINED = new AtomicLong();

    private IceAndFireTabulaLookup() {
    }

    public static InputStream stream(ClassLoader cl, String name, Supplier<InputStream> original) {
        if (!enabled) return original.get();
        return ResourceSite.stream("iceandfire_tabula_lookup_index", cl, name, original, SHADOW, SHADOW_CHECKS, SHADOW_MISMATCHES, ANSWERED, DECLINED);
    }
}
