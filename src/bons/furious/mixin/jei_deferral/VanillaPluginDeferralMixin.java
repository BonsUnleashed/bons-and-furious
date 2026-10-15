package bons.furious.mixin.jei_deferral;

import bons.furious.patch.jei_deferral.Deferral;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.library.plugins.vanilla.VanillaPlugin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * jei_join_recipe_deferral (JEI 15.59.0.212): marks the window in which JEI's own vanilla plugin builds its anvil and
 * grindstone recipes; no other caller is deferred. Helper: {@link Deferral}.
 */
@Mixin(value = VanillaPlugin.class, remap = false)
public abstract class VanillaPluginDeferralMixin {
    @Inject(method = "registerRecipes", at = @At("HEAD"), remap = false)
    private void bons$armDeferral(IRecipeRegistration registration, CallbackInfo ci) {
        Deferral.arm();
    }

    @Inject(method = "registerRecipes", at = @At("RETURN"), remap = false)
    private void bons$disarmDeferral(IRecipeRegistration registration, CallbackInfo ci) {
        Deferral.disarm();
    }
}
