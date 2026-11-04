package bons.furious.mixin.immersiveweathering_spread_c49;

import bons.furious.patch.immersiveweathering_spread_c49.LeanSpread;
import com.ordana.immersive_weathering.blocks.cracked.CrackSpreader;
import com.ordana.immersive_weathering.blocks.cracked.Crackable;
import com.ordana.immersive_weathering.util.PatchSpreader;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

/**
 * immersiveweathering_patch_spread_lean (Immersive Weathering 1.20.1-2.0.5, LGPL-3.0; server side): gives CrackSpreader its
 * own getWantedWeatheringState(boolean, BlockPos, Level, int) in place of the PatchSpreader interface default it inherits.
 * Exactly CrackSpreader with the switch on takes LeanSpread's path; everything else calls the interface default, unchanged.
 */
@Mixin(value = CrackSpreader.class, remap = false)
public abstract class CrackSpreaderLeanMixin implements PatchSpreader<Crackable.CrackLevel> {
    @Override
    public boolean getWantedWeatheringState(boolean hasTicked, BlockPos pos, Level level, int maxRecursion) {
        if (LeanSpread.enabled && LeanSpread.plain(this)) return LeanSpread.wanted(this, hasTicked, pos, level, maxRecursion);
        return PatchSpreader.super.getWantedWeatheringState(hasTicked, pos, level, maxRecursion);
    }
}
