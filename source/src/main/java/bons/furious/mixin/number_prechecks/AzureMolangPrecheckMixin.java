package bons.furious.mixin.number_prechecks;

import bons.furious.patch.number_prechecks.AzureMolangNumberPrecheck;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mod.azure.azurelib.core.molang.MolangParser;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * azurelib_molang_number_precheck (AzureLib 3.0.13, client only, since 1.0.45): MixinExtras @WrapOperation on the one
 * Double.parseDouble call in AzureLib's MolangParser.parseUncached, the same change as molang_number_precheck makes in
 * GeckoLib's copy of the class. Why the result is identical: see AzureMolangNumberPrecheck and FloatGrammar. require = 0.
 * AzureLib is MIT; no AzureLib code is carried.
 */
@Mixin(value = MolangParser.class, remap = false)
public abstract class AzureMolangPrecheckMixin {
    @WrapOperation(method = "parseUncached", require = 0,
            at = @At(value = "INVOKE", target = "Ljava/lang/Double;parseDouble(Ljava/lang/String;)D"))
    private static double bons$quietKeyframe(String value, Operation<Double> original) {
        return AzureMolangNumberPrecheck.parseDouble(value, original);
    }
}
