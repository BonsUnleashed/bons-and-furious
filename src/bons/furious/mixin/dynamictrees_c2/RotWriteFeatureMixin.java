package bons.furious.mixin.dynamictrees_c2;

import bons.furious.patch.dynamictrees_c2.RotCycleGuard;
import com.ferreusveritas.dynamictrees.systems.genfeature.MushroomRotGenFeature;
import com.ferreusveritas.dynamictrees.systems.genfeature.RotSoilGenFeature;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * dynamictrees_rot_cycle_guard (Dynamic Trees 1.20.1-1.4.11, both sides): the one write of each of Dynamic Trees' two
 * POST_ROT features (MushroomRotGenFeature places a mushroom on the rotted branch, RotSoilGenFeature turns rooty soil below
 * it to dirt). A successful write is counted for RotCycleGuard; the value is passed through unchanged. MIT target, no
 * Dynamic Trees code carried.
 */
@Mixin(value = {MushroomRotGenFeature.class, RotSoilGenFeature.class}, remap = false)
public abstract class RotWriteFeatureMixin {
    @ModifyExpressionValue(method = "postRot", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/LevelAccessor;m_7731_(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean bons$rotFeatureWrite(boolean success) {
        return RotCycleGuard.wrote(success);
    }
}
