package bons.furious.mixin.distanthorizons_tint;

import bons.furious.patch.distanthorizons_tint.TintColumns;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.seibel.distanthorizons.common.wrappers.block.AbstractDhTintGetter_neoforge;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * distanthorizons_tint_neighbour_index (Distant Horizons 3.3.3 for Minecraft 1.21.1 / NeoForge, tested build
 * DistantHorizons-3.3.3-1.21.1-fabric-neoforge.jar, LGPL-3.0; client): the neighbour lookup
 * FullDataSourceV2.getDataPointAtBlockPos(IIII)J in the blending loop of
 * AbstractDhTintGetter_neoforge.tryGetBlockTint(DhBlockPosMutable, ColorResolver) (its only call there) goes through
 * TintColumns with a Memo shared by this one call (see TintColumns for why the answer is DH's). A @Redirect so the four
 * ints are not boxed. Composes with distanthorizons_biome_blend_memo's @Redirect of tryGetClientBiomeColor (ordinal 1) in
 * the same loop. No DH code is carried.
 *
 * Ported to 1.21.1: the Forge wrapper AbstractDhTintGetter_forge is AbstractDhTintGetter_neoforge in DH 3.3.3's combined
 * jar; its private tryGetBlockTint(DhBlockPosMutable, ColorResolver) has the same descriptor and the same code (one
 * getDataPointAtBlockPos call, the same loop and arguments), and every DH core class the helper uses is byte-identical.
 */
@Mixin(value = AbstractDhTintGetter_neoforge.class, remap = false)
public abstract class TintColumnsMixin {
    @Redirect(method = "tryGetBlockTint(Lcom/seibel/distanthorizons/core/pos/blockPos/DhBlockPosMutable;Lnet/minecraft/world/level/ColorResolver;)I",
            at = @At(value = "INVOKE",
                    target = "Lcom/seibel/distanthorizons/core/dataObjects/fullData/sources/FullDataSourceV2;getDataPointAtBlockPos(IIII)J"))
    private long bons$neighbourPoint(FullDataSourceV2 source, int x, int y, int z, int levelMinY,
                                     @Share("bons$tintColumns") LocalRef<TintColumns.Memo> memo) {
        return TintColumns.dataPoint(source, x, y, z, levelMinY, memo);
    }
}
