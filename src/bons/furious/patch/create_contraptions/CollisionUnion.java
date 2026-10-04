package bons.furious.patch.create_contraptions;

import bons.furious.mixin.create_contraptions.VoxelShapeAccessor;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch create_collision_single_pass (Create 6.0.8, MIT; Minecraft 1.20.1 shapes; both sides).
 *
 * Contraption.gatherBBsOffThread (off the main thread, whenever a contraption is created, loaded or changes its blocks)
 * folds every block's collision shape into one VoxelShape with Shapes.joinUnoptimized, block after block, then calls
 * optimize().toAabbs(). Each join merges the coordinate lists of everything joined so far with the next block and
 * rebuilds the voxel bit set over the merged grid, so the work grows with the square of the block count (Create's own
 * issue #6902: seconds for large contraptions, with heavy garbage).
 *
 * The switch keeps Create's loop and every collision-shape call as they are and only collects the moved shapes instead of
 * joining them (ContraptionCollisionMixin); at optimize() it builds the combined shape's box list in one pass:
 *  - per axis, the grid = every coordinate of every collected shape (read through the shapes' own coordinate lists, as
 *    joinUnoptimized reads them), sorted, duplicates once; a voxel of the grid is full when it lies inside a full voxel of
 *    some collected shape (Minecraft's BitSetDiscreteVoxelShape holds it), and the boxes are Minecraft's own greedy
 *    forAllBoxes over it, each turned into an AABB from the grid values exactly as VoxelShape.toAabbs does.
 *  - joinUnoptimized gives exactly this grid and these voxels when no two different coordinates of an axis lie within
 *    1e-7 of each other (its index mergers - Minecraft's IndirectMerger, NonOverlappingMerger, IdenticalMerger, and Radium's
 *    LithiumDoublePairList, which follows IndirectMerger's rules - then keep every value, merge equal values only, and map
 *    each merged cell to the cell of each operand that contains it; DiscreteCubeMerger never applies because a moved
 *    shape's coordinate lists are offset lists, not cube grids). The switch requires a gap above 1e-6 between any two
 *    different coordinates of an axis, finite values, no -0.0, strictly increasing lists per shape and known shape classes
 *    (Minecraft's, and Radium's simple and aligned cuboids); otherwise it joins the collected shapes with joinUnoptimized
 *    in Create's order and runs optimize() on that, i.e. Create's own result.
 *  - optimize() turns the combined shape into its boxes (forAllBoxes) and joins those boxes into a new shape; toAabbs()
 *    then lists that shape's boxes. Minecraft's greedy forAllBoxes depends only on the full region and on the grid lines it
 *    may cut at, and grid lines that do not change the region add no box (a z-run, an x-extension or a y-extension only
 *    stops where the region changes), so the optimized shape lists the same boxes in the same order, as long as every box
 *    keeps its exact coordinates. Shapes.create keeps them for boxes it builds from plain values; a box inside the unit
 *    cell whose ends Shapes.findBits aligns becomes a cube-grid shape (vanilla: 2^b parts; Radium: snapped to eighths), which
 *    keeps them only when they are multiples of 1/8 and adds grid lines on eighths. So when such a box occurs, the switch
 *    also requires its coordinates to be multiples of 1/8 and every grid value near the unit cell to be an eighth or more
 *    than 1e-6 from every eighth; otherwise it falls back as above.
 * The list is handed to Create's toAabbs() call on the same shape object it would have called it on.
 *
 * Shapes are collected per worker thread (a contraption's computation runs on one thread from its first join to its
 * toAabbs call); the state belongs to the computation that started with Shapes.empty(), so switching the flag mid-way
 * cannot mix the two paths.
 */
public final class CollisionUnion {
    /** Runtime switch. -Dbons_and_furious.createCollisionSinglePass=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.createCollisionSinglePass", "true"));
    /** Shadow mode for rigs: every one-pass result is compared with Create's join chain (costs the original's time). */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.createCollisionSinglePass.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Counters for probes: computations built in one pass, computations that fell back to the join chain. */
    public static final AtomicLong ONE_PASS = new AtomicLong(), FALLBACK = new AtomicLong();

    static final double SEP = 1.0E-6;
    static final long MAX_CELLS = 1L << 27;
    static final Set<String> SHAPE_CLASSES = Set.of(
            "net.minecraft.world.phys.shapes.ArrayVoxelShape",
            "net.minecraft.world.phys.shapes.CubeVoxelShape",
            "me.jellysquid.mods.lithium.common.shapes.VoxelShapeSimpleCube",
            "me.jellysquid.mods.lithium.common.shapes.VoxelShapeAlignedCuboid",
            "me.jellysquid.mods.lithium.common.shapes.VoxelShapeAlignedCuboidOffset");
    private static final Direction.Axis[] AXES = {Direction.Axis.X, Direction.Axis.Y, Direction.Axis.Z};
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    /** Minecraft's Shapes.findBits (m_83041_); null if it cannot be reached, then every box inside the unit cell counts as aligned. */
    private static final MethodHandle FIND_BITS = findBits();
    private static volatile boolean announced, warned;

    private static final class Run {
        ArrayList<VoxelShape> shapes = new ArrayList<>();
        VoxelShape last;
        boolean active;
        List<AABB> boxes;          // the result waiting for Create's toAabbs() call on `last`
    }

    private static final ThreadLocal<Run> RUN = ThreadLocal.withInitial(Run::new);

    private CollisionUnion() {
    }

    private static MethodHandle findBits() {
        try {
            Method m = Shapes.class.getDeclaredMethod("m_83041_", double.class, double.class);
            m.setAccessible(true);
            return MethodHandles.lookup().unreflect(m);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Shapes.joinUnoptimized(acc, moved, op) in Create's collider lambda. */
    public static VoxelShape join(VoxelShape acc, VoxelShape moved, BooleanOp op, Operation<VoxelShape> original) {
        Run r = RUN.get();
        if (r.active && acc == r.last) {           // inside a computation this switch started: keep collecting
            r.shapes.add(moved);
            r.last = moved;
            return moved;
        }
        if (enabled && acc == Shapes.m_83040_() && op == BooleanOp.f_82695_) {   // the first join of a computation
            r.shapes.clear();
            r.boxes = null;
            r.active = true;
            r.shapes.add(moved);
            r.last = moved;
            return moved;
        }
        return original.call(acc, moved, op);
    }

    /** combined.optimize() in Create's collider lambda. */
    public static VoxelShape optimize(VoxelShape combined, Operation<VoxelShape> original) {
        Run r = RUN.get();
        if (!r.active || combined != r.last) return original.call(combined);
        r.active = false;
        VoxelShape[] shapes = r.shapes.toArray(new VoxelShape[0]);
        if (r.shapes.size() > 4096) r.shapes = new ArrayList<>();
        else r.shapes.clear();
        List<AABB> boxes = null;
        try {
            boxes = onePass(shapes);
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                LOGGER.warn("Bons and Furious: create_collision_single_pass fell back to Create's join chain after {}", t.toString());
            }
        }
        if (boxes == null) {
            r.last = null;
            FALLBACK.incrementAndGet();
            return original.call(chain(shapes));
        }
        ONE_PASS.incrementAndGet();
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: create_collision_single_pass builds contraption collision boxes in one pass");
        }
        if (SHADOW) shadow(shapes, boxes, original);
        r.boxes = boxes;
        return combined;            // Create calls toAabbs() on this object next
    }

    /** shape.toAabbs() in Create's collider lambda. */
    public static List<AABB> toAabbs(VoxelShape shape, Operation<List<AABB>> original) {
        Run r = RUN.get();
        List<AABB> boxes = r.boxes;
        if (boxes == null || shape != r.last) return original.call(shape);
        r.boxes = null;
        r.last = null;
        return boxes;
    }

    /** Create's fold over the collected shapes, in Create's order: the original combined shape. */
    public static VoxelShape chain(VoxelShape[] shapes) {
        VoxelShape acc = Shapes.m_83040_();
        for (VoxelShape s : shapes) acc = Shapes.m_83148_(acc, s, BooleanOp.f_82695_);
        return acc;
    }

    private static void shadow(VoxelShape[] shapes, List<AABB> boxes, Operation<VoxelShape> original) {
        SHADOW_CHECKS.incrementAndGet();
        List<AABB> theirs = original.call(chain(shapes)).m_83299_();
        if (!sameBoxes(boxes, theirs) && SHADOW_MISMATCHES.incrementAndGet() <= 20)
            LOGGER.warn("Bons and Furious: create_collision_single_pass shadow check: {} boxes vs Create's {} for {} shapes", boxes.size(), theirs.size(), shapes.length);
    }

    /** Same boxes in the same order, every coordinate bit for bit. */
    public static boolean sameBoxes(List<AABB> a, List<AABB> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            AABB x = a.get(i), y = b.get(i);
            if (Double.doubleToRawLongBits(x.f_82288_) != Double.doubleToRawLongBits(y.f_82288_)
                    || Double.doubleToRawLongBits(x.f_82289_) != Double.doubleToRawLongBits(y.f_82289_)
                    || Double.doubleToRawLongBits(x.f_82290_) != Double.doubleToRawLongBits(y.f_82290_)
                    || Double.doubleToRawLongBits(x.f_82291_) != Double.doubleToRawLongBits(y.f_82291_)
                    || Double.doubleToRawLongBits(x.f_82292_) != Double.doubleToRawLongBits(y.f_82292_)
                    || Double.doubleToRawLongBits(x.f_82293_) != Double.doubleToRawLongBits(y.f_82293_)) return false;
        }
        return true;
    }

    /** The box list of the combined shape in one pass, or null when any precondition in the class comment does not hold. */
    public static List<AABB> onePass(VoxelShape[] shapes) {
        int n = shapes.length;
        if (n == 0) return null;
        DiscreteVoxelShape[] voxels = new DiscreteVoxelShape[n];
        double[][][] coords = new double[3][n][];
        int[] total = new int[3];
        for (int k = 0; k < n; k++) {
            VoxelShape s = shapes[k];
            if (s == null || !SHAPE_CLASSES.contains(s.getClass().getName())) return null;
            VoxelShapeAccessor a = (VoxelShapeAccessor) s;
            DiscreteVoxelShape d = a.bons$voxels();
            voxels[k] = d;
            for (int ax = 0; ax < 3; ax++) {
                DoubleList list = a.bons$coords(AXES[ax]);
                int size = list.size();
                if (size < 2 || size != d.m_82850_(AXES[ax]) + 1) return null;
                double[] v = new double[size];
                for (int i = 0; i < size; i++) {
                    double x = list.getDouble(i);
                    if (!Double.isFinite(x) || Double.doubleToRawLongBits(x) == Long.MIN_VALUE) return null;   // NaN, infinities, -0.0
                    if (i > 0 && !(x > v[i - 1])) return null;
                    v[i] = x;
                }
                coords[ax][k] = v;
                total[ax] += size;
            }
        }
        double[][] grid = new double[3][];
        for (int ax = 0; ax < 3; ax++) {
            double[] all = new double[total[ax]];
            int p = 0;
            for (int k = 0; k < n; k++) {
                double[] v = coords[ax][k];
                System.arraycopy(v, 0, all, p, v.length);
                p += v.length;
            }
            Arrays.sort(all);
            int m = 0;
            for (int i = 0; i < all.length; i++) {
                if (m > 0 && all[i] == all[m - 1]) continue;
                if (m > 0 && !(all[i] - all[m - 1] > SEP)) return null;
                all[m++] = all[i];
            }
            grid[ax] = Arrays.copyOf(all, m);
        }
        int nx = grid[0].length - 1, ny = grid[1].length - 1, nz = grid[2].length - 1;
        if ((long) nx * ny * nz > MAX_CELLS) return null;
        BitSetDiscreteVoxelShape bits = new BitSetDiscreteVoxelShape(nx, ny, nz);
        for (int k = 0; k < n; k++) {
            int[] mx = indices(coords[0][k], grid[0]), my = indices(coords[1][k], grid[1]), mz = indices(coords[2][k], grid[2]);
            DiscreteVoxelShape d = voxels[k];
            int sx = mx.length - 1, sy = my.length - 1, sz = mz.length - 1;
            for (int i = 0; i < sx; i++)
                for (int j = 0; j < sy; j++)
                    for (int l = 0; l < sz; l++) {
                        if (!d.m_82846_(i, j, l)) continue;
                        for (int gx = mx[i]; gx < mx[i + 1]; gx++)
                            for (int gy = my[j]; gy < my[j + 1]; gy++)
                                for (int gz = mz[l]; gz < mz[l + 1]; gz++) bits.m_142703_(gx, gy, gz);
                    }
        }
        double[] xs = grid[0], ys = grid[1], zs = grid[2];
        ArrayList<AABB> boxes = new ArrayList<>();
        bits.m_82832_((x1, y1, z1, x2, y2, z2) -> boxes.add(new AABB(xs[x1], ys[y1], zs[z1], xs[x2], ys[y2], zs[z2])), true);
        if (!unitBoxesKeepValues(boxes, grid)) return null;
        return boxes;
    }

    /** Position of each value in the grid (every value is there, exactly). */
    private static int[] indices(double[] values, double[] grid) {
        int[] out = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            int at = Arrays.binarySearch(grid, values[i]);
            if (at < 0) throw new IllegalStateException("value missing from its own grid");
            out[i] = at;
        }
        return out;
    }

    /**
     * True when every box that Shapes.create would turn into a cube-grid shape has coordinates on eighths (so neither the
     * vanilla 2^b rounding nor Radium's eighth rounding moves them) and, if there is such a box, every grid value near the
     * unit cell is an eighth or more than SEP from every eighth (so the eighth grid lines those shapes add merge with
     * nothing).
     */
    private static boolean unitBoxesKeepValues(List<AABB> boxes, double[][] grid) throws IllegalStateException {
        boolean cube = false;
        for (AABB b : boxes) {
            if (!aligned(b.f_82288_, b.f_82291_) || !aligned(b.f_82289_, b.f_82292_) || !aligned(b.f_82290_, b.f_82293_)) continue;
            cube = true;
            if (!eighth(b.f_82288_) || !eighth(b.f_82289_) || !eighth(b.f_82290_) || !eighth(b.f_82291_) || !eighth(b.f_82292_)
                    || !eighth(b.f_82293_)) return false;
        }
        if (!cube) return true;
        for (double[] g : grid) {
            for (double v : g) {
                if (v < -SEP || v > 1 + SEP) continue;
                double e = v * 8.0, r = Math.rint(e);
                if (e != r && Math.abs(e - r) <= 8.0 * SEP) return false;
            }
        }
        return true;
    }

    /** Shapes.findBits(min, max) >= 0, i.e. Shapes.create would build a cube-grid shape on this axis. */
    private static boolean aligned(double min, double max) {
        if (FIND_BITS == null) return min >= -1.0E-7 && max <= 1.0000001;      // conservative: any box in the unit cell
        try {
            return (int) FIND_BITS.invokeExact(min, max) >= 0;
        } catch (Throwable t) {
            return min >= -1.0E-7 && max <= 1.0000001;
        }
    }

    private static boolean eighth(double v) {
        double e = v * 8.0;
        return e == Math.rint(e);
    }
}
