package bons.furious.patch.elysium_replacer;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.jadenxgamer.elysium_api.Elysium;
import net.jadenxgamer.elysium_api.impl.biome.ElysiumBiomeHelper;
import net.jadenxgamer.elysium_api.impl.biome_replacer.BiomeReplacerDataDriven;
import net.jadenxgamer.elysium_api.impl.registry.ElysiumRegistries;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.fml.loading.FMLLoader;
import org.slf4j.Logger;

/**
 * Bons and Furious switch elysium_lean_replacer (ElysiumAPI 1.1.3; both sides, it acts wherever biomes are generated).
 *
 * <p>ElysiumAPI injects at the head of every MultiNoiseBiomeSource.getNoiseBiome call (its MultiNoiseBiomeSourceMixin
 * handler elysium$getNoiseBiome): it computes the biome itself (asking FMLLoader's mod list whether TerraBlender is
 * installed on every call), and for sources with a dimension it streams its data-driven biome-replacer registry into a
 * new list, then replaceBiomeIfNeeded allocates a HashSet and a new java.util.Random (whose constructor updates a static
 * seed counter shared by every thread of the JVM) and walks the code and data-driven replacers; when nothing is replaced
 * the original method computes the biome again. Chunk biome filling and Distant Horizons' world generation call it
 * thousands of times per chunk.
 *
 * <p>The switch runs the same handler with the same decisions and leaner bookkeeping (our MixinSquared @TargetHandler
 * injection at the head of Elysium's handler; Elysium's code is GPL-2.0 / LGPL-2.1 and this is a modified version of its
 * handler):
 * <ul>
 * <li>the TerraBlender question is answered once (the mod list does not change after loading); the current biome is
 * computed exactly as before, by Elysium's own TerraBlender helper or by parameters().findValue(sampler.sample(...));</li>
 * <li>the data-driven replacer list is read from the registry once per RegistryAccess object (Elysium.registryAccess)
 * and reused while that object stays the same: registry contents are frozen, the list has the registry's order;</li>
 * <li>the HashSet is dropped: Elysium's loop body runs once (it never changes the biome it tests), so the set only ever
 * holds one element;</li>
 * <li>Random.setSeed(s) followed by nextDouble() is computed directly ({@link #firstDouble}, java.util.Random's specified
 * algorithm): every draw is seeded first, so the unseeded state of Elysium's new Random() is never used.</li>
 * </ul>
 * Everything else - the replacers checked and their order, the seeds, the registry lookup of a code replacer's biome,
 * the comparison with the current biome and the return value set on Elysium's callback - is unchanged, and the double
 * biome computation (Elysium's and the original method's) stays: the climate tree's per-thread warm start depends on it.
 * Runtime switch -Dbons_and_furious.elysiumLeanReplacer=false. Shadow mode -Dbons_and_furious.elysiumLeanReplacer.shadow=true:
 * Elysium's handler runs as shipped, and the lean decision for the biome it computed is compared with what it did
 * (SHADOW_CHECKS / SHADOW_MISMATCHES, at most 20 warnings).
 */
public final class ElysiumLean {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long MULTIPLIER = 0x5DEECE66DL, ADDEND = 0xBL, MASK = (1L << 48) - 1;

    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.elysiumLeanReplacer", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.elysiumLeanReplacer.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static volatile Boolean terraBlender;
    private static volatile Snapshot dataDriven;
    private static boolean announced;

    private record Snapshot(RegistryAccess access, List<BiomeReplacerDataDriven> list) {}

    private ElysiumLean() {
    }

    /** Elysium's FMLLoader.getLoadingModList().getModFileById("terrablender") != null, asked once. */
    public static boolean terraBlender() {
        Boolean b = terraBlender;
        if (b == null) terraBlender = b = FMLLoader.getLoadingModList().getModFileById("terrablender") != null;
        return b;
    }

    /** Elysium.registryAccess.registryOrThrow(BIOME_REPLACER).stream().toList(), once per RegistryAccess object. */
    public static List<BiomeReplacerDataDriven> dataDriven() {
        RegistryAccess access = Elysium.registryAccess;
        Snapshot s = dataDriven;
        if (s != null && s.access == access) return s.list;
        List<BiomeReplacerDataDriven> list = access.m_175515_(ElysiumRegistries.BIOME_REPLACER).m_123024_().toList();
        dataDriven = new Snapshot(access, list);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: elysium_lean_replacer runs ElysiumAPI's biome replacer check without per-lookup lists, sets and random generators");
        }
        return list;
    }

    /** Elysium's replaceBiomeIfNeeded: the same checks, seeds and returns, without the set and the Random object. */
    public static Holder<Biome> replace(int x, int z, Holder<Biome> current, List<ElysiumBiomeHelper.BiomeReplacer> code,
                                        List<BiomeReplacerDataDriven> data, long worldSeed) {
        if (current == null) return null;
        for (ElysiumBiomeHelper.BiomeReplacer r : code) {
            if (r.replaceBiomes().m_203333_(current)) {
                long seed = seed(x / r.size(), z / r.size(), worldSeed) ^ r.uniqueId().hashCode();
                if (firstDouble(seed) < r.rarity()) return Elysium.registryAccess.m_175515_(Registries.f_256952_).m_246971_(r.withBiome());
            }
        }
        for (BiomeReplacerDataDriven r : data) {
            if (r.replaceBiomes().m_203333_(current)) {
                long seed = seed(x / r.size(), z / r.size(), worldSeed) ^ r.uniqueId().hashCode();
                if (firstDouble(seed) < r.rarity()) return r.withBiome();
            }
        }
        return current;
    }

    /** Elysium's makeCoordinatesIntoSeed, the same expression. */
    static long seed(int scaledX, int scaledZ, long worldSeed) {
        return (31L * scaledX + 17L ^ 37L * scaledZ + 23L ^ worldSeed) * 25214903917L;
    }

    /** new Random() then setSeed(seed) then nextDouble(): java.util.Random's specified LCG, first double after seeding. */
    public static double firstDouble(long seed) {
        long s = (seed ^ MULTIPLIER) & MASK;
        s = (s * MULTIPLIER + ADDEND) & MASK;
        int high = (int) (s >>> (48 - 26));
        s = (s * MULTIPLIER + ADDEND) & MASK;
        int low = (int) (s >>> (48 - 27));
        return (((long) high << 27) + low) * 0x1.0p-53;
    }

    /** Shadow mode: Elysium's handler decided {@code replacedTo} (null = no replacement) for {@code current}. */
    public static void shadowCompare(int x, int z, Holder<Biome> current, List<ElysiumBiomeHelper.BiomeReplacer> code, long worldSeed, Holder<Biome> replacedTo) {
        SHADOW_CHECKS.incrementAndGet();
        Holder<Biome> lean = replace(x, z, current, code, dataDriven(), worldSeed);
        Holder<Biome> expect = lean.equals(current) ? null : lean;
        boolean same = expect == null ? replacedTo == null : expect.equals(replacedTo);
        if (same) return;
        long n = SHADOW_MISMATCHES.incrementAndGet();
        if (n <= 20) LOGGER.warn("Bons and Furious: elysium_lean_replacer shadow mismatch {} at {},{}: Elysium {}, lean {}", n, x, z, replacedTo, expect);
    }
}
