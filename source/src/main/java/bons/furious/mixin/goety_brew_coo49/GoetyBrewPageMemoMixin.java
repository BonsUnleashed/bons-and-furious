package bons.furious.mixin.goety_brew_coo49;

import bons.furious.patch.goety_brew_coo49.GoetyBrews;
import com.Polarice3.Goety.common.effects.brew.BrewEffects;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * goety_brew_effects_page_memo (Goety 3.2.2, MIT: no code carried; client; exact variant). Goety's two brewing page
 * processors (Patchouli) build a new BrewEffects table in setup and again in process("input") (the sacrifice page twice
 * there: for its sacrifice and, when it has none, for its catalyst); each new table fills itself from the brew config and
 * the registries on its first query. MixinExtras @WrapOperation on those `new BrewEffects()` hands out one kept, filled
 * table while the brew config, the registries' order and the goety:villagers tag are unchanged (GoetyBrews.table), else
 * builds, fills and keeps a new one. Everything else in the pages runs as before.
 */
@Mixin(targets = {"com.Polarice3.Goety.compat.patchouli.BrewingCatalystProcessor",
        "com.Polarice3.Goety.compat.patchouli.BrewingSacrificeProcessor"}, remap = false)
public abstract class GoetyBrewPageMemoMixin {
    @WrapOperation(method = "setup(Lnet/minecraft/world/level/Level;Lvazkii/patchouli/api/IVariableProvider;)V",
            at = @At(value = "NEW", target = "com/Polarice3/Goety/common/effects/brew/BrewEffects"),
            require = 1, allow = 1)
    private BrewEffects bons$goetyBrewSetupTable(Operation<BrewEffects> original) {
        return GoetyBrews.table(original);
    }

    @WrapOperation(method = "process(Lnet/minecraft/world/level/Level;Ljava/lang/String;)Lvazkii/patchouli/api/IVariable;",
            at = @At(value = "NEW", target = "com/Polarice3/Goety/common/effects/brew/BrewEffects"),
            require = 1, allow = 2)
    private BrewEffects bons$goetyBrewProcessTable(Operation<BrewEffects> original) {
        return GoetyBrews.table(original);
    }
}
