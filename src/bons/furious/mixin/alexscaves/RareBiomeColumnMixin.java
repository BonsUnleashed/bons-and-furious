package bons.furious.mixin.alexscaves;

import bons.furious.patch.alexscaves.RareBiomeColumns;
import com.github.alexmodguy.alexscaves.AlexsCaves;
import com.github.alexmodguy.alexscaves.server.level.biome.ACBiomeRarity;
import com.github.alexmodguy.alexscaves.server.misc.VoronoiGenerator;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * alexscaves_rare_biome_column_memo (Alex's Caves 2.0.2, LGPL; server side of world generation): getRareBiomeInfoForQuad
 * goes through RareBiomeColumns, a per-thread table of the answers for (seed, quart x, quart z) (see RareBiomeColumns for
 * why a remembered answer is the original's answer); init, which sets the values the method reads through statics, bumps
 * the table's generation when it returns. No Alex's Caves code is carried.
 */
@Mixin(value = ACBiomeRarity.class, remap = false)
public abstract class RareBiomeColumnMixin {
    @WrapMethod(method = "getRareBiomeInfoForQuad")
    private static VoronoiGenerator.VoronoiInfo bons$columnMemo(long worldSeed, int x, int z, Operation<VoronoiGenerator.VoronoiInfo> original) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        Operation<Object> op = (Operation) original;
        return (VoronoiGenerator.VoronoiInfo) RareBiomeColumns.info(worldSeed, x, z, AlexsCaves.COMMON_CONFIG.caveBiomeWidthRandomness.get(), op);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private static void bons$newGeneration(CallbackInfo ci) {
        RareBiomeColumns.generation++;
    }
}
