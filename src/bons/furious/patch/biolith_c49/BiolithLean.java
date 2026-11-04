package bons.furious.patch.biolith_c49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.datafixers.util.Either;
import com.terraformersmc.biolith.impl.biome.BiomeCoordinator;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.server.RegistryLayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious, group biolith_c49 (Biolith 1.0.1-beta.1, LGPL-3.0; both sides): helpers of two switches. Ours; no
 * Biolith code is carried. Idea: Collections Of Optimizations (biolith.reuseBiomeEntries, biolith.earlyRegistryCapture),
 * idea text only.
 *
 * biolith_biome_entries_lock_free (exact). Biolith wraps MultiNoiseBiomeSource.parameters() (m_274409_) with a handler that
 * enters synchronized(this) on every call, builds its biome list once and returns it; its getBiome hook asks parameters()
 * for every biome lookup, from every world-generation thread at once (a contended monitor). With the switch, an outer
 * wrapper keeps the list Biolith's handler returned the first time and returns it again without entering the monitor;
 * until it has one, Biolith's handler runs as shipped (lock, build, store). Why identical: Biolith writes its list
 * (biolith$biomeEntries) once per biome source, only while it is still null, never changes or clears it, and its handler
 * always returns it, so every later call would return the very object kept; the list (Climate.ParameterList) publishes
 * its content through final fields (values, index), so a thread that reads the reference without the lock sees it fully
 * built (JLS 17.5). Nothing else is done under that lock that a lock-free reader could miss.
 *
 * biolith_early_registry_capture (fix). Biolith learns the server's registries at the very end of WorldStem's constructor
 * (its MixinSaveLoader, TAIL); BCLib (WorldStem_Mixin, a @ModifyVariable right after the record's super() call) asks every
 * biome source for its biomes inside that constructor, which reaches Biolith before it has any registry and throws
 * (BiomeCoordinator.getBiomeLookupOrThrow) - the world does not load. The switch hands Biolith the same registries argument
 * right after super(), before BCLib's handler; setRegistryManager only stores registries.compositeAccess(), and
 * Biolith's own TAIL call then stores the composite of the (possibly BCLib-replaced) argument as shipped, so the
 * constructor ends with exactly the state it ends with when nothing throws. A null argument is left alone.
 *
 * Flags: -Dbons_and_furious.biolithBiomeEntriesLockFree=false, -Dbons_and_furious.biolithEarlyRegistryCapture=false.
 * Shadow (lock-free): -Dbons_and_furious.biolithBiomeEntriesLockFree.shadow=true runs Biolith's locked handler every time
 * and compares the object (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20).
 */
public final class BiolithLean {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean lockFree = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.biolithBiomeEntriesLockFree", "true"));
    public static final boolean LOCK_FREE_SHADOW = Boolean.getBoolean("bons_and_furious.biolithBiomeEntriesLockFree.shadow");
    public static volatile boolean early = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.biolithEarlyRegistryCapture", "true"));
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong(), EARLY_CALLS = new AtomicLong();
    private static volatile boolean lockAnnounced, earlyAnnounced;

    private BiolithLean() {
    }

    /** MultiNoiseBiomeSource.parameters(): the outer wrapper of the Either.map call Biolith wraps. */
    public static Object entries(Object built, Either<?, ?> either, Function<?, ?> left, Function<?, ?> right, Operation<Object> original) {
        if (!lockFree || built == null) return original.call(either, left, right);
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

    /** WorldStem.<init>, right after super(): hand Biolith the registries argument before anything asks it for biomes. */
    public static LayeredRegistryAccess<RegistryLayer> early(LayeredRegistryAccess<RegistryLayer> registries) {
        if (!early || registries == null) return registries;
        BiomeCoordinator.setRegistryManager(registries);
        EARLY_CALLS.incrementAndGet();
        if (!earlyAnnounced) {
            earlyAnnounced = true;
            LOGGER.info("Bons and Furious: biolith_early_registry_capture applies (Biolith gets the server registries at the start of WorldStem)");
        }
        return registries;
    }
}
