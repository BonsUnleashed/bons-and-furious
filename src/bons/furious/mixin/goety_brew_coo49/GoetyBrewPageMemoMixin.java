package bons.furious.mixin.goety_brew_coo49;

import bons.furious.patch.goety_brew_coo49.GoetyBrews;
import com.Polarice3.Goety.common.effects.brew.BrewEffects;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * goety_brew_effects_page_memo (Goety 2.5.60.0, MIT: no code carried; client; exact variant). Goety's two brewing page
 * processors (Patchouli) build a new BrewEffects table in setup and again in process("input"); MixinExtras @WrapOperation
 * on those two `new BrewEffects()` per class hands out one kept table while the brew config, the registries' order and the
 * goety:villagers tag are unchanged (GoetyBrews.table), else builds and keeps a new one. Everything else in the pages runs
 * as before.
 */
@Mixin(targets = {"com.Polarice3.Goety.compat.patchouli.BrewingCatalystProcessor",
        "com.Polarice3.Goety.compat.patchouli.BrewingSacrificeProcessor"}, remap = false)
public abstract class GoetyBrewPageMemoMixin {
    @WrapOperation(method = {"setup", "process"},
            at = @At(value = "NEW", target = "com/Polarice3/Goety/common/effects/brew/BrewEffects"),
            require = 2, allow = 2)
    private BrewEffects bons$goetyBrewPageTable(Operation<BrewEffects> original) {
        return GoetyBrews.table(original);
    }
}
