package bons.furious.mixin.sliceanddice_c2;

import bons.furious.patch.sliceanddice_c2.WetAirGate;
import com.bawnorton.mixinsquared.TargetHandler;
import com.possible_triangle.sliceanddice.block.sprinkler.WetAir;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * sliceanddice_wet_air_gate (Slice & Dice 3.6.0, both sides; the gate answers on the server thread), part 1 of 3.
 *
 * Slice & Dice's LevelMixin.isRainingAt handler (merged into Level, HEAD of isRainingAt) calls WetAir.check(level, pos),
 * which reads the blocks above pos through Level.getBlockState. This redirect returns false without those reads when
 * WetAirGate proves from the loaded chunk's sections that none of them can be wet air; otherwise it calls WetAir.check
 * exactly as before. See WetAirGate for the proof and the one documented internal difference (chunk-ticket bookkeeping).
 */
@Mixin(value = Level.class, priority = 1500, remap = false)
public abstract class WetAirRainCheckMixin {
    @TargetHandler(mixin = "com.possible_triangle.sliceanddice.mixins.LevelMixin", name = "isRainingAt")
    @Redirect(method = "@MixinSquared:Handler", at = @At(value = "INVOKE",
            target = "Lcom/possible_triangle/sliceanddice/block/sprinkler/WetAir;check(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean bons$gatedWetAirCheck(Level level, BlockPos pos) {
        if (WetAirGate.rainCheckHidden(level, pos)) {
            if (WetAirGate.SHADOW) WetAirGate.shadowRain(level, pos);
            return false;
        }
        return WetAir.check(level, pos);
    }
}
