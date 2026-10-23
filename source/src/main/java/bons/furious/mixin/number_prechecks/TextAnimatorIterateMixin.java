package bons.furious.mixin.number_prechecks;

import bons.furious.patch.number_prechecks.TextAnimatorParsePrecheck;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import snownee.textanimator.effect.Effect;
import snownee.textanimator.util.CommonProxy;

/**
 * textanimator_parse_prechecks (Text Animator 2.5.4, both sides, since 1.0.45): MixinExtras @WrapOperation on the
 * Effect.create(String[], boolean) call in CommonProxy.iterateFormatted, the per-frame text path. Tags Text Animator
 * rejects (unregistered effect type, disallowed typewriter) throw a stackless IllegalArgumentException into the method's
 * own catch (Exception) block, which discards it, instead of running create to its throw; a registered tag with a
 * non-numeric parameter value is built by the pre-checked copy of create. Why the result is identical: see
 * TextAnimatorParsePrecheck. require = 0. Text Animator is Apache-2.0; the helper carries a modified copy of
 * Effect.create (attributed there).
 */
@Mixin(value = CommonProxy.class, remap = false)
public abstract class TextAnimatorIterateMixin {
    @WrapOperation(method = "iterateFormatted", require = 0, at = @At(value = "INVOKE",
            target = "Lsnownee/textanimator/effect/Effect;create([Ljava/lang/String;Z)Lsnownee/textanimator/effect/Effect;"))
    private static Effect bons$quietTag(String[] split, boolean allowTypewriter, Operation<Effect> original) {
        return TextAnimatorParsePrecheck.create(split, allowTypewriter, original);
    }
}
