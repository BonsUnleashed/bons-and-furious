package bons.furious.mixin.number_prechecks;

import bons.furious.patch.number_prechecks.EmfTokenPrecheck;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import traben.entity_model_features.models.animation.math.expression_tree.MathExpressionParser;

/**
 * emf_token_number_precheck (Entity Model Features 3.2.4, client only, since 1.0.45): MixinExtras @WrapOperation on the one
 * Float.parseFloat call in MathExpressionParser.readVariableOrConstant. A token that cannot be a float literal throws a
 * stackless NumberFormatException into EMF's own catch block (which never reads it) instead of the parser's full-stack
 * one; every other token is parsed by Float.parseFloat as before. Why the result is identical: see EmfTokenPrecheck and
 * FloatGrammar. require = 0: a mod that rewrites the method leaves the switch out. EMF is LGPL-3.0; no EMF code is carried.
 */
@Mixin(value = MathExpressionParser.class, remap = false)
public abstract class EmfTokenPrecheckMixin {
    @WrapOperation(method = "readVariableOrConstant", require = 0,
            at = @At(value = "INVOKE", target = "Ljava/lang/Float;parseFloat(Ljava/lang/String;)F"))
    private float bons$quietToken(String token, Operation<Float> original) {
        return EmfTokenPrecheck.parseFloat(token, original);
    }
}
