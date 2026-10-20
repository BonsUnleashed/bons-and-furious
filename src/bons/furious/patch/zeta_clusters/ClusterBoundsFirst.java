package bons.furious.patch.zeta_clusters;

import com.mojang.logging.LogUtils;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.violetmoon.zeta.world.generator.multichunk.ClusterBasedGenerator;
import org.violetmoon.zeta.world.generator.multichunk.ClusterShape;

/**
 * Bons and Furious switch zeta_cluster_bounds_first (Zeta 1.0-24 with Quark 4.0-460, world generation, both sides; since
 * 1.0.46). Idea: ours.
 *
 * Zeta's cluster generator (Quark's big stone clusters: calcite, limestone, jasper, shale, myalite) visits every block
 * of each touched chunk's columns over the cluster's whole height and, for each, asks Quark's context canPlaceAt (a block
 * read plus Quark's replace predicate, a chain of ten or-ed lambdas) before the cluster shape test, which returns false at
 * once for any position outside the cluster's bounding ellipsoid ((dx/rx)^2 + (dy/ry)^2 + (dz/rz)^2 > 1). Most visited
 * positions lie outside it. The switch computes that same ellipsoid value first, from the same integers and radii in
 * the same order (a plain double expression: the same bits), and for a position outside it answers canPlaceAt "no"
 * without asking Quark. Identical because: outside the ellipsoid the shape test is false, so the position is never
 * consumed whatever canPlaceAt answers; and the skipped canPlaceAt is a pure read (BigStoneClusterGenerator's anonymous
 * context only: WorldGenRegion.getBlockState of a position in the chunk being decorated, including Radium's and Distant
 * Horizons' region versions, and Quark's predicate lambdas, which compare blocks and the level's dimension name). Any
 * other context (Quark's underground styles, whose height read can create a missing heightmap, or another mod's) and
 * any non-finite shape value (NaN) keep the original order. consume therefore sees the same positions in the same order.
 *
 * Runtime switch: -Dbons_and_furious.zetaClusterBoundsFirst=false. Shadow mode (verification runs):
 * -Dbons_and_furious.zetaClusterBoundsFirst.shadow=true also runs Zeta's own shape test for every skipped position and
 * counts it (SHADOW_CHECKS); a position the shape test calls inside is a mismatch (SHADOW_MISMATCHES, first 20 logged as
 * WARN; expect 0).
 */
public final class ClusterBoundsFirst {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.zetaClusterBoundsFirst", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.zetaClusterBoundsFirst.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Quark's BigStoneClusterGenerator context (an anonymous class): its canPlaceAt is a pure read (see above). */
    static final String PURE_CONTEXT = "org.violetmoon.quark.content.world.gen.BigStoneClusterGenerator$1";
    private static final ClassValue<Boolean> PURE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return type.getName().equals(PURE_CONTEXT);
        }
    };
    private static final AtomicBoolean ANNOUNCE = new AtomicBoolean();
    public static boolean announced;

    private ClusterBoundsFirst() {
    }

    /**
     * The redirected canPlaceAt in Zeta's per-block lambda: false without asking the context when the position is
     * outside the cluster's ellipsoid and the context is a known pure one; the context's own answer otherwise.
     */
    public static boolean canPlaceAt(ClusterBasedGenerator.IGenerationContext context, BlockPos pos, ClusterShape shape) {
        if (enabled && pure(context.getClass()) && outside(shape, pos)) {
            if (!announced) announce();
            if (SHADOW) shadow(shape, pos);
            return false;
        }
        return context.canPlaceAt(pos);
    }

    /** The whitelisted context class once seen (one identity compare per call from then on). */
    private static volatile Class<?> pureClass;

    static boolean pure(Class<?> type) {
        Class<?> p = pureClass;
        if (type == p) return true;
        if (p != null) return false;          // the pure class is known: this is another one
        boolean is = PURE.get(type);
        if (is) pureClass = type;
        return is;
    }

    /** True exactly when Zeta's shape test returns false at its first check: the ellipsoid value is above 1 (NaN: false). */
    static boolean outside(ClusterShape shape, BlockPos pos) {
        BlockPos src = shape.src();
        Vec3 radius = shape.radius();
        double dx = (double) (pos.m_123341_() - src.m_123341_()) / radius.f_82479_;
        double dy = (double) (pos.m_123342_() - src.m_123342_()) / radius.f_82480_;
        double dz = (double) (pos.m_123343_() - src.m_123343_()) / radius.f_82481_;
        return dx * dx + dy * dy + dz * dz > 1.0;
    }

    private static void shadow(ClusterShape shape, BlockPos pos) {
        SHADOW_CHECKS.incrementAndGet();
        if (shape.isInside(pos)) {
            long n = SHADOW_MISMATCHES.incrementAndGet();
            if (n <= 20) LOGGER.warn("Bons and Furious: zeta_cluster_bounds_first shadow mismatch #{}: {} is inside {} although its ellipsoid value is above 1",
                    n, pos.m_7949_(), shape);
        }
    }

    private static void announce() {
        if (ANNOUNCE.compareAndSet(false, true))
            LOGGER.info("Bons and Furious: zeta_cluster_bounds_first applies (Zeta's clusters test their shape before Quark's block check){}",
                    SHADOW ? " - shadow verification on" : "");
        announced = true;
    }
}
