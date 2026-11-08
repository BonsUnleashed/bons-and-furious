package bons.furious.mixin.shouldersurfing_c49;

import bons.furious.patch.shouldersurfing_c49.PatternMemo;
import com.github.exopandora.shouldersurfing.api.util.Util;
import java.util.regex.Pattern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * shouldersurfing_item_pattern_memo (Shoulder Surfing Reloaded 1.21.1-5.3.0; MIT; client side): the
 * Pattern.compile(expression) call inside api.util.Util.expressionToMatchPredicate(String) is answered by PatternMemo
 * (the Pattern kept for that expression, or the exception its compile threw, rethrown into the method's own catch).
 * asMatchPredicate() still runs on every call, so every caller still gets a new predicate.
 */
@Mixin(value = Util.class, remap = false)
public abstract class UtilPatternMemoMixin {
    @Redirect(method = "expressionToMatchPredicate(Ljava/lang/String;)Ljava/util/function/Predicate;",
            at = @At(value = "INVOKE", target = "Ljava/util/regex/Pattern;compile(Ljava/lang/String;)Ljava/util/regex/Pattern;"),
            require = 1, allow = 1)
    private static Pattern bons$shouldersurfingC49UtilCompile(String expression) {
        return PatternMemo.compile(expression);
    }
}
