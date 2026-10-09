package bons.furious.mixin.terrain;

import bons.furious.patch.terrain.ColumnSummary;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.OptionalInt;
import java.util.function.Predicate;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Mixin;

/**
 * vanilla_noise_column_summary (Minecraft 1.20.1 world generation, tested build 1.20.1 SRG; both sides).
 *
 * NoiseBasedChunkGenerator.iterateNoiseColumn (m_224239_) is the column walk behind getBaseHeight and getBaseColumn. This
 * @WrapMethod records what each walk saw into the column's summary (bons.furious.patch.terrain.ColumnSummary): a
 * getBaseHeight walk gets a recording predicate that answers exactly as its original stopping predicate (so the walk, its
 * states and its result are unchanged), a getBaseColumn walk runs unchanged and its returned column is summarized. The
 * summary answers later getBaseHeight queries of the same column with another heightmap type in vanilla_noise_column_cache's
 * lookup; ColumnSummary documents why those answers are the walk's own. When the switch is off, the generator has no
 * vanilla_noise_column_cache table, or the predicate is not a heightmap type's, the original call runs as is.
 */
@Mixin(value = NoiseBasedChunkGenerator.class, remap = false)
public abstract class ColumnSummaryMixin {
    @WrapMethod(method = "m_224239_")
    private OptionalInt bons$columnSummary(LevelHeightAccessor level, RandomState random, int x, int z, MutableObject<NoiseColumn> column,
                                           Predicate<BlockState> stop, Operation<OptionalInt> original) {
        ColumnSummary.Walk walk = ColumnSummary.begin((NoiseBasedChunkGenerator) (Object) this, level, random, x, z, column, stop);
        if (walk == null) return original.call(level, random, x, z, column, stop);
        OptionalInt result = original.call(level, random, x, z, column, walk.predicate());
        walk.end(result, column);
        return result;
    }
}
