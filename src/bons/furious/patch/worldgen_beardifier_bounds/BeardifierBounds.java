package bons.furious.patch.worldgen_beardifier_bounds;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_beardifier_influence_bounds (Minecraft 1.20.1 world generation, server side; Forge
 * 47.4.16; tested with the Beardifier mixins of Integrated API 1.5.1, YUNG's API 4.0.6, Moog's Structure Lib 3.3.1,
 * Qliphoth Awakening (fdbosses) 3.1.0.3, Lithostitched 1.4.11, Valhelsia Structures 1.1.2 and the Cataclysm and C2ME
 * accessors). SRG member names. Idea: C2ME pull request 552 (vanilla 26.1.2 has the same idea), idea text only.
 *
 * What it costs. Beardifier.compute runs once per block of a chunk near adapting structures (196,608 calls in a
 * 768-high chunk) and walks every rigid piece and every jigsaw junction near the chunk: box distances, a switch, a kernel
 * lookup or a square root for BURY, per piece and per block. Almost every term is +0.0: a piece only reaches 11-12 blocks
 * (5 horizontally for BURY) around its box, a junction 12 blocks around itself, and most of a tall column is far from all
 * of them. JFR (srv10_jfr1 pregeneration, 1.0.26): compute 3.0% of all samples (list iteration 1.1%, kernel 0.7%).
 *
 * What the switch does. The first compute call of a Beardifier takes a snapshot of its two lists (iterating them as
 * compute does, then rewinding) and the influence box of every term: BURY [minX-5, maxX+5] x [k-11, k+11] x [minZ-5,
 * maxZ+5] with k = minY + groundLevelDelta; BEARD_THIN [minX-11, maxX+11] x [k-12, k+11] x ...; BEARD_BOX [minX-11,
 * maxX+11] x [k-11, maxY+11] x ...; NONE nothing; a junction [jx-12, jx+11] x [jy-12, jy+11] x [jz-12, jz+11]. Per block
 * column it keeps the candidates (in list order) and their y range. Inside compute, the reads of the two iterator fields
 * return a view over only the terms whose box holds the block, in list order (BeardifierBoundsMixin). Vanilla's loops,
 * fdbosses' in-loop handler and Integrated API's RETURN handler all run as before; nothing is cancelled.
 *
 * Why the sum is bit-identical. Outside its box a term is exactly +0.0 (the kernel lookup returns the literal 0.0, the
 * BURY clamp returns 0.0 from distance 6 on, NONE is 0.0); no term is ever -0.0 and the sum starts at +0.0, so the sum is
 * never -0.0 and adding +0.0 changes nothing. The kept terms are added in the same order. Integrated API's handler adds
 * its own lists to that same value.
 *
 * Never filters (the original lists run) when: the 1.0.28 Beardifier census (EmptyBeardifiers.census, which checks every
 * Beardifier mixin and helper by SHA-256) is not armed; the object is a subclass; an iterator is not at its start; a rigid
 * has a box that is not exactly BoundingBox (fdbosses' handler adds its own term for its MalkuthStructureBoundingBox
 * pieces and must see them all); a list holds anything but vanilla's Rigid / JigsawJunction or an unknown adjustment.
 *
 * -Dbons_and_furious.vanillaBeardifierInfluenceBounds=false: the original lists (checked per new position).
 * -Dbons_and_furious.vanillaBeardifierInfluenceBounds.shadow=true (verification runs only): compute runs over the original
 * lists (the world stays vanilla) and every term the view would skip is evaluated with vanilla's own kernel methods and
 * must be exactly +0.0 (SHADOW_CHECKS = positions, SHADOW_TERMS = skipped terms checked, SHADOW_MISMATCHES).
 */
public final class BeardifierBounds {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.vanillaBeardifierInfluenceBounds", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.vanillaBeardifierInfluenceBounds.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_TERMS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Beardifiers planned / planned without filtering (diagnostics; the proof reads them). */
    public static final AtomicLong PLANNED = new AtomicLong(), UNFILTERED = new AtomicLong();
    private static volatile boolean announced;

    /** Vanilla's protected static term methods, reached through BeardifierBoundsMixin (Beardifier implements this). */
    public interface Kernels {
        double bons$bury(int dx, int dy, int dz);

        double bons$beard(int dx, int dy, int dz, int yToGround);
    }

    private BeardifierBounds() {
    }

    /** A Beardifier's snapshot and influence boxes. NONE = never filter this Beardifier. */
    public static final class Plan {
        final Object[] rigids, junctions;
        final int[] rx0, rx1, ry0, ry1, rz0, rz1, jx, jy, jz;
        final Object[] rigidAdj;
        final int[] rigidBox;   // per rigid: minX, maxX, minY(+delta=k), maxY, minZ, maxZ (6 ints each) for the shadow terms
        // per-column candidates: slot (x & 15) | (z & 15) << 4, keyed by the real x and z
        final int[] colX = new int[256], colZ = new int[256], colRLo = new int[256], colRHi = new int[256], colJLo = new int[256], colJHi = new int[256];
        final boolean[] colBuilt = new boolean[256];
        final int[][] colR = new int[256][], colJ = new int[256][];

        Plan(Object[] rigids, Object[] junctions) {
            this.rigids = rigids;
            this.junctions = junctions;
            int n = rigids.length, m = junctions.length;
            rx0 = new int[n]; rx1 = new int[n]; ry0 = new int[n]; ry1 = new int[n]; rz0 = new int[n]; rz1 = new int[n];
            rigidAdj = new Object[n];
            rigidBox = new int[n * 6];
            jx = new int[m]; jy = new int[m]; jz = new int[m];
        }
    }

    public static final Plan NONE = new Plan(new Object[0], new Object[0]);

    /**
     * Builds the plan of a Beardifier from its two (unread, start-positioned) iterators; NONE when it must not filter.
     * The iterators are walked exactly as compute walks them and rewound with back(Integer.MAX_VALUE).
     */
    public static Plan plan(Object beardifier, ObjectListIterator<?> rigidIt, ObjectListIterator<?> junctionIt) {
        PLANNED.incrementAndGet();
        try {
            String census = census();
            if (census != null) return unfiltered("the Beardifier census of worldgen_empty_beardifier_marker stands down or is missing: " + census);
            if (beardifier.getClass() != Beardifier.class) return unfiltered(null);
            if (rigidIt == null || junctionIt == null || rigidIt.hasPrevious() || junctionIt.hasPrevious()) return unfiltered(null);
            List<Object> rs = new ArrayList<>(), js = new ArrayList<>();
            try {
                while (rigidIt.hasNext()) rs.add(rigidIt.next());
                while (junctionIt.hasNext()) js.add(junctionIt.next());
            } finally {
                rigidIt.back(Integer.MAX_VALUE);   // as compute leaves them, whatever happened while walking
                junctionIt.back(Integer.MAX_VALUE);
            }
            Plan p = new Plan(rs.toArray(), js.toArray());
            for (int i = 0; i < p.rigids.length; i++) {
                if (!(p.rigids[i] instanceof Beardifier.Rigid r) || r.getClass() != Beardifier.Rigid.class) return unfiltered(null);
                BoundingBox box = r.f_223944_();
                if (box == null || box.getClass() != BoundingBox.class) return unfiltered(null);
                TerrainAdjustment adj = r.f_223945_();
                int minX = box.m_162395_(), maxX = box.m_162399_(), minY = box.m_162396_(), maxY = box.m_162400_(), minZ = box.m_162398_(), maxZ = box.m_162401_();
                int k = minY + r.f_223946_();
                int reach, ylo, yhi;
                if (adj == TerrainAdjustment.NONE) {
                    reach = 0;
                    ylo = 1;
                    yhi = 0;            // no influence
                } else if (adj == TerrainAdjustment.BURY) {
                    reach = 5;
                    ylo = k - 11;
                    yhi = k + 11;
                } else if (adj == TerrainAdjustment.BEARD_THIN) {
                    reach = 11;
                    ylo = k - 12;
                    yhi = k + 11;
                } else if (adj == TerrainAdjustment.BEARD_BOX) {
                    reach = 11;
                    ylo = k - 11;
                    yhi = maxY + 11;
                } else {
                    return unfiltered(null);
                }
                p.rx0[i] = minX - reach;
                p.rx1[i] = maxX + reach;
                p.rz0[i] = minZ - reach;
                p.rz1[i] = maxZ + reach;
                p.ry0[i] = ylo;
                p.ry1[i] = yhi;
                p.rigidAdj[i] = adj;
                int b = i * 6;
                p.rigidBox[b] = minX;
                p.rigidBox[b + 1] = maxX;
                p.rigidBox[b + 2] = k;
                p.rigidBox[b + 3] = maxY;
                p.rigidBox[b + 4] = minZ;
                p.rigidBox[b + 5] = maxZ;
            }
            for (int i = 0; i < p.junctions.length; i++) {
                if (!(p.junctions[i] instanceof JigsawJunction j) || j.getClass() != JigsawJunction.class) return unfiltered(null);
                p.jx[i] = j.m_210252_();
                p.jy[i] = j.m_210257_();
                p.jz[i] = j.m_210258_();
            }
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: vanilla_beardifier_influence_bounds filtered its first structure chunk ({} pieces, {} junctions; terms outside their reach are skipped){}",
                        p.rigids.length, p.junctions.length, SHADOW ? " - shadow verification on" : "");
            }
            return p;
        } catch (Throwable t) {
            return unfiltered("the plan failed (" + t + ")");
        }
    }

    private static volatile String censusResult;

    /**
     * The 1.0.28 Beardifier census (bons.furious.patch.beardifier.EmptyBeardifiers.census(): every Beardifier mixin and
     * the enhanced-adaptation helpers they call, by SHA-256), reused as is: null when armed, else why not. Reached by
     * reflection so this group builds on its own; a missing census class means stand down.
     */
    public static String census() {
        String r = censusResult;
        if (r == null) {
            try {
                Class<?> c = Class.forName("bons.furious.patch.beardifier.EmptyBeardifiers", true, BeardifierBounds.class.getClassLoader());
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

    private static boolean warnedUnfiltered;

    private static Plan unfiltered(String why) {
        UNFILTERED.incrementAndGet();
        if (why != null && !warnedUnfiltered) {
            warnedUnfiltered = true;
            LOGGER.info("Bons and Furious: vanilla_beardifier_influence_bounds stands down: {}; every Beardifier walks its full lists", why);
        }
        return NONE;
    }

    /**
     * Fills the two views with the terms whose influence box holds (x, y, z), in list order. Column candidates are built
     * once per column (slot = low 4 bits of x and z, checked against the real x and z).
     */
    public static void select(Plan p, int x, int y, int z, View rigidView, View junctionView) {
        int slot = (x & 15) | (z & 15) << 4;
        if (!p.colBuilt[slot] || p.colX[slot] != x || p.colZ[slot] != z) buildColumn(p, slot, x, z);
        rigidView.reset(p.rigids);
        if (y >= p.colRLo[slot] && y <= p.colRHi[slot]) {
            int[] cand = p.colR[slot];
            for (int i : cand) if (y >= p.ry0[i] && y <= p.ry1[i]) rigidView.add(i);
        }
        junctionView.reset(p.junctions);
        if (y >= p.colJLo[slot] && y <= p.colJHi[slot]) {
            int[] cand = p.colJ[slot];
            for (int i : cand) {
                int dy = y - p.jy[i];
                if (dy >= -12 && dy <= 11) junctionView.add(i);
            }
        }
    }

    private static void buildColumn(Plan p, int slot, int x, int z) {
        int[] r = new int[p.rigids.length];
        int nr = 0, rlo = Integer.MAX_VALUE, rhi = Integer.MIN_VALUE;
        for (int i = 0; i < p.rigids.length; i++) {
            if (p.ry0[i] > p.ry1[i] || x < p.rx0[i] || x > p.rx1[i] || z < p.rz0[i] || z > p.rz1[i]) continue;
            r[nr++] = i;
            rlo = Math.min(rlo, p.ry0[i]);
            rhi = Math.max(rhi, p.ry1[i]);
        }
        int[] j = new int[p.junctions.length];
        int nj = 0, jlo = Integer.MAX_VALUE, jhi = Integer.MIN_VALUE;
        for (int i = 0; i < p.junctions.length; i++) {
            int dx = x - p.jx[i], dz = z - p.jz[i];
            if (dx < -12 || dx > 11 || dz < -12 || dz > 11) continue;
            j[nj++] = i;
            jlo = Math.min(jlo, p.jy[i] - 12);
            jhi = Math.max(jhi, p.jy[i] + 11);
        }
        p.colR[slot] = java.util.Arrays.copyOf(r, nr);
        p.colJ[slot] = java.util.Arrays.copyOf(j, nj);
        p.colRLo[slot] = rlo;
        p.colRHi[slot] = rhi;
        p.colJLo[slot] = jlo;
        p.colJHi[slot] = jhi;
        p.colX[slot] = x;
        p.colZ[slot] = z;
        p.colBuilt[slot] = true;
    }

    /**
     * Shadow check of one position (compute itself runs over the original lists): every term the views leave out must
     * be exactly +0.0 when evaluated with vanilla's own kernel methods.
     */
    public static void shadow(Kernels k, Plan p, int x, int y, int z, View rigidView, View junctionView) {
        select(p, x, y, z, rigidView, junctionView);
        boolean[] kept = new boolean[p.rigids.length];
        for (int n = 0; n < rigidView.size; n++) kept[rigidView.idx[n]] = true;
        long terms = 0;
        boolean ok = true;
        String bad = null;
        for (int i = 0; i < p.rigids.length; i++) {
            if (kept[i]) continue;
            int b = i * 6;
            int minX = p.rigidBox[b], maxX = p.rigidBox[b + 1], kk = p.rigidBox[b + 2], maxY = p.rigidBox[b + 3], minZ = p.rigidBox[b + 4], maxZ = p.rigidBox[b + 5];
            int i1 = Math.max(0, Math.max(minX - x, x - maxX)), j1 = Math.max(0, Math.max(minZ - z, z - maxZ)), l1 = y - kk;
            Object adj = p.rigidAdj[i];
            double term;
            if (adj == TerrainAdjustment.NONE) term = 0.0;
            else if (adj == TerrainAdjustment.BURY) term = k.bons$bury(i1, l1, j1);
            else if (adj == TerrainAdjustment.BEARD_THIN) term = k.bons$beard(i1, l1, j1, l1) * 0.8;
            else term = k.bons$beard(i1, Math.max(0, Math.max(kk - y, y - maxY)), j1, l1) * 0.8;
            terms++;
            if (Double.doubleToRawLongBits(term) != 0L && ok) {
                ok = false;
                bad = "rigid " + i + " (" + adj + ") term " + term;
            }
        }
        boolean[] keptJ = new boolean[p.junctions.length];
        for (int n = 0; n < junctionView.size; n++) keptJ[junctionView.idx[n]] = true;
        for (int i = 0; i < p.junctions.length; i++) {
            if (keptJ[i]) continue;
            int dy = y - p.jy[i];
            double term = k.bons$beard(x - p.jx[i], dy, z - p.jz[i], dy) * 0.4;
            terms++;
            if (Double.doubleToRawLongBits(term) != 0L && ok) {
                ok = false;
                bad = "junction " + i + " term " + term;
            }
        }
        SHADOW_CHECKS.incrementAndGet();
        SHADOW_TERMS.addAndGet(terms);
        if (!ok && SHADOW_MISMATCHES.incrementAndGet() <= 20)
            LOGGER.warn("Bons and Furious: vanilla_beardifier_influence_bounds shadow mismatch at {},{},{}: a skipped {} is not +0.0", x, y, z, bad);
    }

    /** A reusable ObjectListIterator over a subset of a snapshot, in snapshot order (one per Beardifier and list). */
    public static final class View implements ObjectListIterator<Object> {
        Object[] items = new Object[0];
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
