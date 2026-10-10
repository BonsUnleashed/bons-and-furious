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
 * vanilla_noise_corner_share (Minecraft 1.21.1 world generation, tested build NeoForge 21.1.252; both sides): NoiseChunk's
 * slice fill through CornerShare's corner-column table.
 *
 * The constructor's return remembers the world generator (RandomState, NoiseGeneratorSettings) of one-cell NoiseChunks
 * without a blender - the ones terrain height queries build. At fillSlice such a NoiseChunk asks CornerShare once whether
 * its world generator qualifies; if it does, fillSlice runs here: the same walk over the corner columns as the original
 * (cellStartBlockX, inCellX, per corner cellStartBlockZ, inCellZ and ++arrayInterpolationCounter, a final
 * ++arrayInterpolationCounter), each corner either copied from the table (interpolationCounter and the slice provider's
 * last position set to what the fill leaves; interpolators the table does not store are filled as usual) or filled with
 * every interpolator's fillArray in the original order and stored. Everything else runs the original method. Why a stored
 * corner equals a fresh fill is documented on CornerShare.
 *
 * Ported to 1.21.1: fillSlice, initializeForFirstCellX / advanceCellX, the slice provider (NoiseChunk$1) and every
 * shadowed field are unchanged; Mojang names, nothing else changed.
 */
@Mixin(value = NoiseChunk.class, remap = false)
public abstract class NoiseChunkCornerShareMixin implements CornerShare.Chunk {
    @Shadow @Final List<?> interpolators;
    @Shadow @Final List<?> cellCaches;
    @Shadow @Final int cellCountXZ;
    @Shadow @Final int cellWidth;
    @Shadow @Final private int firstCellZ;
    @Shadow @Final private NoiseSettings noiseSettings;
    @Shadow @Final private DensityFunction.ContextProvider sliceFillingContextProvider;
    @Shadow private int cellStartBlockX;
    @Shadow int cellStartBlockY;
    @Shadow private int cellStartBlockZ;
    @Shadow int inCellX;
    @Shadow int inCellY;
    @Shadow int inCellZ;
    @Shadow long interpolationCounter;
    @Shadow long arrayInterpolationCounter;
    @Shadow int arrayIndex;

    /** The world generator of a one-cell NoiseChunk without a blender (else null), and CornerShare's decision for it. */
    @Unique private RandomState bons$csRandom;
    @Unique private NoiseGeneratorSettings bons$csSettings;
    @Unique private CornerShare.Shape bons$csShape;

    @Override
    public List<?> bons$csInterpolators() {
        return this.interpolators;
    }

    @Override
    public List<?> bons$csCellCaches() {
        return this.cellCaches;
    }

    @Override
    public NoiseSettings bons$csNoiseSettings() {
        return this.noiseSettings;
    }

    /** Constructor return: remember the world generator of the NoiseChunks the table may serve. */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void bons$csRemember(int cellCountXZ, RandomState random, int startX, int startZ, NoiseSettings noise,
                                 DensityFunctions.BeardifierOrMarker beardifier, NoiseGeneratorSettings settings,
                                 Aquifer.FluidPicker picker, Blender blender, CallbackInfo ci) {
        if (cellCountXZ == 1 && blender == Blender.empty() && CornerShare.enabled) {
            this.bons$csRandom = random;
            this.bons$csSettings = settings;
        }
    }

    /** fillSlice: the table walk for qualifying NoiseChunks (cancels the original), the original for every other one. */
    @Inject(method = "fillSlice", at = @At("HEAD"), cancellable = true)
    private void bons$csFillSlice(boolean first, int cellX, CallbackInfo ci) {
        if (this.bons$csRandom == null) return;
        CornerShare.Shape shape = this.bons$csShape;
        if (shape == null) this.bons$csShape = shape = CornerShare.shape(this, this.bons$csRandom, this.bons$csSettings);
        if (!shape.active()) return;
        List<?> interpolators = this.interpolators;
        int n = interpolators.size();
        boolean[] stored = shape.stored;
        this.cellStartBlockX = cellX * this.cellWidth;
        this.inCellX = 0;
        for (int corner = 0; corner < this.cellCountXZ + 1; ++corner) {
            int cellZ = this.firstCellZ + corner;
            this.cellStartBlockZ = cellZ * this.cellWidth;
            this.inCellZ = 0;
            ++this.arrayInterpolationCounter;
            int x = this.cellStartBlockX, z = this.cellStartBlockZ;
            CornerShare.Entry e = shape.get(x, z);
            long start = this.interpolationCounter;
            if (e != null && !shape.verifyNext()) {
                for (int k = 0; k < n; k++) {
                    double[] slice = bons$slice(interpolators.get(k), first)[corner];
                    double[] column = e.columns[k];
                    if (column != null) System.arraycopy(column, 0, slice, 0, slice.length);
                    else ((DensityFunction) interpolators.get(k)).fillArray(slice, this.sliceFillingContextProvider);
                }
                this.interpolationCounter = start + e.increment;
                this.cellStartBlockY = e.lastY;
                this.inCellY = e.lastInCellY;
                this.arrayIndex = e.lastIndex;
                shape.served();
                continue;
            }
            double[][] fresh = new double[n][];
            for (int k = 0; k < n; k++) {
                double[] slice = bons$slice(interpolators.get(k), first)[corner];
                ((DensityFunction) interpolators.get(k)).fillArray(slice, this.sliceFillingContextProvider);
                if (stored[k]) fresh[k] = slice.clone();
            }
            long increment = this.interpolationCounter - start;
            if (e != null) shape.check(e, fresh, increment, this.cellStartBlockY, this.inCellY, this.arrayIndex);
            else shape.put(new CornerShare.Entry(x, z, fresh, increment, this.cellStartBlockY, this.inCellY, this.arrayIndex));
        }
        ++this.arrayInterpolationCounter;
        ci.cancel();
    }

    @Unique
    private static double[][] bons$slice(Object interpolator, boolean first) {
        NoiseInterpolatorSlicesAccessor a = (NoiseInterpolatorSlicesAccessor) interpolator;
        return first ? a.bons$csSlice0() : a.bons$csSlice1();
    }
}
