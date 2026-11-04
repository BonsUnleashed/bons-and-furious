package bons.furious.patch.skyarena_coo49;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch skyarena_spawn_scan_lean (Sky Arena 1.3.6, MIT; server). Ours; no Sky Arena code is carried.
 * Idea: Collections Of Optimizations (skyarena.leanSpawnScan), idea text only.
 *
 * The altar's spawn-square sweep (AltarBlockEntity.findValidSpawnPositions) makes a new BlockPos for every column of its
 * square and for every neighbour block it asks about: at the default radius of 36 about 5,300 column positions and up to
 * 7 (water-and-air arenas: 14) neighbour positions per column in the circle, during a battle once per stray mob per tick.
 * AltarSpawnScanLeanMixin runs the same sweep with one MutableBlockPos; this class holds its flag, the water-and-air
 * branch's test and the shadow-mode bookkeeping. Why the answer is identical: see the mixin.
 *
 * Flag: -Dbons_and_furious.skyarenaSpawnScanLean=false runs the original sweep. Shadow:
 * -Dbons_and_furious.skyarenaSpawnScanLean.shadow=true runs both and compares (WARN on at most 20 mismatches).
 */
public final class SkyarenaSpawnScan {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static final String KEY = "skyarena_spawn_scan_lean";
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.skyarenaSpawnScanLean", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.skyarenaSpawnScanLean.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Sweeps answered by the lean sweep (for the harness). */
    public static final AtomicLong SWEEPS = new AtomicLong();
    private static final ThreadLocal<List<BlockPos>> KEPT = new ThreadLocal<>();
    private static volatile boolean announced;

    private SkyarenaSpawnScan() {
    }

    /** The water-and-air branch's per-block test, as the original asks it: the state's collision shape at pos is empty. */
    public static boolean open(Level level, BlockPos pos) {
        return level.m_8055_(pos).m_60812_(level, pos).m_83281_();
    }

    public static void announce() {
        SWEEPS.incrementAndGet();
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: {} applies (Sky Arena's altar spawn-square sweep uses one reusable position)", KEY);
        }
    }

    /** Shadow mode: our list, compared with the original's at its RETURN. */
    public static void keep(List<BlockPos> ours) {
        KEPT.set(ours);
    }

    public static void compare(List<BlockPos> original) {
        List<BlockPos> ours = KEPT.get();
        KEPT.remove();
        if (ours == null) return;
        SHADOW_CHECKS.incrementAndGet();
        if (!ours.equals(original)) {
            long n = SHADOW_MISMATCHES.incrementAndGet();
            if (n <= 20) LOGGER.warn("Bons and Furious: {} shadow mismatch #{}: lean sweep {} squares, original {}", KEY, n,
                    ours.size(), original == null ? -1 : original.size());
        }
    }
}
