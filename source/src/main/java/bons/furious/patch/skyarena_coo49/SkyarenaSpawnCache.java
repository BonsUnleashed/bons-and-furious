package bons.furious.patch.skyarena_coo49;

import bons.pure.config.PureConfig;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.jrdemiurge.skyarena.block.entity.AltarBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch skyarena_spawn_scan_cache_ticks (Sky Arena 1.3.6-1.21.1, MIT; server). A trade-off, off by
 * default. Ours; no Sky Arena code is carried. Idea: Collections Of Optimizations (skyarena.spawnScanCacheTicks), idea text
 * only.
 *
 * During a battle the altar's tick (AltarBlockEntity.updateSummonedMobs) calls findValidSpawnPositions once for every
 * summoned mob farther than the arena's teleport distance, every tick: a sweep of every column of the arena circle (at
 * the default radius of 36, 4,072 columns with up to 8 block lookups each), whose answer only changes when blocks of the
 * arena change or the player moves. A mob that cannot be pulled back (no valid square) pays it 20 times a second.
 *
 * What the switch does at that call (AltarSpawnScanCacheMixin, a MixinExtras @WrapOperation): the altar keeps the
 * columns whose blocks passed the original's tests (same squares, same tests, same order and the same block lookups as
 * the original, only without the player test) together with the level, the altar position, the radius and the water
 * flag; for the next N ticks (the tick of the sweep and N - 1 more; N = the key's value, 0 and 1 = that tick only) every
 * call answers from them, applying the original's player test (player.blockPosition().distSqr(column) above
 * spawnDistanceFromPlayer squared, in the original's order) to the CURRENT player position into a new list. Another
 * level, altar position, radius or water flag, or a tick outside the window, sweeps again. The wave start (AltarBlock.use)
 * and the auto wave are not touched: they always sweep.
 *
 * Trade-off: a square built over (or cleared) during the last N ticks can still be picked (or is not picked yet): the mob
 * lands in the block and is pushed out, as when two mobs are pulled to the same square. Player movement is always seen.
 * Switch off = the original call.
 *
 * Flag: -Dbons_and_furious.skyarenaSpawnScanCacheTicks=false = the original call. Shadow:
 * -Dbons_and_furious.skyarenaSpawnScanCacheTicks.shadow=true also runs the original and counts differing answers
 * (expected only after block changes inside the window; WARN on at most 20).
 */
public final class SkyarenaSpawnCache {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static final String KEY = "skyarena_spawn_scan_cache_ticks";
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.skyarenaSpawnScanCacheTicks", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.skyarenaSpawnScanCacheTicks.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Calls answered by a fresh sweep / from the kept sweep (for the harness). */
    public static final AtomicLong SWEEPS = new AtomicLong(), REUSED = new AtomicLong();
    private static volatile boolean announced;

    private SkyarenaSpawnCache() {
    }

    /** The value, read on the switch-on path only (first use). */
    private static final class Window {
        static final int TICKS = PureConfig.intValue(KEY, 20, 0, 1200);
    }

    /** One altar's kept sweep. */
    static final class Sweep {
        final Level level;
        final BlockPos center;
        final boolean water;
        final int radius;
        final long tick;
        final BlockPos[] columns;

        Sweep(Level level, BlockPos center, boolean water, int radius, long tick, BlockPos[] columns) {
            this.level = level;
            this.center = center;
            this.water = water;
            this.radius = radius;
            this.tick = tick;
            this.columns = columns;
        }
    }

    public static int ticks() {
        return Window.TICKS;
    }

    /** From AltarSpawnScanCacheMixin: the answer of updateSummonedMobs' findValidSpawnPositions call. */
    public static List<BlockPos> answer(AltarBlockEntity be, Level level, BlockPos center, Player player, Operation<List<BlockPos>> original) {
        if (!enabled || !(be instanceof SkyarenaCoo49MemoHolder holder) || level == null || center == null)
            return original.call(be, level, center, player);
        long now = level.getGameTime();
        boolean water = be.isAllowWaterAndAirSpawn();
        int radius = be.getMobSpawnRadius();
        Object kept = holder.bons$skyarenaCoo49Memo();
        Sweep s = kept instanceof Sweep k ? k : null;
        if (s == null || s.level != level || s.water != water || s.radius != radius || !s.center.equals(center)
                || now < s.tick || now - s.tick >= Math.max(Window.TICKS, 1)) {
            s = new Sweep(level, center.immutable(), water, radius, now, columns(level, center, water, radius));
            holder.bons$skyarenaCoo49SetMemo(s);
            SWEEPS.incrementAndGet();
        } else {
            REUSED.incrementAndGet();
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: {} applies (Sky Arena altars reuse their spawn-square sweep for {} tick(s); off by default)", KEY, Math.max(Window.TICKS, 1));
        }
        int d = be.getSpawnDistanceFromPlayer();
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : s.columns)
            if (player.blockPosition().distSqr(p) > (double) (d * d)) out.add(p);
        if (SHADOW) {
            List<BlockPos> stock = original.call(be, level, center, player);
            SHADOW_CHECKS.incrementAndGet();
            if (!out.equals(stock)) {
                long n = SHADOW_MISMATCHES.incrementAndGet();
                if (n <= 20) LOGGER.warn("Bons and Furious: {} shadow: kept sweep from tick {} answers {} squares, a fresh sweep {} (tick {})",
                        KEY, s.tick, out.size(), stock.size(), now);
            }
        }
        return out;
    }

    /** The original's column tests without the player test: same squares, same order, same block lookups. */
    static BlockPos[] columns(Level level, BlockPos center, boolean water, int r) {
        List<BlockPos> valid = new ArrayList<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int cx = center.getX(), cy = center.getY(), cz = center.getZ();
        for (int x = -r; x <= r; ++x) {
            for (int z = -r; z <= r; ++z) {
                if (x * x + z * z > r * r) continue;
                int px = cx + x, pz = cz + z;
                boolean ok;
                if (!water) {
                    ok = level.isEmptyBlock(m.set(px, cy, pz))
                            && level.isEmptyBlock(m.set(px, cy + 1, pz))
                            && level.isEmptyBlock(m.set(px, cy + 2, pz))
                            && level.isEmptyBlock(m.set(px, cy, pz - 1))
                            && level.isEmptyBlock(m.set(px, cy, pz + 1))
                            && level.isEmptyBlock(m.set(px + 1, cy, pz))
                            && level.isEmptyBlock(m.set(px - 1, cy, pz))
                            && !level.isEmptyBlock(m.set(px, cy - 1, pz));
                } else {
                    ok = SkyarenaSpawnScan.open(level, m.set(px, cy, pz))
                            && SkyarenaSpawnScan.open(level, m.set(px, cy + 1, pz))
                            && SkyarenaSpawnScan.open(level, m.set(px, cy + 2, pz))
                            && SkyarenaSpawnScan.open(level, m.set(px, cy, pz - 1))
                            && SkyarenaSpawnScan.open(level, m.set(px, cy, pz + 1))
                            && SkyarenaSpawnScan.open(level, m.set(px + 1, cy, pz))
                            && SkyarenaSpawnScan.open(level, m.set(px - 1, cy, pz));
                }
                if (ok) valid.add(new BlockPos(px, cy, pz));
            }
        }
        return valid.toArray(new BlockPos[0]);
    }
}
