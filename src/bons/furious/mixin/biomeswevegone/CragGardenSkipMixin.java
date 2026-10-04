package bons.furious.mixin.biomeswevegone;

import bons.furious.patch.biomeswevegone.ForeignChunkTerrain;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.potionstudios.biomeswevegone.world.level.levelgen.biome.BWGBiomes;
import net.potionstudios.biomeswevegone.world.level.levelgen.customterrain.CragGardenExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * biomeswevegone_foreign_chunk_skip (BWG 1.8.0, All Rights Reserved: our own check only): the Crag Gardens pass returns at
 * once when ForeignChunkTerrain proves that no column of the chunk can be in Crag Gardens (see there).
 */
@Mixin(value = CragGardenExtension.class, remap = false)
public abstract class CragGardenSkipMixin {
    @Inject(method = "runCragGardenExtension", at = @At("HEAD"), cancellable = true)
    private static void bons$skipForeign(Function<BlockPos, Holder<Biome>> biomeGetter, ChunkAccess chunk, long seed,
                                         NormalNoise.NoiseParameters noise, NormalNoise.NoiseParameters cliffs, CallbackInfo ci) {
        if (ForeignChunkTerrain.skip(ForeignChunkTerrain.regionFor(chunk), chunk, BWGBiomes.CRAG_GARDENS)) {
            ci.cancel();
        }
    }
}
