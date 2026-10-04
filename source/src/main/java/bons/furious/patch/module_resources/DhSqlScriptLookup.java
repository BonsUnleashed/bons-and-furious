package bons.furious.patch.module_resources;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Bons and Furious switch distanthorizons_sql_script_lookup_index (Distant Horizons, LGPL-3.0; 1.21.1 tested build
 * DistantHorizons-3.3.3-1.21.1-fabric-neoforge; both sides). No Distant Horizons code here.
 *
 * Every Distant Horizons database (four per dimension: full data V1 and V2, chunk hashes, beacon beams) runs
 * DatabaseUpdater.runAutoUpdateScripts when it opens, and getAutoUpdateScripts reads "sqlScripts/scriptList.txt" and the
 * 12 scripts it names through DatabaseUpdater's class loader: 13 lookups that each make the game layer's class loader ask
 * every game-layer jar (341 Server-thread samples of our own 1.20.1 client load window, when the integrated server
 * loads its dimensions). The lookups go through GameLayerResources (same answer, see there); the scripts are still read
 * from the same jar entries every time. With this switch off, or where it declines, DH's calls run unchanged.
 *
 * Ported to 1.21.1: DatabaseUpdater.getAutoUpdateScripts decompiles identically in DH 3.3.2 (1.20.1) and 3.3.3 (1.21.1)
 * (the same two DatabaseUpdater.class.getClassLoader().getResourceAsStream calls); the engine behind it was re-derived
 * for securejarhandler 3.0.8 / modlauncher 11.0.5 (GameLayerResources).
 */
public final class DhSqlScriptLookup {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.distanthorizonsSqlScriptLookupIndex=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.distanthorizonsSqlScriptLookupIndex", "true"));
    /** Shadow mode for rigs: every indexed answer is compared with the class loader's own getResource. */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.distanthorizonsSqlScriptLookupIndex.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Counters (read by probes): lookups answered from the index, lookups left to the original call. */
    public static final AtomicLong ANSWERED = new AtomicLong(), DECLINED = new AtomicLong();

    private DhSqlScriptLookup() {
    }

    public static InputStream stream(ClassLoader cl, String name, Supplier<InputStream> original) {
        if (!enabled) return original.get();
        return ResourceSite.stream("distanthorizons_sql_script_lookup_index", cl, name, original, SHADOW, SHADOW_CHECKS, SHADOW_MISMATCHES, ANSWERED, DECLINED);
    }
}
