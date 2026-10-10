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
 * vanilla_noise_column_cell_fill (Minecraft 1.21.1 world generation, tested build NeoForge 21.1.252; both sides), part 2
 * of 2: NoiseChunk's cell-cache fill.
 *
 * NoiseChunk.selectCellYZ fills every cell cache for the whole cell. For a NoiseChunk iterateNoiseColumn built and
 * ColumnCellFill accepted (part 1 stores a {@link ColumnCellFill.Column} on it), the wrapped fillArray call fills only
 * the queried column, through ColumnCellFill.Column, which sets this NoiseChunk's in-cell position the way forIndex does
 * (bons$cfPosition). Every other NoiseChunk, and any call with another context provider, runs the original call. Why the
 * values read are identical is documented on ColumnCellFill.
 *
 * Ported to 1.21.1: selectCellYZ, the shadowed fields and forIndex / fillAllDirectly are unchanged; Mojang names. Next to
 * Generator Accelerator (its MixinNoiseChunk @Overwrites selectCellYZ: an injector into it would be refused and the class
 * would fail to load) and C2ME's density-function compiler (its MixinChunkNoiseSampler @Overwrites updateForY/X/Z, which
 * the exactness argument relies on) the switch steps aside; see patches/vanilla_cellfill.json.
 */
@Mixin(value = NoiseChunk.class, remap = false)
public abstract class NoiseChunkColumnFillMixin implements ColumnCellFill.Cells {
    @Shadow @Final List<?> cellCaches;
    @Shadow @Final int cellWidth;
    @Shadow @Final int cellHeight;
    @Shadow @Final int cellCountXZ;
    @Shadow @Final private Blender blender;
    @Shadow @Final private NoiseSettings noiseSettings;
    @Shadow int inCellX;
    @Shadow int inCellY;
    @Shadow int inCellZ;
    @Shadow int arrayIndex;

    /** The queried column, when iterateNoiseColumn built this NoiseChunk and ColumnCellFill accepted it; otherwise null. */
    @Unique
    private ColumnCellFill.Column bons$cellFillColumn;

    @Override
    public List<?> bons$cfCaches() {
        return this.cellCaches;
    }

    @Override
    public int bons$cfWidth() {
        return this.cellWidth;
    }

    @Override
    public int bons$cfHeight() {
        return this.cellHeight;
    }

    @Override
    public int bons$cfCountXZ() {
        return this.cellCountXZ;
    }

    @Override
    public Blender bons$cfBlender() {
        return this.blender;
    }

    @Override
    public NoiseSettings bons$cfNoiseSettings() {
        return this.noiseSettings;
    }

    @Override
    public DensityFunction.FunctionContext bons$cfPosition(int inCellX, int inCellY, int inCellZ, int arrayIndex) {
        this.inCellX = inCellX;
        this.inCellY = inCellY;
        this.inCellZ = inCellZ;
        this.arrayIndex = arrayIndex;
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
    @WrapOperation(method = "selectCellYZ", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/DensityFunction;fillArray([DLnet/minecraft/world/level/levelgen/DensityFunction$ContextProvider;)V"))
    private void bons$cfColumnCellFill(DensityFunction filler, double[] values, DensityFunction.ContextProvider provider, Operation<Void> original) {
        ColumnCellFill.Column column = this.bons$cellFillColumn;
        if (column == null || provider != (Object) this) original.call(filler, values, provider);
        else column.fill(filler, values, provider, original);
    }
}
