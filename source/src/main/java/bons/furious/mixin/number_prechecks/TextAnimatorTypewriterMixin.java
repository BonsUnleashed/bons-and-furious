package bons.furious.mixin.number_prechecks;

import bons.furious.patch.number_prechecks.TextAnimatorParsePrecheck;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import snownee.textanimator.effect.Effect;
import snownee.textanimator.typewriter.TypewriterEffect;

/**
 * textanimator_typewriter_prechecks (Text Animator 3.2.3 for NeoForge 1.21.1, both sides, since 1.0.49; change, off by
 * default: the check costs well-formed typewriter tags time): @Redirect of the Effect.create(String, boolean) call in
 * TypewriterEffect.find, which every literal text visit reaches for a text starting with "<typewriter" (Text Animator's
 * client LiteralContentsMixin / TranslatableContentsMixin; find returns null first while TextAnimatorApi suspends parsing).
 * A well-formed typewriter tag goes to Effect.create as shipped (no allocation on the way); a tag Text Animator rejects
 * throws a stackless IllegalArgumentException into find's own catch (Exception) block, which discards it (find returns
 * null, as before); a registered tag with a non-numeric parameter value is built by the pre-checked copy of create. Why
 * the result is identical: see TextAnimatorParsePrecheck.createTag. A @Redirect rather than a wrapper: no operation object
 * per call. require = 0. Text Animator is Apache-2.0; the helper carries a modified copy of Effect.create (attributed there).
 */
@Mixin(value = TypewriterEffect.class, remap = false)
public abstract class TextAnimatorTypewriterMixin {
    @Redirect(method = "find", require = 0, at = @At(value = "INVOKE",
            target = "Lsnownee/textanimator/effect/Effect;create(Ljava/lang/String;Z)Lsnownee/textanimator/effect/Effect;"))
    private static Effect bons$numberPrechecksQuietTypewriterTag(String tagContent, boolean allowTypewriter) {
        return TextAnimatorParsePrecheck.createTag(tagContent, allowTypewriter);
    }
}
