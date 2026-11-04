package bons.furious.mixin.shouldersurfing_c49;

import bons.furious.patch.shouldersurfing_c49.PatternMemo;
import java.util.regex.Pattern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * shouldersurfing_item_pattern_memo, Shoulder Surfing Reloaded 5.0.11 (accepted alternative build; MIT; client side):
 * the same redirect as UtilPatternMemoMixin, in 5.0.11's private ComputePlayerAimStateEventHandlerImpl
 * .expressionToMatchPredicate(String) (byte for byte the method 5.2.0 moved to api.util.Util). Offered to Mixin only when
 * the switch's guard matched the 5.0.11 build (ShouldersurfingPlugin), so Mixin never looks for it in 5.2.0.
 */
@Mixin(targets = "com.github.exopandora.shouldersurfing.client.event.handler.ComputePlayerAimStateEventHandlerImpl", remap = false)
public abstract class AimStatePatternMemoMixin {
    @Redirect(method = "expressionToMatchPredicate(Ljava/lang/String;)Ljava/util/function/Predicate;",
            at = @At(value = "INVOKE", target = "Ljava/util/regex/Pattern;compile(Ljava/lang/String;)Ljava/util/regex/Pattern;"),
            require = 1, allow = 1)
    private static Pattern bons$shouldersurfingC49AimCompile(String expression) {
        return PatternMemo.compile(expression);
    }
}
