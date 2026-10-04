package bons.furious.mixin.distanthorizons;

import bons.furious.patch.distanthorizons.BiomeBlendMemo;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.seibel.distanthorizons.common.wrappers.block.AbstractDhTintGetter_neoforge;
import com.seibel.distanthorizons.common.wrappers.block.BiomeWrapper_neoforge;
import net.minecraft.world.level.ColorResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * distanthorizons_biome_blend_memo (Distant Horizons 3.3.3 for Minecraft 1.21.1 / NeoForge, client): the per-sample
 * tryGetClientBiomeColor call in the blending loop of AbstractDhTintGetter_neoforge.tryGetBlockTint (the second of its
 * three calls) reuses the previous sample's colour while the biome wrapper object is the same, through two locals shared by
 * this one call (see BiomeBlendMemo for why it is the same colour). A @Redirect so the int colour is not boxed. No DH code
 * is carried.
 *
 * Ported to 1.21.1: AbstractDhTintGetter_forge / BiomeWrapper_forge are the _neoforge classes; tryGetBlockTint is
 * byte-identical in DH 3.3.3 (same INVOKE ordinal 1).
 */
@Mixin(value = AbstractDhTintGetter_neoforge.class, remap = false)
public abstract class BiomeBlendMemoMixin {
    @Shadow
    private int tryGetClientBiomeColor(ColorResolver colorResolver, BiomeWrapper_neoforge biomeWrapper) {
        throw new AssertionError();
    }

    @Redirect(method = "tryGetBlockTint(Lcom/seibel/distanthorizons/core/pos/blockPos/DhBlockPosMutable;Lnet/minecraft/world/level/ColorResolver;)I",
            at = @At(value = "INVOKE", ordinal = 1,
                    target = "Lcom/seibel/distanthorizons/common/wrappers/block/AbstractDhTintGetter_neoforge;tryGetClientBiomeColor(Lnet/minecraft/world/level/ColorResolver;Lcom/seibel/distanthorizons/common/wrappers/block/BiomeWrapper_neoforge;)I"))
    private int bons$sampleColor(AbstractDhTintGetter_neoforge self, ColorResolver resolver, BiomeWrapper_neoforge biome,
                                 @Share("bons$blendBiome") LocalRef<BiomeWrapper_neoforge> lastBiome, @Share("bons$blendColor") LocalIntRef lastColor) {
        if (BiomeBlendMemo.enabled && biome != null && biome == lastBiome.get()) {
            int color = lastColor.get();
            if (BiomeBlendMemo.SHADOW) BiomeBlendMemo.shadow(color, this.tryGetClientBiomeColor(resolver, biome));
            return color;
        }
        int color = this.tryGetClientBiomeColor(resolver, biome);
        if (BiomeBlendMemo.enabled && color != -1) {
            BiomeBlendMemo.announce();
            lastBiome.set(biome);
            lastColor.set(color);
        }
        return color;
    }
}
