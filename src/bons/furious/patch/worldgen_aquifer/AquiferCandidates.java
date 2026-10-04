package bons.furious.patch.worldgen_aquifer;

import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.levelgen.Aquifer;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

/**
 * Bons and Furious switches vanilla_aquifer_candidate_cache and vanilla_aquifer_high_air (Minecraft 1.20.1 world
 * generation, server side; Forge 47.4.16; tested with YUNG's Cave Biomes 2.0.5's two Aquifer mixins and C2ME 0.2.0
 * alpha.12 with optimizeAquifer off). SRG member names. Idea: C2ME pull requests 557, 558 and 559, idea text only.
 *
 * What it costs. Aquifer$NoiseBasedAquifer.computeSubstance runs for every block whose final density is not above zero:
 * the air above the terrain (most of a 768-high chunk), water and caves, and every block of a column height query. Each
 * call walks the twelve candidate cells around the block: grid index, location cache read, BlockPos unpacking, squared
 * distance and a three-way ranking with data-dependent branches; then it looks the winners' fluid statuses up again by
 * unpacking their positions and dividing them back into cells. In the pack's pregeneration JFR (srv10_jfr1) that is
 * 8.2% self of all samples (computeSubstance 17.3% inclusive, the rest is computeFluid).
 *
 * What the switch does (AquiferCandidateMixin, merged into NoiseBasedAquifer). Per aquifer, a small table of grid cells
 * (eight slots, by the low bit of each grid coordinate) holds the twelve candidates' unpacked positions and their cache
 * indices, read from the aquifer's own location cache. A call ranks the twelve squared distances branch-free (keys
 * distance << 4 | 15 - slot: equal distances rank the later slot first, exactly as vanilla's >= insertion does), reads
 * the winners' statuses by index and computes a missing one with vanilla's own computeFluid, in vanilla's order (rank 1;
 * rank 2 and 3 only on the branches where vanilla reaches them). Pressures, similarities and the fluid picker are
 * vanilla's own methods, called with the same arguments at the same points. A cell whose twelve locations are not all
 * in the location cache yet, or that lies outside the aquifer's grid, runs the original method (vanilla's code fills the
 * locations, so the location cache and every positional random draw are vanilla's).
 *
 * vanilla_aquifer_high_air (AquiferHighAirMixin, FluidStatusLevelAccessor): when rank 2's and rank 3's statuses are
 * already in the cache and all three fluid levels are at least 5 below the block, the call returns at once. Vanilla
 * would only read those two cached statuses and compute three pressures that are 0 or at most -4.4 (both statuses are
 * air at that y, the barrier noise is never sampled because the shaped offset is below -2), so the block stays air and
 * the flag true. No computeFluid call is added, skipped or moved: the shortcut needs both statuses already cached.
 *
 * Why the order of computeFluid matters: YUNG's Cave Biomes injects at its RETURN and looks up a biome through the
 * climate tree, whose thread-local last result breaks ties for later lookups.
 *
 * Census (once per run, first use): every mixin merged into NoiseBasedAquifer must be ours or one of VERIFIED (SHA-256 of
 * its class file; both YUNG mixins only touch computeFluid or nothing). Unknown code (for example C2ME's own aquifer
 * rewrite when optimizeAquifer is on): one INFO line and the original method runs everywhere. Only the exact class
 * NoiseBasedAquifer takes the fast path.
 *
 * -Dbons_and_furious.vanillaAquiferCandidateCache=false / -Dbons_and_furious.vanillaAquiferHighAir=false: the original
 * (checked per call). -Dbons_and_furious.vanillaAquiferCandidateCache.shadow=true (verification runs only): every call
 * runs the original first (its result is returned, so the world stays vanilla), then the fast path on the state the
 * original left, which must need no status the original did not compute; compared: result, flag, the set of statuses
 * the original computed versus the ones the fast path would have computed, and the fast path's ranking against a
 * reference ranking written from vanilla's loop (SHADOW_CHECKS / SHADOW_MISMATCHES, SHADOW_HIGH_AIR).
 */
public final class AquiferCandidates {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.vanillaAquiferCandidateCache", "true"));
    public static volatile boolean highAirEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.vanillaAquiferHighAir", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.vanillaAquiferCandidateCache.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong(), SHADOW_HIGH_AIR = new AtomicLong();

    /** Returned by the table path when it cannot answer a call (the original runs); never a block state. */
    public static final Object NO_ANSWER = new Object();

    /** One grid cell's entry: [0..2] key gx, gy, gz; [3..14] cache index per slot; [15..26] x; [27..38] y; [39..50] z. */
    public static final int STRIDE = 52, IDX = 3, PX = 15, PY = 27, PZ = 39;
    /** Candidate slot k in vanilla's loop order (x outer 0..1, y -1..1, z inner 0..1). */
    public static final int[] DX = new int[12], DY = new int[12], DZ = new int[12];

    static {
        for (int k = 0; k < 12; k++) {
            DX[k] = k / 6;
            DY[k] = (k % 6) / 2 - 1;
            DZ[k] = k % 2;
        }
    }

    /** Other mods' NoiseBasedAquifer mixins whose code was read, by SHA-256 of the class file. */
    static final Map<String, String> VERIFIED = Map.of(
            "com.yungnickyoung.minecraft.yungscavebiomes.mixin.frosted_caves.AquiferMixin", "dfcff9dd18cc2910d80530dee472e7c4d1706ba0dd26f51d8760399f1c2f843c",
            "com.yungnickyoung.minecraft.yungscavebiomes.mixin.marble_caves.AquiferMixin", "006b930f7cf3470e544973302de558e878d6a5dc0bb0dc8dc7fb6ae683cf98df");

    /** 0 = not taken yet, 1 = armed, -1 = stood down. */
    private static volatile int census;
    private static volatile String censusDetail = "not taken";

    private AquiferCandidates() {
    }

    /** True when the census allows the fast path (taken on first use). */
    public static boolean armed() {
        int c = census;
        if (c == 0) c = takeCensus();
        return c > 0;
    }

    public static String censusDetail() {
        return censusDetail;
    }

    private static synchronized int takeCensus() {
        if (census != 0) return census;
        String why = check(Aquifer.NoiseBasedAquifer.class, Aquifer.NoiseBasedAquifer.class.getClassLoader());
        censusDetail = why == null ? "armed" : why;
        census = why == null ? 1 : -1;
        if (why == null) LOGGER.info("Bons and Furious: vanilla_aquifer_candidate_cache applies (aquifer candidates ranked from a per-cell table{}){}",
                highAirEnabled ? ", with vanilla_aquifer_high_air's early air return" : "", SHADOW ? " - shadow verification on" : "");
        else LOGGER.info("Bons and Furious: vanilla_aquifer_candidate_cache stands down: {}; every aquifer runs the original computeSubstance", why);
        return census;
    }

    /** Null when every merged mixin is ours or verified, else the reason. Public for the offline proof. */
    public static String check(Class<?> aquifer, ClassLoader loader) {
        try {
            TreeSet<String> merged = new TreeSet<>();
            for (Method m : aquifer.getDeclaredMethods()) {
                MixinMerged mm = m.getAnnotation(MixinMerged.class);
                if (mm != null) merged.add(mm.mixin());
            }
            for (String mixin : merged) {
                if (mixin.startsWith("bons.furious.mixin.")) continue;
                String want = VERIFIED.get(mixin);
                if (want == null) return "NoiseBasedAquifer carries code from " + mixin + ", which this switch has not verified";
                if (!want.equals(sha256(loader, mixin))) return mixin + " is not the verified build";
            }
            return null;
        } catch (Throwable t) {
            return "the check failed (" + t + ")";
        }
    }

    /** Test hook for the offline proof only: forget the census so the next call takes it again. */
    public static synchronized void resetCensus() {
        census = 0;
        censusDetail = "not taken";
    }

    /** A fresh entry table: eight slots, each marked empty (no grid coordinate is Integer.MIN_VALUE). */
    public static int[] newCells() {
        int[] t = new int[8 * STRIDE];
        for (int s = 0; s < 8; s++) t[s * STRIDE] = Integer.MIN_VALUE;
        return t;
    }

    /** The slot offset of a grid cell (low bit of each coordinate: the cells a fill alternates between never collide). */
    public static int slot(int gx, int gy, int gz) {
        return ((gx & 1) | (gz & 1) << 1 | (gy & 1) << 2) * STRIDE;
    }

    /**
     * Vanilla's ranking of the twelve candidates of an entry, written from computeSubstance's loop behaviour (>= insertion
     * in slot order); returns k1 | k2 << 4 | k3 << 8. Shadow reference only.
     */
    public static int referenceRanks(int[] t, int off, int x, int y, int z) {
        int d1 = Integer.MAX_VALUE, d2 = Integer.MAX_VALUE, d3 = Integer.MAX_VALUE, k1 = 0, k2 = 0, k3 = 0;
        for (int k = 0; k < 12; k++) {
            int dx = t[off + PX + k] - x, dy = t[off + PY + k] - y, dz = t[off + PZ + k] - z;
            int d = dx * dx + dy * dy + dz * dz;
            if (d1 >= d) {
                k3 = k2;
                k2 = k1;
                k1 = k;
                d3 = d2;
                d2 = d1;
                d1 = d;
            } else if (d2 >= d) {
                k3 = k2;
                k2 = k;
                d3 = d2;
                d2 = d;
            } else if (d3 >= d) {
                k3 = k;
                d3 = d;
            }
        }
        return k1 | k2 << 4 | k3 << 8;
    }

    /** Shadow bookkeeping: one comparison; WARN for the first 20 disagreements. */
    public static void shadow(boolean same, boolean highAir, String detail) {
        SHADOW_CHECKS.incrementAndGet();
        if (highAir) SHADOW_HIGH_AIR.incrementAndGet();
        if (!same && SHADOW_MISMATCHES.incrementAndGet() <= 20)
            LOGGER.warn("Bons and Furious: vanilla_aquifer_candidate_cache shadow mismatch: {}", detail);
    }

    private static String sha256(ClassLoader loader, String className) throws Exception {
        String resource = className.replace('.', '/') + ".class";
        ClassLoader l = loader != null ? loader : ClassLoader.getSystemClassLoader();
        byte[] bytes;
        try (InputStream in = l.getResourceAsStream(resource)) {
            if (in == null) return "unreadable";
            bytes = in.readAllBytes();
        }
        byte[] d = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : d) sb.append(Character.forDigit((b >> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
        return sb.toString();
    }
}
