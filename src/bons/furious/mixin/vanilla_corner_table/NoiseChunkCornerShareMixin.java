package bons.furious.mixin.vanilla_corner_table;

import bons.furious.patch.vanilla_corner_table.CornerShare;
import java.util.List;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * vanilla_noise_corner_share (Minecraft 1.20.1 world generation, tested build 1.20.1 SRG; both sides): NoiseChunk's slice
 * fill through CornerShare's corner-column table.
 *
 * The constructor's return remembers the world generator (RandomState, NoiseGeneratorSettings) of one-cell NoiseChunks
 * without a blender - the ones terrain height queries build. At fillSlice (m_209220_) such a NoiseChunk asks CornerShare
 * once whether its world generator qualifies; if it does, fillSlice runs here: the same walk over the corner columns as
 * the original (cellStartBlockX, inCellX, per corner cellStartBlockZ, inCellZ and ++arrayInterpolationCounter, a final
 * ++arrayInterpolationCounter), each corner either copied from the table (interpolationCounter and the slice provider's
 * last position set to what the fill leaves; interpolators the table does not store are filled as usual) or filled with
 * every interpolator's fillArray in the original order and stored. Everything else runs the original method. Why a stored
 * corner equals a fresh fill is documented on CornerShare.
 */
@Mixin(value = NoiseChunk.class, remap = false)
public abstract class NoiseChunkCornerShareMixin implements CornerShare.Chunk {
    @Shadow @Final List<?> f_188725_;                                           // interpolators
    @Shadow @Final List<?> f_209160_;                                           // cellCaches
    @Shadow @Final int f_188718_;                                               // cellCountXZ
    @Shadow @Final int f_209170_;                                               // cellWidth
    @Shadow @Final private int f_188722_;                                       // firstCellZ
    @Shadow @Final private NoiseSettings f_188717_;                             // noiseSettings
    @Shadow @Final private DensityFunction.ContextProvider f_209159_;           // sliceFillingContextProvider
    @Shadow private int f_209150_;                                              // cellStartBlockX
    @Shadow int f_209151_;                                                      // cellStartBlockY
    @Shadow private int f_209152_;                                              // cellStartBlockZ
    @Shadow int f_209153_;                                                      // inCellX
    @Shadow int f_209154_;                                                      // inCellY
    @Shadow int f_209155_;                                                      // inCellZ
    @Shadow long f_209156_;                                                     // interpolationCounter
    @Shadow long f_209157_;                                                     // arrayInterpolationCounter
    @Shadow int f_209158_;                                                      // arrayIndex

    /** The world generator of a one-cell NoiseChunk without a blender (else null), and CornerShare's decision for it. */
    @Unique private RandomState bons$csRandom;
    @Unique private NoiseGeneratorSettings bons$csSettings;
    @Unique private CornerShare.Shape bons$csShape;

    @Override
    public List<?> bons$csInterpolators() {
        return this.f_188725_;
    }

    @Override
    public List<?> bons$csCellCaches() {
        return this.f_209160_;
    }

    @Override
    public NoiseSettings bons$csNoiseSettings() {
        return this.f_188717_;
    }

    /** Constructor return: remember the world generator of the NoiseChunks the table may serve. */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void bons$csRemember(int cellCountXZ, RandomState random, int startX, int startZ, NoiseSettings noise,
                                 DensityFunctions.BeardifierOrMarker beardifier, NoiseGeneratorSettings settings,
                                 Aquifer.FluidPicker picker, Blender blender, CallbackInfo ci) {
        if (cellCountXZ == 1 && blender == Blender.m_190153_() && CornerShare.enabled) {
            this.bons$csRandom = random;
            this.bons$csSettings = settings;
        }
    }

    /** fillSlice: the table walk for qualifying NoiseChunks (cancels the original), the original for every other one. */
    @Inject(method = "m_209220_", at = @At("HEAD"), cancellable = true)
    private void bons$csFillSlice(boolean first, int cellX, CallbackInfo ci) {
        if (this.bons$csRandom == null) return;
        CornerShare.Shape shape = this.bons$csShape;
        if (shape == null) this.bons$csShape = shape = CornerShare.shape(this, this.bons$csRandom, this.bons$csSettings);
        if (!shape.active()) return;
        List<?> interpolators = this.f_188725_;
        int n = interpolators.size();
        boolean[] stored = shape.stored;
        this.f_209150_ = cellX * this.f_209170_;
        this.f_209153_ = 0;
        for (int corner = 0; corner < this.f_188718_ + 1; ++corner) {
            int cellZ = this.f_188722_ + corner;
            this.f_209152_ = cellZ * this.f_209170_;
            this.f_209155_ = 0;
            ++this.f_209157_;
            int x = this.f_209150_, z = this.f_209152_;
            CornerShare.Entry e = shape.get(x, z);
            long start = this.f_209156_;
            if (e != null && !shape.verifyNext()) {
                for (int k = 0; k < n; k++) {
                    double[] slice = bons$slice(interpolators.get(k), first)[corner];
                    double[] column = e.columns[k];
                    if (column != null) System.arraycopy(column, 0, slice, 0, slice.length);
                    else ((DensityFunction) interpolators.get(k)).m_207362_(slice, this.f_209159_);
                }
                this.f_209156_ = start + e.increment;
                this.f_209151_ = e.lastY;
                this.f_209154_ = e.lastInCellY;
                this.f_209158_ = e.lastIndex;
                shape.served();
                continue;
            }
            double[][] fresh = new double[n][];
            for (int k = 0; k < n; k++) {
                double[] slice = bons$slice(interpolators.get(k), first)[corner];
                ((DensityFunction) interpolators.get(k)).m_207362_(slice, this.f_209159_);
                if (stored[k]) fresh[k] = slice.clone();
            }
            long increment = this.f_209156_ - start;
            if (e != null) shape.check(e, fresh, increment, this.f_209151_, this.f_209154_, this.f_209158_);
            else shape.put(new CornerShare.Entry(x, z, fresh, increment, this.f_209151_, this.f_209154_, this.f_209158_));
        }
        ++this.f_209157_;
        ci.cancel();
    }

    @Unique
    private static double[][] bons$slice(Object interpolator, boolean first) {
        NoiseInterpolatorSlicesAccessor a = (NoiseInterpolatorSlicesAccessor) interpolator;
        return first ? a.bons$csSlice0() : a.bons$csSlice1();
    }
}
