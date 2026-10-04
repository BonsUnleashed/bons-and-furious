package bons.furious.mixin.biomeswevegone;

import bons.furious.patch.biomeswevegone.ForeignChunkTerrain;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.potionstudios.biomeswevegone.world.level.levelgen.biome.BWGBiomes;
import net.potionstudios.biomeswevegone.world.level.levelgen.customterrain.BasaltBarreraExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * biomeswevegone_foreign_chunk_skip (BWG 1.8.0, All Rights Reserved: our own check only): the Basalt Barrera pass returns
 * at once when ForeignChunkTerrain proves that no column of the chunk can be in Basalt Barrera (see there).
 */
@Mixin(value = BasaltBarreraExtension.class, remap = false)
public abstract class BasaltBarreraSkipMixin {
    @Inject(method = "runBasaltBarreraExtension", at = @At("HEAD"), cancellable = true)
    private static void bons$skipForeign(ChunkAccess chunk, WorldGenRegion region, ChunkGenerator generator, CallbackInfo ci) {
        if (ForeignChunkTerrain.skip(region, chunk, BWGBiomes.BASALT_BARRERA)) {
            ci.cancel();
        }
    }
}
