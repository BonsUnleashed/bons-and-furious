package bons.furious.patch.biolith_c49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.datafixers.util.Either;
import com.terraformersmc.biolith.impl.biome.InterfaceBiomeSource;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious, group biolith_c49 (Biolith, LGPL-3.0; 1.21.1 tested build: Biolith 3.0.14 for NeoForge; both sides):
 * helper of biolith_biome_entries_lock_free. Ours; no Biolith code is carried. Idea: Collections Of Optimizations
 * (biolith.reuseBiomeEntries), idea text only.
 *
 * biolith_biome_entries_lock_free (exact). Biolith wraps MultiNoiseBiomeSource.parameters() with a handler that enters
 * synchronized(this) on every call, builds its biome list while its field biolith$biomeEntries is null and returns that
 * field; its getNoiseBiome hook asks parameters() at least once for every biome lookup (TerraBlender's compat lookup, then
 * the vanilla one when that found nothing), from every world-generation thread at once (a contended monitor). Biolith
 * 3.0.14 also clears the field (biolith$refreshBiomeEntries, called at the head of ChunkGenerator.refreshFeaturesPerStep,
 * which NeoForge runs for every LevelStem after applying biome modifiers when a server starts); the next call then
 * builds the list again.
 *
 * With the switch, an outer wrapper reads Biolith's field through Biolith's own getter (biolith$getBiomeEntries) and,
 * when it holds a list, returns that list without entering the monitor; while it is null (not built yet, or cleared),
 * Biolith's handler runs as shipped (lock, build, store, return). Why identical: Biolith's handler returns exactly that
 * field once it is set, so the wrapper returns the very object the handler would; the list (Climate.ParameterList)
 * publishes its content through final fields (values, index), so a thread that reads the reference without the lock
 * sees it fully built (JLS 17.5). Nothing else is done under that lock that a lock-free reader could miss. Keeping no
 * copy of its own, the wrapper follows every clear and rebuild.
 *
 * Flag: -Dbons_and_furious.biolithBiomeEntriesLockFree=false. Shadow: -Dbons_and_furious.biolithBiomeEntriesLockFree.shadow=true
 * runs Biolith's locked handler every time and compares the object (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20).
 */
public final class BiolithLean {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean lockFree = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.biolithBiomeEntriesLockFree", "true"));
    public static final boolean LOCK_FREE_SHADOW = Boolean.getBoolean("bons_and_furious.biolithBiomeEntriesLockFree.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static volatile boolean lockAnnounced;

    private BiolithLean() {
    }

    /** MultiNoiseBiomeSource.parameters(): the outer wrapper of the Either.map call Biolith wraps. */
    public static Object entries(Object source, Either<?, ?> either, Function<?, ?> left, Function<?, ?> right, Operation<Object> original) {
        if (!lockFree || !(source instanceof InterfaceBiomeSource biolith)) return original.call(either, left, right);
        Object built = biolith.biolith$getBiomeEntries();
        if (built == null) return original.call(either, left, right);
        if (LOCK_FREE_SHADOW) {
            Object locked = original.call(either, left, right);
            SHADOW_CHECKS.incrementAndGet();
            if (locked != built) {
                long m = SHADOW_MISMATCHES.incrementAndGet();
                if (m <= 20) LOGGER.warn("Bons and Furious: biolith_biome_entries_lock_free shadow mismatch #{}: Biolith returned another list", m);
            }
            return locked;
        }
        if (!lockAnnounced) {
            lockAnnounced = true;
            LOGGER.info("Bons and Furious: biolith_biome_entries_lock_free applies (Biolith's biome list is read without its lock once built)");
        }
        return built;
    }
}
