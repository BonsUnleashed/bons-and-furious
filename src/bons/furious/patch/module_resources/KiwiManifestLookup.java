package bons.furious.patch.module_resources;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Bons and Furious switch kiwi_manifest_lookup_index (Kiwi 11.8.28, both sides). No Kiwi code here.
 *
 * Kiwi's constructor asks, for every installed mod, the thread's context class loader for "/<modid>.kiwi.json"
 * (AnnotatedTypeLoader.get); only Kiwi and Snow! Real Magic ship one. Each miss makes the game layer's class loader ask
 * all ~530 jars, on the serial mod-construction thread (fml.toml maxThreads = 1): 86 samples of our own client load
 * window. The lookup goes through GameLayerResources (same answer, see there); with this switch off, or where it
 * declines, Kiwi's call runs unchanged.
 */
public final class KiwiManifestLookup {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.kiwiManifestLookupIndex=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.kiwiManifestLookupIndex", "true"));
    /** Shadow mode for rigs: every indexed answer is compared with the class loader's own getResource. */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.kiwiManifestLookupIndex.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Counters (read by probes): lookups answered from the index, lookups left to the original call. */
    public static final AtomicLong ANSWERED = new AtomicLong(), DECLINED = new AtomicLong();

    private KiwiManifestLookup() {
    }

    public static InputStream stream(ClassLoader cl, String name, Supplier<InputStream> original) {
        if (!enabled) return original.get();
        return ResourceSite.stream("kiwi_manifest_lookup_index", cl, name, original, SHADOW, SHADOW_CHECKS, SHADOW_MISMATCHES, ANSWERED, DECLINED);
    }
}
