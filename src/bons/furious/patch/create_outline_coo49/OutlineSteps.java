package bons.furious.patch.create_outline_coo49;

import com.simibubi.create.foundation.block.IHaveBigOutline;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch create_big_outline_probe_dedupe (Create 6.0.8, client).
 *
 * Create's BigOutlines.pick runs on every frame: it walks the look ray block by block (RaycastHelper.rayTraceUntil) and,
 * at every step, reads the 27 blocks around the step's position looking for a block with a big outline (one whose shape
 * reaches past its own cell) that the ray hits. Neighbouring steps share 18 of those 27 cells (diagonal steps fewer).
 *
 * The switch skips a cell that lies within one block (in every axis) of the previous step's position in the same pick:
 * that cell was read and tested one step earlier, with the same block state (the client level does not change during the
 * pick), the same shape and the same ray, and it gave no hit: rayTraceUntil only takes another step when the previous
 * test found nothing, and while nothing is found the test of a cell depends only on that cell (big-outline block, ray
 * clip, distance below the reach). So the skipped read could not change the result; the skip hands the test AIR, which
 * is not a big-outline block. The first step reads all 27 as before. The pick's result (the hit block, face, position and
 * the hit result Create sets) is the same for every ray and world. Runtime flag
 * -Dbons_and_furious.createBigOutlineProbeDedupe=false. Shadow mode (.shadow=true): a skipped cell is read anyway and must
 * not be a big-outline block that the ray hits within the reach (WARN at most 20 mismatches). Create is MIT; no code of it
 * is carried.
 */
public final class OutlineSteps {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.createBigOutlineProbeDedupe", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.createBigOutlineProbeDedupe.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong();
    public static final AtomicLong SHADOW_MISMATCHES = new AtomicLong();
    /** What a skipped read hands the test: not a big-outline block. */
    public static final BlockState SKIPPED = Blocks.f_50016_.m_49966_();

    // the pick's step positions and ray (client main thread only)
    private static boolean havePrevious, haveCurrent;
    private static int px, py, pz, cx, cy, cz;
    private static Vec3 origin, target;
    private static double range;
    private static volatile boolean announced;

    private OutlineSteps() {
    }

    /** HEAD of BigOutlines.pick: a new ray. */
    public static void begin() {
        havePrevious = false;
        haveCurrent = false;
    }

    /** HEAD of the pick's per-step test: the current step becomes the previous one. */
    public static void step(BlockPos pos, Vec3 rayOrigin, Vec3 rayTarget, double maxDistanceSqr) {
        if (haveCurrent) {
            px = cx;
            py = cy;
            pz = cz;
            havePrevious = true;
        }
        cx = pos.m_123341_();
        cy = pos.m_123342_();
        cz = pos.m_123343_();
        haveCurrent = true;
        origin = rayOrigin;
        target = rayTarget;
        range = maxDistanceSqr;
    }

    /** True when the cell was already read and tested at the previous step of this pick. */
    public static boolean seen(BlockPos cell) {
        if (!enabled || !havePrevious) return false;
        if (Math.abs(cell.m_123341_() - px) > 1 || Math.abs(cell.m_123342_() - py) > 1 || Math.abs(cell.m_123343_() - pz) > 1) return false;
        if (!announced) announce();
        return true;
    }

    /** Shadow mode: the skipped cell's real state must not be a big-outline block the ray hits within the reach. */
    public static void shadow(BlockState real, BlockGetter level, BlockPos cell) {
        SHADOW_CHECKS.incrementAndGet();
        boolean hit = false;
        if (real.m_60734_() instanceof IHaveBigOutline) {
            BlockHitResult h = real.m_60820_(level, cell).m_83220_(origin, target, cell.m_7949_());
            hit = h != null && h.m_82450_().m_82557_(origin) < range;
        }
        if (hit) {
            long n = SHADOW_MISMATCHES.incrementAndGet();
            if (n <= 20) LOGGER.warn("Bons and Furious: create_big_outline_probe_dedupe shadow mismatch #{}: skipped {} at {} would have been hit", n, real, cell);
        }
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: create_big_outline_probe_dedupe: Create's big-outline pick reads each block once per ray");
    }
}
