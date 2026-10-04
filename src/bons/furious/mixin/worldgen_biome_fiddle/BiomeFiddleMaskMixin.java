package bons.furious.mixin.worldgen_biome_fiddle;

import bons.furious.patch.worldgen_biome_fiddle.BiomeFiddleMask;
import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * vanilla_biome_fiddle_mask (Minecraft 1.20.1, both sides; Forge 47.4.16): BiomeManager.getFiddle's Math.floorMod(JI)I
 * goes through BiomeFiddleMask.floorMod, a bit mask for the divisor 1024 (identical for every long; see there). Only the
 * JDK call is redirected; nothing of Minecraft's code is carried.
 */
@Mixin(value = BiomeManager.class, remap = false)
public abstract class BiomeFiddleMaskMixin {
    @Redirect(method = "m_186689_", at = @At(value = "INVOKE", target = "Ljava/lang/Math;floorMod(JI)I"))
    private static int bons$fiddleMask(long x, int y) {
        return BiomeFiddleMask.floorMod(x, y);
    }
}
