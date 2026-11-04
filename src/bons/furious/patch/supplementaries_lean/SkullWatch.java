package bons.furious.patch.supplementaries_lean;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * Bons and Furious switch supplementaries_skull_watch_precheck (Supplementaries 1.20-3.1.41 for Minecraft 1.20.1 Forge;
 * server side, including the integrated server). SRG member names.
 *
 * What Supplementaries does. Every tick every enderman skull (EndermanSkullBlockTile.isBeingWatched) takes the players in
 * a box of +-20 blocks and, for each one not wearing an enderman mask, casts the player's 64-block look ray
 * (player.pick(64, 1, false): Level.clip from the eye along the view vector, block outlines, no fluids) and counts the
 * skull as watched only when the ray's block hit is the skull's own position (and the face test passes).
 *
 * What the switch does. Before that pick it checks, from the same eye position and view vector (the same getEyePosition(1)
 * and getViewVector(1) calls the pick makes), whether the segment the clip walks (BlockGetter.traverseBlocks: eye to eye +
 * view x 64, each end extended by 1e-7 of the length) can touch the skull's block cell at all (cell inflated by 1e-4, far
 * above the walk's floating-point error). When it cannot, and every chunk column the segment crosses is loaded
 * (getChunkNow), the ray can never report the skull's position: the pick is replaced by a miss at the ray's end (a block
 * position 64 blocks away, never the skull's), which the caller treats exactly as any other block or a miss, and moves on
 * to the next player. A skull in a Valkyrien Skies shipyard (the ray reaches it through the ship's transform), an unloaded
 * chunk on the way (the real pick would load it), a call other than pick(64, 1, false) and the runtime switch off all take
 * the real pick.
 *
 * Exact variant: the skipped pick's block reads do not refresh ServerChunkCache's last-chunk lookup cache nor the
 * profiler's getChunk counter (the pre-check asks getChunkNow instead); every result and every block, entity and save is
 * the same.
 *
 * -Dbons_and_furious.supplementariesSkullWatchPrecheck=false switches it off at run time.
 * -Dbons_and_furious.supplementariesSkullWatchPrecheck.shadow=true (verification runs only): the real pick always runs;
 * SHADOW_CHECKS counts the picks the switch would have skipped and SHADOW_MISMATCHES those whose real result was the
 * skull's position (WARN for the first 20), which the geometry rules out.
 */
public final class SkullWatch {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.supplementariesSkullWatchPrecheck", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.supplementariesSkullWatchPrecheck.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Picks replaced by a miss / real picks made (statistics for the in-game check). */
    public static final AtomicLong SKIPPED = new AtomicLong(), REAL = new AtomicLong();
    /** BlockGetter.traverseBlocks' end extension (Mth.lerp(-1.0E-7, ...)). */
    static final double WALK_EXTENSION = 1.0E-7;
    /** Inflation of the skull's cell: far above the walk's accumulated floating-point error. */
    static final double CELL_MARGIN = 1.0E-4;
    private static final MethodHandle IN_SHIPYARD = shipyardTest();
    private static volatile boolean announced;

    private SkullWatch() {
    }

    /** EndermanSkullBlockTile.isBeingWatched: its player.pick(64, 1, false) call. */
    public static HitResult pick(Player player, double distance, float partialTicks, boolean fluids, Operation<HitResult> original,
                                 Level level, BlockPos skull) {
        if (!enabled || distance != 64.0D || partialTicks != 1.0F || fluids || level == null || level.f_46443_)
            return original.call(player, distance, partialTicks, fluids);
        Vec3 eye = player.m_20299_(partialTicks);
        Vec3 view = player.m_20252_(partialTicks);
        Vec3 end = eye.m_82520_(view.f_82479_ * distance, view.f_82480_ * distance, view.f_82481_ * distance);
        if (eye.equals(end) || inShipyard(level, skull) || mayTouchCell(eye, end, skull)
                || BlockPos.m_274446_(end).equals(skull) || !columnsLoaded(level.m_7726_(), eye, end)) {
            REAL.incrementAndGet();
            return original.call(player, distance, partialTicks, fluids);
        }
        if (SHADOW) {
            HitResult real = original.call(player, distance, partialTicks, fluids);
            SHADOW_CHECKS.incrementAndGet();
            if (real instanceof BlockHitResult hit && hit.m_82425_().equals(skull)) {
                long m = SHADOW_MISMATCHES.incrementAndGet();
                if (m <= 20) LOGGER.warn("Bons and Furious: supplementaries_skull_watch_precheck shadow mismatch #{}: skull {} eye {} end {} hit {}", m, skull, eye, end, real.m_82450_());
            }
            return real;
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: supplementaries_skull_watch_precheck applies (enderman skulls skip a player's 64-block look ray that cannot reach them)");
        }
        SKIPPED.incrementAndGet();
        return BlockHitResult.m_82426_(end, Direction.m_122366_(view.f_82479_, view.f_82480_, view.f_82481_), BlockPos.m_274446_(end));
    }

    /**
     * True when the segment BlockGetter.traverseBlocks walks for from -> to (both ends extended by 1e-7 of its length) can
     * touch the block cell at pos, inflated by CELL_MARGIN (slab test, conservative).
     */
    public static boolean mayTouchCell(Vec3 from, Vec3 to, BlockPos pos) {
        double fx = from.f_82479_, fy = from.f_82480_, fz = from.f_82481_;
        double dx = to.f_82479_ - fx, dy = to.f_82480_ - fy, dz = to.f_82481_ - fz;
        double t0 = -WALK_EXTENSION, t1 = 1.0D + WALK_EXTENSION;
        double[] range = {t0, t1};
        return slab(fx, dx, pos.m_123341_() - CELL_MARGIN, pos.m_123341_() + 1.0D + CELL_MARGIN, range)
                && slab(fy, dy, pos.m_123342_() - CELL_MARGIN, pos.m_123342_() + 1.0D + CELL_MARGIN, range)
                && slab(fz, dz, pos.m_123343_() - CELL_MARGIN, pos.m_123343_() + 1.0D + CELL_MARGIN, range);
    }

    /** Clips range[0..1] (segment parameters) to where start + t * delta lies in [lo, hi]; false when nothing is left. */
    private static boolean slab(double start, double delta, double lo, double hi, double[] range) {
        if (delta == 0.0D) return start >= lo && start <= hi;
        double a = (lo - start) / delta, b = (hi - start) / delta;
        if (a > b) {
            double s = a;
            a = b;
            b = s;
        }
        if (a > range[0]) range[0] = a;
        if (b < range[1]) range[1] = b;
        return range[0] <= range[1];
    }

    /**
     * True when every chunk column the XZ projection of the walked segment crosses (with the same margins) is loaded:
     * the real pick's block reads then load nothing.
     */
    public static boolean columnsLoaded(ChunkSource chunks, Vec3 from, Vec3 to) {
        double ext = WALK_EXTENSION, m = CELL_MARGIN;
        double dx = to.f_82479_ - from.f_82479_, dz = to.f_82481_ - from.f_82481_;
        double ax = from.f_82479_ - ext * dx, az = from.f_82481_ - ext * dz;
        double bx = to.f_82479_ + ext * dx, bz = to.f_82481_ + ext * dz;
        double sx = bx - ax, sz = bz - az;
        int cx0 = (int) Math.floor((Math.min(ax, bx) - m) / 16.0D), cx1 = (int) Math.floor((Math.max(ax, bx) + m) / 16.0D);
        for (int cx = cx0; cx <= cx1; cx++) {
            double x0 = cx * 16.0D - m, x1 = cx * 16.0D + 16.0D + m;
            double ta = 0.0D, tb = 1.0D;
            if (sx != 0.0D) {
                double a = (x0 - ax) / sx, b = (x1 - ax) / sx;
                ta = Math.max(0.0D, Math.min(a, b));
                tb = Math.min(1.0D, Math.max(a, b));
                if (ta > tb) continue;
            } else if (ax < x0 || ax > x1) {
                continue;
            }
            double za = az + sz * ta, zb = az + sz * tb;
            int cz0 = (int) Math.floor((Math.min(za, zb) - m) / 16.0D), cz1 = (int) Math.floor((Math.max(za, zb) + m) / 16.0D);
            for (int cz = cz0; cz <= cz1; cz++) {
                if (chunks.m_7131_(cx, cz) == null) return false;
            }
        }
        return true;
    }

    private static boolean inShipyard(Level level, BlockPos pos) {
        if (IN_SHIPYARD == null) return false;
        try {
            return (boolean) IN_SHIPYARD.invokeExact(level, pos);
        } catch (Throwable t) {
            return true;   // cannot tell: take the real pick
        }
    }

    /** Valkyrien Skies' VSGameUtilsKt.isBlockInShipyard(Level, BlockPos); null without Valkyrien Skies. */
    private static MethodHandle shipyardTest() {
        try {
            Class<?> vs = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt", false, SkullWatch.class.getClassLoader());
            return MethodHandles.publicLookup().findStatic(vs, "isBlockInShipyard", MethodType.methodType(boolean.class, Level.class, BlockPos.class));
        } catch (ClassNotFoundException e) {
            return null;
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: supplementaries_skull_watch_precheck cannot ask Valkyrien Skies about shipyards ({}); every skull takes the real pick", t.toString());
            return MethodHandles.dropArguments(MethodHandles.constant(boolean.class, true), 0, Level.class, BlockPos.class);
        }
    }
}
