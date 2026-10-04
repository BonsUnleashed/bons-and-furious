package bons.furious.mixin.distanthorizons;

import bons.furious.patch.distanthorizons.LodBiomeMemo;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.seibel.distanthorizons.core.dataObjects.transformers.LodDataBuilder;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * distanthorizons_lod_biome_memo (Distant Horizons 3.3.2, both sides): both chunkWrapper.getBiome calls of
 * LodDataBuilder.createFromChunk (a column's top and the per-block loop) go through LodBiomeMemo with a local shared by
 * this one call, which hands back the previous answer for the same column and quart (see LodBiomeMemo for why it is the
 * same answer). A @Redirect rather than a @WrapOperation: the miss path then calls getBiome with its three ints directly,
 * where an Operation would box them into an argument array on every miss. DH is LGPL; no DH code is carried.
 */
@Mixin(value = LodDataBuilder.class, remap = false)
public abstract class LodBiomeMemoMixin {
    @Redirect(method = "createFromChunk", at = @At(value = "INVOKE",
            target = "Lcom/seibel/distanthorizons/core/wrapperInterfaces/chunk/IChunkWrapper;getBiome(III)Lcom/seibel/distanthorizons/core/wrapperInterfaces/world/IBiomeWrapper;"))
    private static IBiomeWrapper bons$quartBiome(IChunkWrapper chunk, int x, int y, int z, @Share("bons$lodBiome") LocalRef<LodBiomeMemo.Memo> memo) {
        return LodBiomeMemo.biome(chunk, x, y, z, memo);
    }
}
