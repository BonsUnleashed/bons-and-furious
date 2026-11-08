package bons.furious.patch.integratedapi_c49;

import com.craisinlord.integrated_api.world.terrainadaptation.CustomAdaptation;
import com.craisinlord.integrated_api.world.terrainadaptation.EnhancedTerrainAdaptation;
import com.craisinlord.integrated_api.world.terrainadaptation.LargeCarvedTopNoBeardAdaptation;
import com.craisinlord.integrated_api.world.terrainadaptation.NoneAdaptation;
import com.craisinlord.integrated_api.world.terrainadaptation.SmallCarvedTopNoBeardAdaptation;
import com.craisinlord.integrated_api.world.terrainadaptation.TerrainAdaptationDirection;
import com.craisinlord.integrated_api.world.terrainadaptation.beardifier.EnhancedBeardifierData;
import com.craisinlord.integrated_api.world.terrainadaptation.beardifier.EnhancedBeardifierRigid;
import com.craisinlord.integrated_api.world.terrainadaptation.beardifier.EnhancedJigsawJunction;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import org.slf4j.Logger;

/**
 * Bons and Furious switch integratedapi_beardifier_influence_bounds (Integrated API, All Rights Reserved; 1.21.1 tested
 * build neoforge-1.21.1-1.9.1, world generation; both sides because the integrated server generates too). Mojang member
 * names. Idea: Collections Of Optimizations (integratedapi.skipEmptyBeardifier), idea text only; the per-block reach filter
 * is the one of vanilla_beardifier_influence_bounds, applied to Integrated API's own two adaptation lists.
 *
 * What it costs. Integrated API adds its own terrain adaptation to every Beardifier: a RETURN handler of
 * Beardifier.compute calls EnhancedBeardifierHelper.computeDensity for every block of a chunk near structures (196,608
 * calls in a 768-high chunk), and that walks every enhanced rigid piece and every enhanced jigsaw junction near the chunk:
 * box distances and a kernel lookup per term and per block. Outside a term's kernel the term is exactly +0.0, and a kernel
 * reaches only half its size around a piece box or a junction, so most terms of most blocks add +0.0. In chunks near
 * other structures (villages of vanilla or other mods) Integrated API's two lists are empty and the call adds nothing.
 *
 * What the switch does. At a Beardifier's first block, a snapshot of Integrated API's two lists (walked as
 * computeDensity walks them, then rewound) and each term's reach: a rigid with kernel size s (radius r = s / 2) reaches
 * [minX - e, maxX + e] x [minY - e, maxY + e] x [minZ - e, maxZ + e] with e = s - 1 - r; a junction [jx - r, jx + e] x
 * [jy - r, jy + e] x [jz - r, jz + e] (y mirrored to [jy - e, jy + r] for the UP direction); a NoneAdaptation term
 * reaches nothing. Per block column: the candidates (in list order) and their y range. Per block, the call of
 * computeDensity in Integrated API's handler (IapiBoundsMixin) runs on two views holding only the terms whose reach holds
 * the block, swapped into the Beardifier's two slots for the call and swapped back after it; when no term reaches the
 * block, the value comes back unchanged without the call. Integrated API's own loops and arithmetic do the adding. When
 * both lists are empty the value comes back unchanged at once; with one or two terms in all the full lists run (picking
 * the terms costs about what adding them does).
 *
 * Why the value is bit-identical. A term outside its kernel is the literal 0.0 (computeDensityFactor's range check; NONE
 * gives 0.0 too), times 0.8 or 0.4 still +0.0, and adding +0.0 changes a value only when that value is -0.0. The running
 * sum can be -0.0 only when the value handed in was -0.0, so a value of -0.0 (and NaN) runs the full lists. Kept terms
 * are added in the same order, terms inside a kernel are always kept (even when they are zero).
 *
 * Never filters (the full lists run) when: the Beardifier census of worldgen_empty_beardifier_marker (every Beardifier
 * mixin and Integrated API's helper by SHA-256) is not armed or missing; an iterator is null or not at its start; an
 * element is not exactly Integrated API's record; a rigid box is not exactly BoundingBox; a junction is not exactly
 * JigsawJunction; an adaptation is not exactly one of Integrated API's four classes (custom, large and small carved top,
 * none). A slot that another mod replaced after the snapshot is planned again.
 *
 * Ported to 1.21.1: Integrated API 1.9.1's BeardifierMixin, BeardifierAccessor and EnhancedBeardifierHelper are byte for byte
 * the 1.8.2 NeoForge builds the census already verifies (SHA-256 5c258c35..., 49f8f3e1..., 212ae482...);
 * computeDensityFactor, the kernel range and the four adaptation classes read the same; the 1.21.1 slot setters replace a
 * null iterator with an empty one, and the views handed in are never null.
 *
 * -Dbons_and_furious.integratedapiBeardifierInfluenceBounds=false: every call runs on the full lists (checked per call).
 * -Dbons_and_furious.integratedapiBeardifierInfluenceBounds.shadow=true (verification runs only): every block is computed
 * on the full lists (that value is used) and on the views, bit for bit compared (SHADOW_CHECKS / SHADOW_MISMATCHES).
 */
public final class IapiBounds {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.integratedapiBeardifierInfluenceBounds", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.integratedapiBeardifierInfluenceBounds.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Beardifiers planned / planned without filtering (diagnostics; the proof reads them). */
    public static final AtomicLong PLANNED = new AtomicLong(), UNFILTERED = new AtomicLong();
    private static volatile boolean announced, warnedUnfiltered;

    /** The per-Beardifier state IapiBoundsMixin adds to Beardifier. */
    public interface Holder {
        Plan bons$iapiPlan();

        void bons$iapiSetPlan(Plan plan);

        View bons$iapiRigidView();

        View bons$iapiJunctionView();
    }

    private IapiBounds() {
    }

    /** A Beardifier's snapshot of Integrated API's two lists and the reach of every term. */
    public static final class Plan {
        final ObjectListIterator<?> rigidIt, junctionIt;
        final boolean unfiltered;
        final Object[] rigids, junctions;
        final int[] rx0, rx1, ry0, ry1, rz0, rz1, jx0, jx1, jy0, jy1, jz0, jz1;
        // per-column candidates: slot (x & 15) | (z & 15) << 4, checked against the real x and z
        final int[] colX = new int[256], colZ = new int[256], colRLo = new int[256], colRHi = new int[256], colJLo = new int[256], colJHi = new int[256];
        final boolean[] colBuilt = new boolean[256];
        final int[][] colR = new int[256][], colJ = new int[256][];

        Plan(ObjectListIterator<?> rigidIt, ObjectListIterator<?> junctionIt, boolean unfiltered, Object[] rigids, Object[] junctions) {
            this.rigidIt = rigidIt;
            this.junctionIt = junctionIt;
            this.unfiltered = unfiltered;
            this.rigids = rigids;
            this.junctions = junctions;
            int n = rigids.length, m = junctions.length;
            rx0 = new int[n]; rx1 = new int[n]; ry0 = new int[n]; ry1 = new int[n]; rz0 = new int[n]; rz1 = new int[n];
            jx0 = new int[m]; jx1 = new int[m]; jy0 = new int[m]; jy1 = new int[m]; jz0 = new int[m]; jz1 = new int[m];
        }
    }

    private static final Object[] NO_TERMS = new Object[0];

    /**
     * The call of EnhancedBeardifierHelper.computeDensity in Integrated API's compute handler. beardifier = the Beardifier
     * (the handler's this, which is also data).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static double density(Object beardifier, DensityFunction.FunctionContext ctx, double density, EnhancedBeardifierData data,
                                 Operation<Double> original) {
        if (!enabled || data != beardifier || !(beardifier instanceof Holder h)
                || Double.doubleToRawLongBits(density) == Long.MIN_VALUE || density != density)
            return original.call(ctx, density, data);
        ObjectListIterator<?> rIt = data.getEnhancedRigidIterator(), jIt = data.getEnhancedJunctionIterator();
        Plan p = h.bons$iapiPlan();
        if (p == null || p.rigidIt != rIt || p.junctionIt != jIt) {
            p = plan(rIt, jIt);
            h.bons$iapiSetPlan(p);
        }
        if (p.unfiltered) return original.call(ctx, density, data);
        // both lists empty (a chunk near other mods' structures only): Integrated API's loops would add nothing
        if (p.rigids.length == 0 && p.junctions.length == 0 && !SHADOW) return density;
        // one or two terms: picking them costs about what adding them does (measured: one rigid +13 % with views)
        if (p.rigids.length + p.junctions.length <= 2 && !SHADOW) return original.call(ctx, density, data);
        int x = ctx.blockX(), y = ctx.blockY(), z = ctx.blockZ();
        View rv = h.bons$iapiRigidView(), jv = h.bons$iapiJunctionView();
        select(p, x, y, z, rv, jv);
        if (SHADOW) {
            double full = original.call(ctx, density, data);
            double viewed = rv.size == 0 && jv.size == 0 ? density : withViews(ctx, density, data, rIt, jIt, rv, jv, original);
            SHADOW_CHECKS.incrementAndGet();
            if (Double.doubleToRawLongBits(full) != Double.doubleToRawLongBits(viewed) && SHADOW_MISMATCHES.incrementAndGet() <= 20)
                LOGGER.warn("Bons and Furious: integratedapi_beardifier_influence_bounds shadow mismatch at {},{},{}: {} on the full lists, {} on the views ({} of {} rigids, {} of {} junctions)",
                        x, y, z, full, viewed, rv.size, p.rigids.length, jv.size, p.junctions.length);
            return full;
        }
        if (rv.size == 0 && jv.size == 0) return density;
        return withViews(ctx, density, data, rIt, jIt, rv, jv, original);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static double withViews(DensityFunction.FunctionContext ctx, double density, EnhancedBeardifierData data, ObjectListIterator rIt,
                                    ObjectListIterator jIt, View rv, View jv, Operation<Double> original) {
        rv.pos = 0;
        jv.pos = 0;
        data.setEnhancedRigidIterator((ObjectListIterator) rv);
        data.setEnhancedJunctionIterator((ObjectListIterator) jv);
        try {
            return original.call(ctx, density, data);
        } finally {
            data.setEnhancedRigidIterator(rIt);
            data.setEnhancedJunctionIterator(jIt);
        }
    }

    /** Builds the plan from Integrated API's two (start-positioned) iterators; walked as computeDensity walks them, then rewound. */
    static Plan plan(ObjectListIterator<?> rIt, ObjectListIterator<?> jIt) {
        PLANNED.incrementAndGet();
        try {
            String census = census();
            if (census != null)
                return unfiltered(rIt, jIt, "the Beardifier census of worldgen_empty_beardifier_marker stands down or is missing: " + census);
            if (rIt == null || jIt == null || rIt.hasPrevious() || jIt.hasPrevious()) return unfiltered(rIt, jIt, null);
            List<Object> rs = new ArrayList<>(), js = new ArrayList<>();
            try {
                while (rIt.hasNext()) rs.add(rIt.next());
                while (jIt.hasNext()) js.add(jIt.next());
            } finally {
                rIt.back(Integer.MAX_VALUE);   // as computeDensity leaves them, whatever happened while walking
                jIt.back(Integer.MAX_VALUE);
            }
            Plan p = new Plan(rIt, jIt, false, rs.isEmpty() ? NO_TERMS : rs.toArray(), js.isEmpty() ? NO_TERMS : js.toArray());
            for (int i = 0; i < p.rigids.length; i++) {
                if (!(p.rigids[i] instanceof EnhancedBeardifierRigid r) || r.getClass() != EnhancedBeardifierRigid.class) return unfiltered(rIt, jIt, null);
                BoundingBox box = r.pieceBoundingBox();
                EnhancedTerrainAdaptation a = r.pieceTerrainAdaptation();
                if (box == null || box.getClass() != BoundingBox.class || !known(a)) return unfiltered(rIt, jIt, null);
                if (a instanceof NoneAdaptation) {   // the NONE term is the literal 0.0 (also for NONE's twin instance)
                    p.rx0[i] = 1;
                    p.rx1[i] = 0;
                    continue;
                }
                int s = a.getKernelSize(), e = s - 1 - s / 2;
                if (e < 0) {
                    p.rx0[i] = 1;
                    p.rx1[i] = 0;
                    continue;
                }
                p.rx0[i] = box.minX() - e;
                p.rx1[i] = box.maxX() + e;
                p.ry0[i] = box.minY() - e;
                p.ry1[i] = box.maxY() + e;
                p.rz0[i] = box.minZ() - e;
                p.rz1[i] = box.maxZ() + e;
            }
            for (int i = 0; i < p.junctions.length; i++) {
                if (!(p.junctions[i] instanceof EnhancedJigsawJunction ej) || ej.getClass() != EnhancedJigsawJunction.class) return unfiltered(rIt, jIt, null);
                JigsawJunction j = ej.jigsawJunction();
                EnhancedTerrainAdaptation a = ej.pieceTerrainAdaptation();
                if (j == null || j.getClass() != JigsawJunction.class || !known(a)) return unfiltered(rIt, jIt, null);
                int s = a instanceof NoneAdaptation ? 0 : a.getKernelSize(), r = s / 2, e = s - 1 - r;
                if (s <= 0) {
                    p.jx0[i] = 1;
                    p.jx1[i] = 0;
                    continue;
                }
                int jx = j.getSourceX(), jy = j.getSourceGroundY(), jz = j.getSourceZ();
                p.jx0[i] = jx - r;
                p.jx1[i] = jx + e;
                p.jz0[i] = jz - r;
                p.jz1[i] = jz + e;
                if (a.direction() == TerrainAdaptationDirection.UP) {
                    p.jy0[i] = jy - e;
                    p.jy1[i] = jy + r;
                } else {
                    p.jy0[i] = jy - r;
                    p.jy1[i] = jy + e;
                }
            }
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: integratedapi_beardifier_influence_bounds planned its first Beardifier ({} enhanced pieces, {} enhanced junctions; terms outside their kernel are skipped){}",
                        p.rigids.length, p.junctions.length, SHADOW ? " - shadow verification on" : "");
            }
            return p;
        } catch (Throwable t) {
            return unfiltered(rIt, jIt, "the plan failed (" + t + ")");
        }
    }

    /** Integrated API's four adaptation classes (EnhancedTerrainAdaptation's constructors are package-private). */
    private static boolean known(EnhancedTerrainAdaptation a) {
        if (a == null) return false;
        Class<?> c = a.getClass();
        return c == CustomAdaptation.class || c == LargeCarvedTopNoBeardAdaptation.class || c == SmallCarvedTopNoBeardAdaptation.class
                || c == NoneAdaptation.class;
    }

    private static Plan unfiltered(ObjectListIterator<?> rIt, ObjectListIterator<?> jIt, String why) {
        UNFILTERED.incrementAndGet();
        if (why != null && !warnedUnfiltered) {
            warnedUnfiltered = true;
            LOGGER.info("Bons and Furious: integratedapi_beardifier_influence_bounds stands down: {}; Integrated API walks its full lists", why);
        }
        return new Plan(rIt, jIt, true, NO_TERMS, NO_TERMS);
    }

    private static volatile String censusResult;

    /**
     * The Beardifier census of worldgen_empty_beardifier_marker (bons.furious.patch.beardifier.EmptyBeardifiers.census():
     * every Beardifier mixin and the enhanced-adaptation helpers they call, by SHA-256), reused as is: null when armed,
     * else why not. Reached by reflection so this group builds on its own; a missing census class means stand down.
     */
    static String census() {
        String r = censusResult;
        if (r == null) {
            try {
                Class<?> c = Class.forName("bons.furious.patch.beardifier.EmptyBeardifiers", true, IapiBounds.class.getClassLoader());
                Object census = c.getMethod("census").invoke(null);
                boolean armed = (Boolean) census.getClass().getMethod("armed").invoke(census);
                r = armed ? "" : String.valueOf(census.getClass().getMethod("detail").invoke(census));
            } catch (Throwable t) {
                r = "the census could not be read (" + t + ")";
            }
            censusResult = r;
        }
        return r.isEmpty() ? null : r;
    }

    /** Fills the two views with the terms whose reach holds (x, y, z), in list order. */
    static void select(Plan p, int x, int y, int z, View rv, View jv) {
        int slot = (x & 15) | (z & 15) << 4;
        if (!p.colBuilt[slot] || p.colX[slot] != x || p.colZ[slot] != z) buildColumn(p, slot, x, z);
        rv.reset(p.rigids);
        if (y >= p.colRLo[slot] && y <= p.colRHi[slot]) {
            for (int i : p.colR[slot]) if (y >= p.ry0[i] && y <= p.ry1[i]) rv.add(i);
        }
        jv.reset(p.junctions);
        if (y >= p.colJLo[slot] && y <= p.colJHi[slot]) {
            for (int i : p.colJ[slot]) if (y >= p.jy0[i] && y <= p.jy1[i]) jv.add(i);
        }
    }

    private static void buildColumn(Plan p, int slot, int x, int z) {
        int[] r = new int[p.rigids.length];
        int nr = 0, rlo = Integer.MAX_VALUE, rhi = Integer.MIN_VALUE;
        for (int i = 0; i < p.rigids.length; i++) {
            if (p.rx0[i] > p.rx1[i] || x < p.rx0[i] || x > p.rx1[i] || z < p.rz0[i] || z > p.rz1[i]) continue;
            r[nr++] = i;
            rlo = Math.min(rlo, p.ry0[i]);
            rhi = Math.max(rhi, p.ry1[i]);
        }
        int[] j = new int[p.junctions.length];
        int nj = 0, jlo = Integer.MAX_VALUE, jhi = Integer.MIN_VALUE;
        for (int i = 0; i < p.junctions.length; i++) {
            if (p.jx0[i] > p.jx1[i] || x < p.jx0[i] || x > p.jx1[i] || z < p.jz0[i] || z > p.jz1[i]) continue;
            j[nj++] = i;
            jlo = Math.min(jlo, p.jy0[i]);
            jhi = Math.max(jhi, p.jy1[i]);
        }
        p.colR[slot] = Arrays.copyOf(r, nr);
        p.colJ[slot] = Arrays.copyOf(j, nj);
        p.colRLo[slot] = rlo;
        p.colRHi[slot] = rhi;
        p.colJLo[slot] = jlo;
        p.colJHi[slot] = jhi;
        p.colX[slot] = x;
        p.colZ[slot] = z;
        p.colBuilt[slot] = true;
    }

    /** A reusable ObjectListIterator over a subset of a snapshot, in snapshot order (one per Beardifier and list). */
    public static final class View implements ObjectListIterator<Object> {
        Object[] items = NO_TERMS;
        int[] idx = new int[8];
        int size, pos;

        void reset(Object[] items) {
            this.items = items;
            this.size = 0;
            this.pos = 0;
            if (this.idx.length < items.length) this.idx = new int[Math.max(items.length, 8)];
        }

        void add(int i) {
            this.idx[this.size++] = i;
        }

        public int size() {
            return this.size;
        }

        @Override
        public boolean hasNext() {
            return this.pos < this.size;
        }

        @Override
        public Object next() {
            if (this.pos >= this.size) throw new NoSuchElementException();
            return this.items[this.idx[this.pos++]];
        }

        @Override
        public boolean hasPrevious() {
            return this.pos > 0;
        }

        @Override
        public Object previous() {
            if (this.pos <= 0) throw new NoSuchElementException();
            return this.items[this.idx[--this.pos]];
        }

        @Override
        public int nextIndex() {
            return this.pos;
        }

        @Override
        public int previousIndex() {
            return this.pos - 1;
        }

        @Override
        public int back(int n) {
            int moved = Math.min(Math.max(n, 0), this.pos);
            this.pos -= moved;
            return moved;
        }

        @Override
        public int skip(int n) {
            int moved = Math.min(Math.max(n, 0), this.size - this.pos);
            this.pos += moved;
            return moved;
        }
    }
}
