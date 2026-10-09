package bons.furious.mixin.vanilla_cellfill;

import bons.furious.patch.vanilla_cellfill.ColumnCellFill;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_noise_column_cell_fill (Minecraft 1.20.1 world generation, tested build 1.20.1 SRG; both sides), part 2 of 2:
 * NoiseChunk's cell-cache fill.
 *
 * NoiseChunk.selectCellYZ (m_188810_) fills every cell cache for the whole cell. For a NoiseChunk iterateNoiseColumn
 * built and ColumnCellFill accepted (part 1 stores a {@link ColumnCellFill.Column} on it), the wrapped fillArray call
 * fills only the queried column, through ColumnCellFill.Column, which sets this NoiseChunk's in-cell position the way
 * forIndex does (bons$cfPosition). Every other NoiseChunk, and any call with another context provider, runs the
 * original call. Why the values read are identical is documented on ColumnCellFill.
 */
@Mixin(value = NoiseChunk.class, remap = false)
public abstract class NoiseChunkColumnFillMixin implements ColumnCellFill.Cells {
    @Shadow @Final List<?> f_209160_;                      // cellCaches
    @Shadow @Final int f_209170_;                          // cellWidth
    @Shadow @Final int f_209171_;                          // cellHeight
    @Shadow @Final int f_188718_;                          // cellCountXZ
    @Shadow @Final private Blender f_188731_;              // blender
    @Shadow @Final private NoiseSettings f_188717_;        // noiseSettings
    @Shadow int f_209153_;                                 // inCellX
    @Shadow int f_209154_;                                 // inCellY
    @Shadow int f_209155_;                                 // inCellZ
    @Shadow int f_209158_;                                 // arrayIndex

    /** The queried column, when iterateNoiseColumn built this NoiseChunk and ColumnCellFill accepted it; otherwise null. */
    @Unique
    private ColumnCellFill.Column bons$cellFillColumn;

    @Override
    public List<?> bons$cfCaches() {
        return this.f_209160_;
    }

    @Override
    public int bons$cfWidth() {
        return this.f_209170_;
    }

    @Override
    public int bons$cfHeight() {
        return this.f_209171_;
    }

    @Override
    public int bons$cfCountXZ() {
        return this.f_188718_;
    }

    @Override
    public Blender bons$cfBlender() {
        return this.f_188731_;
    }

    @Override
    public NoiseSettings bons$cfNoiseSettings() {
        return this.f_188717_;
    }

    @Override
    public DensityFunction.FunctionContext bons$cfPosition(int inCellX, int inCellY, int inCellZ, int arrayIndex) {
        this.f_209153_ = inCellX;
        this.f_209154_ = inCellY;
        this.f_209155_ = inCellZ;
        this.f_209158_ = arrayIndex;
        return (DensityFunction.FunctionContext) (Object) this;
    }

    @Override
    public ColumnCellFill.Column bons$cfColumn() {
        return this.bons$cellFillColumn;
    }

    @Override
    public void bons$cfColumn(ColumnCellFill.Column column) {
        this.bons$cellFillColumn = column;
    }

    /** selectCellYZ: the cell-cache fill (noiseFiller.fillArray(values, this)). */
    @WrapOperation(method = "m_188810_", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/DensityFunction;m_207362_([DLnet/minecraft/world/level/levelgen/DensityFunction$ContextProvider;)V"))
    private void bons$cfColumnCellFill(DensityFunction filler, double[] values, DensityFunction.ContextProvider provider, Operation<Void> original) {
        ColumnCellFill.Column column = this.bons$cellFillColumn;
        if (column == null || provider != (Object) this) original.call(filler, values, provider);
        else column.fill(filler, values, provider, original);
    }
}
