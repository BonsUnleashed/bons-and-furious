package bons.furious.patch.shouldersurfing_c49;

import com.github.exopandora.shouldersurfing.api.client.IShoulderSurfing;
import com.github.exopandora.shouldersurfing.api.client.world.phys.BlockCollisionPredicate;
import com.github.exopandora.shouldersurfing.api.client.world.phys.IObjectPicker;
import com.github.exopandora.shouldersurfing.client.world.phys.ObjectPicker;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3f;

/**
 * Bons and Furious switch shouldersurfing_camera_sweep_prefilter (Shoulder Surfing Reloaded 1.21.1-5.3.0, MIT; client
 * side). Ours; no Shoulder Surfing code is carried.
 *
 * What Shoulder Surfing does. With dynamic offsets on (its default), every frame in third person
 * DynamicOffsets.calcDynamicOffsets steps dz from 0 to |offset z| by 1/32 block (129 steps for the default 4-block arm)
 * and traces one short segment per step with its ObjectPicker.clip: from a point on the line camera -> camera + offset to
 * a point on the line camera + lateral offset -> camera + lateral offset - look x |z|, through vanilla's block traversal,
 * keeping a hit only for a block whose collision shape (for the camera entity) the segment hits and that the block
 * collision predicate does not exempt. Every hit can only shorten the lateral offsets (to the hit distance plus the step's
 * share of the offset, less a third of the entity's width); with no hit the result is (signum(x) x |x|, signum(y) x |y|,
 * z).
 *
 * What the switch does. At the head of calcDynamicOffsets one pass over the blocks of the box around the four corner
 * points of those two lines (every traced segment lies in their convex hull; the box is widened by 0.001, far more than
 * the traversal's 1e-7 x length end extension plus rounding) keeps the blocks that could ever be hit: a non-empty
 * collision shape for the camera entity's collision context (the context every trace builds from the same entity) and
 * not exempted by the very predicate the method passes to clip. None: the method returns the no-hit result at once, the
 * same expression on the same values. Otherwise the original loop runs, and a step's trace is answered "miss" without
 * tracing when no kept block lies in that segment's own widened box (a trace only visits blocks of its box); the other
 * steps trace as before. More than 16 kept blocks, a box over 512 blocks on a side, a zero, NaN or infinite z, a missing
 * camera, entity or level, an object picker that is not Shoulder Surfing's own class, or a call off the first thread
 * that ran it: the original method, unchanged. When a plan does not pay (it found more than 16 blocks, or the loop it
 * planned skipped fewer than 8 traces), the next 1, then 2, 4 ... at most 32 calls run the original method without
 * planning; a plan that pays resets this. Beside obstacles the method then costs what it costs without the switch.
 *
 * Why it is identical. A trace returns a hit only from a visited block that the shape and predicate tests above keep, so
 * a step whose box holds no kept block returns a miss, which the loop skips; the shape and predicate are pure functions of
 * the block state, the level and the position (and the constant collision context) during one call, and the skipped
 * traces have no side effect (a trace only reads the level). The values every non-skipped step computes are untouched.
 *
 * -Dbons_and_furious.shouldersurfingCameraSweepPrefilter=false switches it off at run time.
 * -Dbons_and_furious.shouldersurfingCameraSweepPrefilter.shadow=true: never answers early; every trace the switch would
 * skip is run anyway and must miss (SHADOW_CHECKS, SHADOW_MISMATCHES; at most 20 WARN lines).
 */
public final class CameraSweep {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.shouldersurfingCameraSweepPrefilter", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.shouldersurfingCameraSweepPrefilter.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /**
     * Counters for in-game checks (the planning thread only): calls, calls answered at the head, skipped traces, calls
     * left to the original method, calls run without planning after a plan that did not pay.
     */
    public static long calls, answered, skippedTraces, plainCalls, pausedCalls;

    static final double MARGIN = 1.0E-3;
    static final double MAX_SPAN = 512.0;
    static final long MAX_CELLS = 32_768L;
    static final int MAX_KEPT = 16;
    /** A planned loop pays when it skips this many traces (the plan costs about as much as six traces). */
    static final int PAYS = 8;
    static final int MAX_PAUSE = 32;

    /** Back-off state (planning thread only). */
    private static boolean planned;
    private static long skippedAtPlan;
    private static int pauseLeft, pauseNext = 1;
    private static final BlockHitResult MISS = BlockHitResult.miss(Vec3.ZERO, Direction.UP, BlockPos.ZERO);

    private static volatile Thread planThread;
    /** The current call's kept blocks (planning thread only); -1 = trace every step. */
    private static int kept = -1;
    private static final int[] KX = new int[MAX_KEPT], KY = new int[MAX_KEPT], KZ = new int[MAX_KEPT];
    private static volatile boolean announced;

    private CameraSweep() {}

    /**
     * Head of calcDynamicOffsets: the method's own no-hit result when no step can hit a block (the caller returns it),
     * else null (the original loop runs, with the plan for {@link #clip}).
     */
    public static Vec3 plan(Camera camera, Entity cameraEntity, BlockGetter level, Vec3 targetOffset, BlockCollisionPredicate predicate) {
        Thread current = Thread.currentThread();
        Thread planner = planThread;
        if (planner == null) {
            synchronized (CameraSweep.class) {
                if (planThread == null) planThread = current;
                planner = planThread;
            }
        }
        if (planner != current) return null;           // another thread: original loop, the plan untouched
        kept = -1;
        if (!enabled || camera == null || cameraEntity == null || level == null || targetOffset == null || predicate == null) return null;
        calls++;
        if (planned) {                                  // the previous call's loop ran with a plan: did it pay?
            planned = false;
            if (skippedTraces - skippedAtPlan >= PAYS) pauseNext = 1;
            else notPaid();
        }
        if (pauseLeft > 0) {
            pauseLeft--;
            pausedCalls++;
            return null;
        }
        IShoulderSurfing instance = IShoulderSurfing.getInstance();
        if (instance == null || instance.getObjectPicker() == null || instance.getObjectPicker().getClass() != ObjectPicker.class) {
            plainCalls++;
            return null;
        }
        double x = targetOffset.x(), y = targetOffset.y(), z = targetOffset.z();
        double zAbs = Math.abs(z);
        if (!(zAbs > 0.0) || zAbs == Double.POSITIVE_INFINITY) {
            plainCalls++;
            return null;
        }
        // the method's own vectors: lookVector, worldXYOffset = up * y + left * x, worldOffset = worldXYOffset + look * -z
        Vector3f look = camera.getLookVector(), up = camera.getUpVector(), left = camera.getLeftVector();
        double lx = look.x(), ly = look.y(), lz = look.z();
        double xyX = (double) up.x() * y + (double) left.x() * x, xyY = (double) up.y() * y + (double) left.y() * x,
                xyZ = (double) up.z() * y + (double) left.z() * x;
        double woX = xyX + lx * -z, woY = xyY + ly * -z, woZ = xyZ + lz * -z;
        Vec3 cam = camera.getPosition();
        double ax = cam.x, ay = cam.y, az = cam.z;
        double bx = ax + woX, by = ay + woY, bz = az + woZ;
        double cx = ax + xyX, cy = ay + xyY, cz = az + xyZ;
        double dx = cx + lx * -zAbs, dy = cy + ly * -zAbs, dz = cz + lz * -zAbs;
        double minX = Math.min(Math.min(ax, bx), Math.min(cx, dx)) - MARGIN, maxX = Math.max(Math.max(ax, bx), Math.max(cx, dx)) + MARGIN;
        double minY = Math.min(Math.min(ay, by), Math.min(cy, dy)) - MARGIN, maxY = Math.max(Math.max(ay, by), Math.max(cy, dy)) + MARGIN;
        double minZ = Math.min(Math.min(az, bz), Math.min(cz, dz)) - MARGIN, maxZ = Math.max(Math.max(az, bz), Math.max(cz, dz)) + MARGIN;
        if (!(maxX - minX <= MAX_SPAN && maxY - minY <= MAX_SPAN && maxZ - minZ <= MAX_SPAN)) {   // also catches NaN
            plainCalls++;
            return null;
        }
        int x0 = Mth.floor(minX), x1 = Mth.floor(maxX), y0 = Mth.floor(minY), y1 = Mth.floor(maxY);
        int z0 = Mth.floor(minZ), z1 = Mth.floor(maxZ);
        if ((long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1) > MAX_CELLS) {
            plainCalls++;
            return null;
        }
        CollisionContext context = CollisionContext.of(cameraEntity);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int n = 0;
        for (int px = x0; px <= x1; px++) {
            for (int py = y0; py <= y1; py++) {
                for (int pz = z0; pz <= z1; pz++) {
                    pos.set(px, py, pz);
                    BlockState state = level.getBlockState(pos);
                    if (state.getCollisionShape(level, pos, context).isEmpty()) continue;
                    if (predicate.hasNoCollision(state, level, pos)) continue;
                    if (n == MAX_KEPT) {
                        plainCalls++;
                        notPaid();
                        return null;                    // too many obstacles: the original loop, every step traced
                    }
                    KX[n] = px;
                    KY[n] = py;
                    KZ[n] = pz;
                    n++;
                }
            }
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: shouldersurfing_camera_sweep_prefilter applies (camera traces only where a block can be hit)");
        }
        if (n == 0 && !SHADOW) {
            answered++;
            pauseNext = 1;
            return new Vec3(Math.signum(targetOffset.x()) * Math.abs(x), Math.signum(targetOffset.y()) * Math.abs(y), targetOffset.z());
        }
        kept = n;
        planned = !SHADOW;                              // shadow mode plans every call (every skip is checked)
        skippedAtPlan = skippedTraces;
        return null;
    }

    /** A plan that did not pay: the next calls run without one (1, 2, 4 ... MAX_PAUSE calls; never in shadow mode). */
    private static void notPaid() {
        if (SHADOW) return;
        pauseLeft = pauseNext;
        pauseNext = Math.min(pauseNext * 2, MAX_PAUSE);
    }

    /** In place of the loop's ObjectPicker.clip(level, context, predicate) call. */
    public static BlockHitResult clip(IObjectPicker picker, BlockGetter level, ClipContext context, BlockCollisionPredicate predicate) {
        int n = kept;
        if (n < 0 || Thread.currentThread() != planThread) return picker.clip(level, context, predicate);
        Vec3 from = context.getFrom(), to = context.getTo();
        int x0 = Mth.floor(Math.min(from.x, to.x) - MARGIN), x1 = Mth.floor(Math.max(from.x, to.x) + MARGIN);
        int y0 = Mth.floor(Math.min(from.y, to.y) - MARGIN), y1 = Mth.floor(Math.max(from.y, to.y) + MARGIN);
        int z0 = Mth.floor(Math.min(from.z, to.z) - MARGIN), z1 = Mth.floor(Math.max(from.z, to.z) + MARGIN);
        for (int i = 0; i < n; i++) {
            int kx = KX[i], ky = KY[i], kz = KZ[i];
            if (kx >= x0 && kx <= x1 && ky >= y0 && ky <= y1 && kz >= z0 && kz <= z1) return picker.clip(level, context, predicate);
        }
        if (SHADOW) {
            BlockHitResult real = picker.clip(level, context, predicate);
            SHADOW_CHECKS.incrementAndGet();
            if (real.getType() != HitResult.Type.MISS && SHADOW_MISMATCHES.incrementAndGet() <= 20)
                LOGGER.warn("Bons and Furious: shouldersurfing_camera_sweep_prefilter shadow mismatch: a skipped camera trace {} -> {} hit {}", from, to, real.getBlockPos());
            return real;
        }
        skippedTraces++;
        return MISS;
    }
}
