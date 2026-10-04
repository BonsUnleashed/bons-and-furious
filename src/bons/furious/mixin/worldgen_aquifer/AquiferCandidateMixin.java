package bons.furious.mixin.worldgen_aquifer;

import bons.furious.patch.worldgen_aquifer.AquiferCandidates;
import bons.furious.patch.worldgen_aquifer.AquiferHighAir;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.apache.commons.lang3.mutable.MutableDouble;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * vanilla_aquifer_candidate_cache (Minecraft 1.20.1 world generation, server side; Forge 47.4.16): computeSubstance of
 * Aquifer$NoiseBasedAquifer through a per-aquifer table of grid cells (see AquiferCandidates for the cost, the method and
 * why every result, flag, status computation and location is vanilla's).
 *
 * Minecraft is no-copy: the fast path is our own implementation of the method's behaviour (table, branch-free ranking,
 * index reads); it calls vanilla's own fluid picker, computeFluid, calculatePressure and similarity, and every call it
 * cannot answer from the table (a cell with a location not yet computed, a cell outside the grid, the switch off, the
 * census standing down, a subclass) runs the original method unchanged.
 */
@Mixin(value = Aquifer.NoiseBasedAquifer.class, remap = false)
public abstract class AquiferCandidateMixin {
    @Shadow
    @Final
    private Aquifer.FluidPicker f_188411_;
    @Shadow
    @Final
    private Aquifer.FluidStatus[] f_157998_;
    @Shadow
    @Final
    private long[] f_157999_;
    @Shadow
    private boolean f_158000_;
    @Shadow
    @Final
    private int f_158002_;
    @Shadow
    @Final
    private int f_158003_;
    @Shadow
    @Final
    private int f_158004_;
    @Shadow
    @Final
    private int f_158005_;
    @Shadow
    @Final
    private int f_158006_;
    @Shadow
    @Final
    private static double f_196979_;

    /** The grid-cell table (lazily allocated; confined to the aquifer's thread like vanilla's own caches). */
    @Unique
    private int[] bons$cells;
    /** Shadow bookkeeping only (dead code unless AquiferCandidates.SHADOW). */
    @Unique
    private int bons$fetched, bons$computed, bons$ranks, bons$preNull;
    @Unique
    private boolean bons$highAirTaken;

    @Shadow
    private static double m_158024_(int a, int b) {
        throw new AssertionError();
    }

    @Shadow
    private double m_208188_(DensityFunction.FunctionContext ctx, MutableDouble barrier, Aquifer.FluidStatus a, Aquifer.FluidStatus b) {
        throw new AssertionError();
    }

    @Shadow
    private Aquifer.FluidStatus m_188447_(int x, int y, int z) {
        throw new AssertionError();
    }

    /**
     * computeSubstance: the table path, or the original wherever the table cannot answer. Kept tiny on purpose: it inlines
     * into computeSubstance, so the JIT can drop the Operation MixinExtras builds for the original (measured 0 B per call).
     */
    @WrapMethod(method = "m_207104_")
    private BlockState bons$computeSubstance(DensityFunction.FunctionContext ctx, double density, Operation<BlockState> original) {
        if (AquiferCandidates.SHADOW) return this.bons$shadowCall(ctx, density, original);
        Object state = this.bons$table(ctx, density);
        return state == AquiferCandidates.NO_ANSWER ? original.call(ctx, density) : (BlockState) state;
    }

    /** The table path; AquiferCandidates.NO_ANSWER when it cannot answer (the caller then runs the original). */
    @Unique
    private Object bons$table(DensityFunction.FunctionContext ctx, double density) {
        if (!AquiferCandidates.enabled || ((Object) this).getClass() != Aquifer.NoiseBasedAquifer.class || !AquiferCandidates.armed())
            return AquiferCandidates.NO_ANSWER;
        int x = ctx.m_207115_(), y = ctx.m_207114_(), z = ctx.m_207113_();
        if (density > 0.0) {
            this.f_158000_ = false;
            return null;
        }
        int gx = Math.floorDiv(x - 5, 16), gy = Math.floorDiv(y + 1, 12), gz = Math.floorDiv(z - 5, 16);
        int[] t = this.bons$cells;
        if (t == null) this.bons$cells = t = AquiferCandidates.newCells();
        int off = AquiferCandidates.slot(gx, gy, gz);
        if ((t[off] != gx || t[off + 1] != gy || t[off + 2] != gz) && !this.bons$fill(t, off, gx, gy, gz)) return AquiferCandidates.NO_ANSWER;
        return this.bons$fast(ctx, density, t, off, x, y, z);
    }

    /** Shadow verification entry (AquiferCandidates.SHADOW only): same preconditions as bons$table. */
    @Unique
    private BlockState bons$shadowCall(DensityFunction.FunctionContext ctx, double density, Operation<BlockState> original) {
        if (!AquiferCandidates.enabled || ((Object) this).getClass() != Aquifer.NoiseBasedAquifer.class || !AquiferCandidates.armed() || density > 0.0)
            return original.call(ctx, density);
        int x = ctx.m_207115_(), y = ctx.m_207114_(), z = ctx.m_207113_();
        int gx = Math.floorDiv(x - 5, 16), gy = Math.floorDiv(y + 1, 12), gz = Math.floorDiv(z - 5, 16);
        int[] t = this.bons$cells;
        if (t == null) this.bons$cells = t = AquiferCandidates.newCells();
        int off = AquiferCandidates.slot(gx, gy, gz);
        if ((t[off] != gx || t[off + 1] != gy || t[off + 2] != gz) && !this.bons$fill(t, off, gx, gy, gz)) return original.call(ctx, density);
        return this.bons$shadow(ctx, density, original, t, off, x, y, z);
    }

    /**
     * Fills a table slot from the aquifer's location cache; false (the caller runs the original) when one of the twelve
     * cells lies outside the grid, has no location yet (vanilla's loop computes it), or holds a location outside its own
     * cell (then vanilla's status index would differ from the slot's).
     */
    @Unique
    private boolean bons$fill(int[] t, int off, int gx, int gy, int gz) {
        long[] locations = this.f_157999_;
        int sx = this.f_158005_, sz = this.f_158006_;
        int sy = locations.length / (sx * sz);
        int rx = gx - this.f_158002_, ry = gy - this.f_158003_, rz = gz - this.f_158004_;
        if (rx < 0 || rx + 1 >= sx || rz < 0 || rz + 1 >= sz || ry < 1 || ry + 1 >= sy) return false;
        t[off] = Integer.MIN_VALUE;   // the slot is invalid until all twelve candidates are written (a failed fill leaves no stale entry)
        int base = (ry * sz + rz) * sx + rx;
        for (int k = 0; k < 12; k++) {
            int cx = gx + AquiferCandidates.DX[k], cy = gy + AquiferCandidates.DY[k], cz = gz + AquiferCandidates.DZ[k];
            int index = base + (AquiferCandidates.DY[k] * sz + AquiferCandidates.DZ[k]) * sx + AquiferCandidates.DX[k];
            long pos = locations[index];
            if (pos == Long.MAX_VALUE) return false;
            int px = BlockPos.m_121983_(pos), py = BlockPos.m_122008_(pos), pz = BlockPos.m_122015_(pos);
            if (Math.floorDiv(px, 16) != cx || Math.floorDiv(py, 12) != cy || Math.floorDiv(pz, 16) != cz) return false;
            t[off + AquiferCandidates.IDX + k] = index;
            t[off + AquiferCandidates.PX + k] = px;
            t[off + AquiferCandidates.PY + k] = py;
            t[off + AquiferCandidates.PZ + k] = pz;
        }
        t[off] = gx;
        t[off + 1] = gy;
        t[off + 2] = gz;
        return true;
    }

    /** The status of slot k: vanilla's getAquiferStatus by index (computeFluid when missing, stored as vanilla stores it). */
    @Unique
    private Aquifer.FluidStatus bons$status(Aquifer.FluidStatus[] statuses, int[] t, int off, int k) {
        int index = t[off + AquiferCandidates.IDX + k];
        Aquifer.FluidStatus s = statuses[index];
        if (s == null) {
            if (AquiferCandidates.SHADOW) this.bons$computed++;
            s = this.m_188447_(t[off + AquiferCandidates.PX + k], t[off + AquiferCandidates.PY + k], t[off + AquiferCandidates.PZ + k]);
            statuses[index] = s;
        }
        if (AquiferCandidates.SHADOW) this.bons$fetched |= 1 << k;
        return s;
    }

    @Unique
    private BlockState bons$fast(DensityFunction.FunctionContext ctx, double density, int[] t, int off, int x, int y, int z) {
        Aquifer.FluidPicker picker = this.f_188411_;
        if (picker.m_183538_(x, y, z).m_188405_(y).m_60713_(Blocks.f_49991_)) {
            this.f_158000_ = false;
            return Blocks.f_49991_.m_49966_();
        }
        // the three smallest keys (distance << 4 | 15 - slot) are vanilla's ranks 1, 2, 3, ties included
        int m1 = Integer.MAX_VALUE, m2 = Integer.MAX_VALUE, m3 = Integer.MAX_VALUE;
        for (int k = 0; k < 12; k++) {
            int dx = t[off + AquiferCandidates.PX + k] - x, dy = t[off + AquiferCandidates.PY + k] - y, dz = t[off + AquiferCandidates.PZ + k] - z;
            int v = (dx * dx + dy * dy + dz * dz) << 4 | (15 - k);
            int c = Math.max(m1, v);
            m1 = Math.min(m1, v);
            int c2 = Math.max(m2, c);
            m2 = Math.min(m2, c);
            m3 = Math.min(m3, c2);
        }
        int k1 = 15 - (m1 & 15), k2 = 15 - (m2 & 15), k3 = 15 - (m3 & 15);
        int d1 = m1 >> 4, d2 = m2 >> 4, d3 = m3 >> 4;
        if (AquiferCandidates.SHADOW) {
            this.bons$ranks = k1 | k2 << 4 | k3 << 8;
            this.bons$highAirTaken = false;
        }
        Aquifer.FluidStatus[] statuses = this.f_157998_;
        Aquifer.FluidStatus s1 = this.bons$status(statuses, t, off, k1);
        double sim12 = m_158024_(d1, d2);
        BlockState state = s1.m_188405_(y);
        if (sim12 <= 0.0) {
            this.f_158000_ = sim12 >= f_196979_;
            return state;
        }
        if (state.m_60713_(Blocks.f_49990_) && picker.m_183538_(x, y - 1, z).m_188405_(y - 1).m_60713_(Blocks.f_49991_)) {
            this.f_158000_ = true;
            return state;
        }
        if (((Object) this) instanceof AquiferHighAir && AquiferCandidates.highAirEnabled) {
            Aquifer.FluidStatus c2 = statuses[t[off + AquiferCandidates.IDX + k2]], c3 = statuses[t[off + AquiferCandidates.IDX + k3]];
            // shadow runs after the original filled the cache: judge "already cached" by the state before that call
            if (AquiferCandidates.SHADOW && ((this.bons$preNull >> k2 & 1) != 0 || (this.bons$preNull >> k3 & 1) != 0)) c2 = null;
            int top = y - 5;
            if (c2 != null && c3 != null && ((FluidStatusLevelAccessor) (Object) s1).bons$fluidLevel() <= top
                    && ((FluidStatusLevelAccessor) (Object) c2).bons$fluidLevel() <= top && ((FluidStatusLevelAccessor) (Object) c3).bons$fluidLevel() <= top) {
                if (AquiferCandidates.SHADOW) this.bons$highAirTaken = true;
                this.f_158000_ = true;
                return state;
            }
        }
        MutableDouble barrier = new MutableDouble(Double.NaN);
        Aquifer.FluidStatus s2 = this.bons$status(statuses, t, off, k2);
        double q12 = sim12 * this.m_208188_(ctx, barrier, s1, s2);
        if (density + q12 > 0.0) {
            this.f_158000_ = false;
            return null;
        }
        Aquifer.FluidStatus s3 = this.bons$status(statuses, t, off, k3);
        double sim13 = m_158024_(d1, d3);
        if (sim13 > 0.0) {
            double q13 = sim12 * sim13 * this.m_208188_(ctx, barrier, s1, s3);
            if (density + q13 > 0.0) {
                this.f_158000_ = false;
                return null;
            }
        }
        double sim23 = m_158024_(d2, d3);
        if (sim23 > 0.0) {
            double q23 = sim12 * sim23 * this.m_208188_(ctx, barrier, s2, s3);
            if (density + q23 > 0.0) {
                this.f_158000_ = false;
                return null;
            }
        }
        this.f_158000_ = true;
        return state;
    }

    /**
     * Shadow verification (AquiferCandidates.SHADOW): the original runs first and its result is returned; then the fast
     * path runs on the state the original left and must not need any status the original did not compute.
     */
    @Unique
    private BlockState bons$shadow(DensityFunction.FunctionContext ctx, double density, Operation<BlockState> original, int[] t, int off, int x, int y, int z) {
        Aquifer.FluidStatus[] statuses = this.f_157998_;
        int before = 0;
        for (int k = 0; k < 12; k++) if (statuses[t[off + AquiferCandidates.IDX + k]] == null) before |= 1 << k;
        BlockState vanilla = original.call(ctx, density);
        boolean vanillaFlag = this.f_158000_;
        int computedByVanilla = 0;
        for (int k = 0; k < 12; k++)
            if ((before >> k & 1) != 0 && statuses[t[off + AquiferCandidates.IDX + k]] != null) computedByVanilla |= 1 << k;
        this.bons$fetched = 0;
        this.bons$computed = 0;
        this.bons$ranks = -1;
        this.bons$preNull = before;
        BlockState ours = this.bons$fast(ctx, density, t, off, x, y, z);
        boolean oursFlag = this.f_158000_;
        this.f_158000_ = vanillaFlag;
        int wouldCompute = this.bons$fetched & before;
        int reference = AquiferCandidates.referenceRanks(t, off, x, y, z);
        boolean ranksChecked = this.bons$ranks != -1;
        boolean same = ours == vanilla && oursFlag == vanillaFlag && this.bons$computed == 0 && wouldCompute == computedByVanilla
                && (!ranksChecked || this.bons$ranks == reference);
        AquiferCandidates.shadow(same, this.bons$highAirTaken, same ? "" : "at " + x + "," + y + "," + z + " density " + density + ": result "
                + vanilla + "/" + vanillaFlag + " vs " + ours + "/" + oursFlag + ", statuses computed by the original "
                + Integer.toBinaryString(computedByVanilla) + " vs needed " + Integer.toBinaryString(wouldCompute) + " (+"
                + this.bons$computed + " extra), ranks " + Integer.toHexString(this.bons$ranks) + " vs " + Integer.toHexString(reference));
        return vanilla;
    }
}
