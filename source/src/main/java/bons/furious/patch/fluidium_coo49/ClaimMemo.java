package bons.furious.patch.fluidium_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49 switch fluidium_claim_lookup_memo (Fluidium 1.21.1-1.4.0 with FTB Chunks 2101.1.23 / FTB Library
 * 2101.1; exact; server; helper of ClaimLookupMemoMixin and FtbClaimEpochMixin; no Fluidium or FTB code carried).
 *
 * What Fluidium does. Its ServerLevel mixin asks ClaimManager.isClaimed(level, pos) first on every fluid tick; with FTB
 * Chunks installed that asks FTBChecker.isClaimed: FTBChunksAPI.api(), isManagerLoaded(), then getManager().getChunk(new
 * ChunkDimPos(level, pos)) != null - a new ChunkDimPos record (dimension key + a new ChunkPos) and a HashMap lookup in the
 * manager's private claimedChunks map. Near waterfalls and lava lakes that is hundreds to thousands of lookups per tick.
 *
 * What the switch does. The answer FTB gave is kept in a 4,096-entry table (about 70 KB; a 256-entry table missed too
 * often when fluids tick in more chunks than it holds) keyed by (the level's dimension key, chunk x, chunk z)
 * together with the manager object it came from and a claim epoch. FTB Chunks 2101.1.23 writes claimedChunks only in the
 * manager's constructor, registerClaim (put) and unregisterClaim (remove) (census of ClaimedChunkManagerImpl; the field is
 * private and final, getAllClaimedChunks hands out an unmodifiable view); FtbClaimEpochMixin bumps the epoch at every return of
 * those two methods, and a new manager (a new world or server) empties the table. A lookup still asks FTBChunksAPI.api(),
 * isManagerLoaded() and getManager() as FTB's checker does (plain getters) and is answered from the table only for the same
 * manager, the same epoch and the same key (ChunkDimPos record equality: the same dimension key object, a ChunkPos with the
 * same x / z, which is what new ChunkDimPos(level, pos) builds from pos.getX() >> 4 / pos.getZ() >> 4); every other lookup
 * runs FTB's checker and is kept. The table belongs to the first thread that uses it (the server thread); other threads
 * always ask FTB. Exact.
 * Before the table answers for the first time, three facts are checked once: FluidiumPlugin's look at the merged
 * ClaimedChunkManagerImpl found the map private, assigned only by the constructor, named only by the five known methods,
 * and both writers calling the epoch bump; the field is still private at run time (no access transformer widened it); and
 * no mixin of another mod with priority at or above ours was applied to the class after that look. Any failure: FTB is
 * asked every time for the rest of the session, one INFO line.
 * Open Parties and Claims' checker (asked after a false FTB answer) is untouched. Runtime flag
 * bons_and_furious.fluidiumClaimLookupMemo; SHADOW MODE -Dbons_and_furious.fluidiumClaimLookupMemo.shadow=true asks FTB on every
 * table hit and compares (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20; FTB's answer is returned).
 */
public final class ClaimMemo {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.fluidiumClaimLookupMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.fluidiumClaimLookupMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Bumped at every return of ClaimedChunkManagerImpl.registerClaim / unregisterClaim (any thread). */
    public static volatile int epoch;
    /** Statistics for probes (plain fields). */
    public static long hits, misses;
    static final String FTB_CHECKER = "me.kall.fluidium.common.integration.ftbchunks.FTBChecker";
    static final String MANAGER_IMPL = "dev.ftb.mods.ftbchunks.data.ClaimedChunkManagerImpl";
    private static final int SIZE = 4096;
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    // the table (owner thread only)
    private static Thread owner;
    private static Object manager;
    private static Class<?> checkerClass, managerClass;
    private static final Object[] DIMS = new Object[SIZE];
    private static final int[] XS = new int[SIZE], ZS = new int[SIZE], EPOCHS = new int[SIZE];
    private static final boolean[] ANSWERS = new boolean[SIZE];
    private static volatile boolean announced;
    /** 0 = not checked yet, 1 = the table may answer, -1 = stood down for the session (owner thread only). */
    static int verified;

    private ClaimMemo() {
    }

    /** The one-time check of the manager class (see the class comment); {@code manager} is FTB's current manager. */
    static boolean verified(Object manager) {
        if (verified != 0) return verified > 0;
        String why = FluidiumPlugin.managerVerdict;
        if (why == null) why = "FtbClaimEpochMixin was not applied to FTB Chunks' claim manager";
        if (why.isEmpty()) why = fieldWidened(manager.getClass());
        if (why.isEmpty()) why = laterManagerMixins();
        if (!why.isEmpty()) {
            verified = -1;
            LOGGER.info("Bons and Furious: fluidium_claim_lookup_memo stands down for this session: {}; Fluidium asks FTB Chunks every time", why);
            return false;
        }
        verified = 1;
        return true;
    }

    /** "" while the manager's claim map field is still private (no access transformer opened it to other classes). */
    public static String fieldWidened(Class<?> managerClass) {
        try {
            int mod = managerClass.getDeclaredField("claimedChunks").getModifiers();
            return java.lang.reflect.Modifier.isPrivate(mod) ? "" : "the claim map field is not private here (" + java.lang.reflect.Modifier.toString(mod) + ")";
        } catch (Throwable t) {
            return "the claim map field could not be inspected (" + t + ")";
        }
    }

    /** "" when no mixin of another mod with priority at or above FtbClaimEpochMixin's was applied to the manager class. */
    public static String laterManagerMixins() {
        try {
            int ours = FluidiumPlugin.epochPriority;
            for (org.spongepowered.asm.mixin.extensibility.IMixinInfo mi : org.spongepowered.asm.mixin.transformer.ClassInfo
                    .forName(FluidiumPlugin.MANAGER).getAppliedMixins()) {
                if (mi.getClassName().startsWith("bons.furious.mixin.fluidium_coo49.")) continue;
                if (mi.getPriority() >= ours) return "the mixin " + mi.getClassName() + " (priority " + mi.getPriority() + ") was applied to ClaimedChunkManagerImpl after the look at its claim map";
            }
            return "";
        } catch (Throwable t) {
            return "the mixins applied to ClaimedChunkManagerImpl could not be listed (" + t + ")";
        }
    }

    /** FtbClaimEpochMixin: a claim was registered or unregistered. */
    public static void bump() {
        epoch++;
    }

    /** ClaimManager.isClaimed's FTB checker call, wrapped ({@code checker} is the IChecker it was made on). */
    public static boolean claimed(Object checker, Level level, BlockPos pos, Operation<Boolean> original) {
        if (!enabled || level == null || pos == null || checker == null) return original.call(checker, level, pos);
        Thread t = Thread.currentThread();
        if (owner != t) {
            if (owner != null) return original.call(checker, level, pos);
            synchronized (ClaimMemo.class) {
                if (owner == null) owner = t;
            }
            if (owner != t) return original.call(checker, level, pos);
        }
        // the class checks compare names once, then the class objects (owner thread only)
        Class<?> cc = checker.getClass();
        if (cc != checkerClass) {
            if (checkerClass != null || !cc.getName().equals(FTB_CHECKER)) return original.call(checker, level, pos);
            checkerClass = cc;
        }
        FTBChunksAPI.API api = FTBChunksAPI.api();   // never null: FTB's api() throws as the checker's own first call would
        if (!api.isManagerLoaded()) return original.call(checker, level, pos);
        Object m = api.getManager();
        if (m == null) return original.call(checker, level, pos);
        Class<?> mc = m.getClass();
        if (mc != managerClass) {
            if (managerClass != null || !mc.getName().equals(MANAGER_IMPL)) return original.call(checker, level, pos);
            managerClass = mc;
        }
        if (!verified(m)) return original.call(checker, level, pos);
        if (m != manager) {
            manager = m;
            java.util.Arrays.fill(DIMS, null);
        }
        int e = epoch;
        ResourceKey<Level> dim = level.dimension();
        int x = pos.getX() >> 4, z = pos.getZ() >> 4;
        int h = System.identityHashCode(dim) * 31 + x * 0x9E3779B9 + z * 0x85EBCA6B;
        int slot = (h ^ (h >>> 16)) & (SIZE - 1);
        if (DIMS[slot] == dim && XS[slot] == x && ZS[slot] == z && EPOCHS[slot] == e) {
            boolean kept = ANSWERS[slot];
            if (SHADOW) {
                boolean asked = original.call(checker, level, pos);
                SHADOW_CHECKS.incrementAndGet();
                if (asked != kept && SHADOW_MISMATCHES.incrementAndGet() <= 20)
                    LOGGER.warn("Bons and Furious: fluidium_claim_lookup_memo SHADOW MISMATCH at {} in {}: kept {}, FTB answered {}", pos, dim.location(), kept, asked);
                return asked;
            }
            hits++;
            return kept;
        }
        boolean answer = original.call(checker, level, pos);
        misses++;
        if (epoch == e) {   // no claim change while FTB answered
            DIMS[slot] = dim;
            XS[slot] = x;
            ZS[slot] = z;
            EPOCHS[slot] = e;
            ANSWERS[slot] = answer;
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: fluidium_claim_lookup_memo: Fluidium's FTB Chunks claim answers are kept per chunk until a claim changes");
        }
        return answer;
    }
}
