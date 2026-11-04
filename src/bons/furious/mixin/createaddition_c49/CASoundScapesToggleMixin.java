package bons.furious.mixin.createaddition_c49;

import bons.furious.patch.createaddition_c49.CaSoundFixes;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mrh0.createaddition.sound.CASoundScapes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * createaddition_ambient_sound_toggle_fix (Create Crafts &amp; Additions 1.20.1-1.3.3, MIT; client side; off by default):
 * the Boolean.booleanValue() of Create's enableAmbientSounds read in CASoundScapes.tick() (the prune's "disable") is
 * inverted, so the prune reads the setting as Create's own SoundScapes does (CaSoundFixes).
 */
@Mixin(value = CASoundScapes.class, remap = false)
public abstract class CASoundScapesToggleMixin {
    @ModifyExpressionValue(method = "tick()V", at = @At(value = "INVOKE", target = "Ljava/lang/Boolean;booleanValue()Z"), require = 1, allow = 1)
    private static boolean bons$caC49Disable(boolean enableAmbientSounds) {
        return CaSoundFixes.disable(enableAmbientSounds);
    }
}
