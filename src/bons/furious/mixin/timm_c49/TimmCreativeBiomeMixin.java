package bons.furious.mixin.timm_c49;

import bons.furious.patch.timm_c49.TimmCreativePlaylist;
import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.Music;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * timm_creative_biome_playlist (The Immersive Music Mod 1.1.0, client, since 1.0.49; kind change, gameplay fix, off by
 * default). MixinSquared @TargetHandler + MixinExtras @WrapOperation on the BiomePlaylist.getCreativeMusic call inside TIMM's
 * MinecraftMixin.updateCreativeMusic handler (merged into Minecraft.getSituationalMusic, m_91107_): the current biome's
 * playlist answers first, TIMM's creative playlist when the biome has none (see TimmCreativePlaylist). Priority 1500:
 * applied after TIMM's mixin. require = 0 / expect = 0. TIMM is GPL-3.0; no TIMM code is carried.
 */
@Mixin(value = Minecraft.class, priority = 1500, remap = false)
public abstract class TimmCreativeBiomeMixin {
    @TargetHandler(mixin = "com.github.charlyb01.timm.mixin.MinecraftMixin", name = "updateCreativeMusic")
    @WrapOperation(method = "@MixinSquared:Handler", require = 0, expect = 0, allow = 1,
            at = @At(value = "INVOKE", target = "Lcom/github/charlyb01/timm/music/BiomePlaylist;getCreativeMusic(Lnet/minecraft/util/RandomSource;)Lnet/minecraft/sounds/Music;"))
    private Music bons$timmC49CreativeBiome(RandomSource random, Operation<Music> original) {
        return TimmCreativePlaylist.creativeMusic((Minecraft) (Object) this, random, original);
    }
}
