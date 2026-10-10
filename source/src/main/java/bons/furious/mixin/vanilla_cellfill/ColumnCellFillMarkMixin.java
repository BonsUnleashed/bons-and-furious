package bons.furious.mixin.vanilla_cellfill;

import bons.furious.patch.vanilla_cellfill.ColumnCellFill;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import java.util.function.Predicate;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_noise_column_cell_fill (Minecraft 1.21.1 world generation, tested build NeoForge 21.1.252; both sides), part 1
 * of 2: NoiseBasedChunkGenerator.iterateNoiseColumn, the walk behind getBaseHeight, getBaseColumn and every other
 * terrain-height query.
 *
 * The one-cell NoiseChunk it builds (its NEW) is handed, unchanged, to ColumnCellFill.mark together with the queried
 * column (x, z) before it starts interpolating; mark remembers the column on that NoiseChunk when the generator's
 * cell-cache graph qualifies, and part 2 then fills only that column of each cell. The target method's arguments are
 * captured as trailing handler parameters (no @Local: that would need MixinExtras' generated reference classes).
 *
 * Ported to 1.21.1: iterateNoiseColumn is unchanged (the same one-cell NoiseChunk NEW with Blender.empty() and the
 * beardifier marker, the same walk); Mojang names, nothing else changed.
 */
@Mixin(value = NoiseBasedChunkGenerator.class, remap = false)
public abstract class ColumnCellFillMarkMixin {
    /** iterateNoiseColumn: the NoiseChunk it constructs for the column. */
    @ModifyExpressionValue(method = "iterateNoiseColumn", at = @At(value = "NEW", target = "net/minecraft/world/level/levelgen/NoiseChunk"))
    private NoiseChunk bons$markColumn(NoiseChunk chunk, LevelHeightAccessor level, RandomState random, int x, int z,
                                       MutableObject<NoiseColumn> column, Predicate<BlockState> stop) {
        ColumnCellFill.mark(chunk, (NoiseBasedChunkGenerator) (Object) this, random, x, z);
        return chunk;
    }
}
