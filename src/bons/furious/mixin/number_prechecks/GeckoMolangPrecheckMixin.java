package bons.furious.mixin.number_prechecks;

import bons.furious.patch.number_prechecks.MolangNumberPrecheck;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import software.bernie.geckolib.core.molang.MolangParser;

/**
 * molang_number_precheck (GeckoLib 4.8.4, client only, since 1.0.45): MixinExtras @WrapOperation on the one
 * Double.parseDouble call in MolangParser.parseJson. A keyframe string that cannot be a double literal throws a stackless
 * NumberFormatException into GeckoLib's own catch block (which never reads it and parses the expression) instead of the
 * parser's full-stack one; every other string is parsed as before. Why the result is identical: see MolangNumberPrecheck
 * and FloatGrammar. require = 0. GeckoLib is MIT; no GeckoLib code is carried.
 */
@Mixin(value = MolangParser.class, remap = false)
public abstract class GeckoMolangPrecheckMixin {
    @WrapOperation(method = "parseJson", require = 0,
            at = @At(value = "INVOKE", target = "Ljava/lang/Double;parseDouble(Ljava/lang/String;)D"))
    private static double bons$quietKeyframe(String value, Operation<Double> original) {
        return MolangNumberPrecheck.parseDouble(value, original);
    }
}
