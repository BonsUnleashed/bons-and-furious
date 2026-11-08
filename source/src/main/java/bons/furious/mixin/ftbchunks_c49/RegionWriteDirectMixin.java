package bons.furious.mixin.ftbchunks_c49;

import bons.furious.patch.ftbchunks_c49.RegionWriteDirect;
import dev.ftb.mods.ftbchunks.client.map.MapRegionData;
import java.awt.image.BufferedImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * ftbchunks_region_write_direct (FTB Chunks 2101.1.23, All Rights Reserved; client): the five BufferedImage.setRGB calls of
 * MapRegionData.write()'s map-thread task (the lambda lambda$write$0 FTB Chunks hands to its map executor; in bytecode
 * order: data, foliage, grass, water, blocks image) go to one handler each, which stores the pixel into that image's int
 * array exactly as setRGB would (see RegionWriteDirect for the storage rule and the layout check). The array copies on the
 * calling thread, the lock, the chunk list and the PNG encoding are untouched.
 */
@Mixin(value = MapRegionData.class, remap = false)
public abstract class RegionWriteDirectMixin {
    @Redirect(method = "lambda$write$0([S[S[I[I[ILjava/util/Collection;)V", at = @At(value = "INVOKE", target = "Ljava/awt/image/BufferedImage;setRGB(III)V", ordinal = 0), require = 1, allow = 1)
    private void bons$ftbchunksC49WriteData(BufferedImage image, int x, int y, int rgb) {
        RegionWriteDirect.data(image, x, y, rgb);
    }

    @Redirect(method = "lambda$write$0([S[S[I[I[ILjava/util/Collection;)V", at = @At(value = "INVOKE", target = "Ljava/awt/image/BufferedImage;setRGB(III)V", ordinal = 1), require = 1, allow = 1)
    private void bons$ftbchunksC49WriteFoliage(BufferedImage image, int x, int y, int rgb) {
        RegionWriteDirect.foliage(image, x, y, rgb);
    }

    @Redirect(method = "lambda$write$0([S[S[I[I[ILjava/util/Collection;)V", at = @At(value = "INVOKE", target = "Ljava/awt/image/BufferedImage;setRGB(III)V", ordinal = 2), require = 1, allow = 1)
    private void bons$ftbchunksC49WriteGrass(BufferedImage image, int x, int y, int rgb) {
        RegionWriteDirect.grass(image, x, y, rgb);
    }

    @Redirect(method = "lambda$write$0([S[S[I[I[ILjava/util/Collection;)V", at = @At(value = "INVOKE", target = "Ljava/awt/image/BufferedImage;setRGB(III)V", ordinal = 3), require = 1, allow = 1)
    private void bons$ftbchunksC49WriteWater(BufferedImage image, int x, int y, int rgb) {
        RegionWriteDirect.water(image, x, y, rgb);
    }

    @Redirect(method = "lambda$write$0([S[S[I[I[ILjava/util/Collection;)V", at = @At(value = "INVOKE", target = "Ljava/awt/image/BufferedImage;setRGB(III)V", ordinal = 4), require = 1, allow = 1)
    private void bons$ftbchunksC49WriteBlocks(BufferedImage image, int x, int y, int rgb) {
        RegionWriteDirect.blocks(image, x, y, rgb);
    }
}
