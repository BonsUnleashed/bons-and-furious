package bons.furious.mixin.dynamictrees_c2;

import bons.furious.patch.dynamictrees_c2.RotCycleGuard;
import com.dtteam.dynamictrees.systems.genfeature.MushroomRotGenFeature;
import com.dtteam.dynamictrees.systems.genfeature.RotSoilGenFeature;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * dynamictrees_rot_cycle_guard (Dynamic Trees, MIT; 1.21.1 tested build: dynamictrees-neoforge-1.21.1-1.7.2; both sides):
 * the one write of each of Dynamic Trees' two POST_ROT features (MushroomRotGenFeature places a mushroom on the rotted
 * branch, RotSoilGenFeature turns rooty soil below it to dirt). A successful write is counted for RotCycleGuard; the value
 * is passed through unchanged. MIT target, no Dynamic Trees code carried.
 * Ported to 1.21.1: same single LevelAccessor.setBlock in both postRot bodies (MushroomRot's placement test is now
 * isSolidRender of the state there instead of Forge's canSustainPlant: still a read).
 */
@Mixin(value = {MushroomRotGenFeature.class, RotSoilGenFeature.class}, remap = false)
public abstract class RotWriteFeatureMixin {
    @ModifyExpressionValue(method = "postRot", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/LevelAccessor;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean bons$rotFeatureWrite(boolean success) {
        return RotCycleGuard.wrote(success);
    }
}
