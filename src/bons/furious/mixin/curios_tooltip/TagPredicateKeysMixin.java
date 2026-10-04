package bons.furious.mixin.curios_tooltip;

import bons.furious.patch.curios_tooltip.CuriosTagKeys;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.mixin.CuriosImplMixinHooks;

/**
 * curios_tag_predicate_keys (Curios API 5.14.1+1.20.1, LGPL-3.0-or-later; tested build curios-forge-5.14.1+1.20.1; both
 * sides).
 *
 * Wraps Curios' built-in "curios:tag" slot validator (the synthetic CuriosImplMixinHooks.lambda$static$9, registered in
 * CuriosImplMixinHooks.&lt;clinit&gt;; both guarded by fingerprint). With the switch on, the validator's answer comes from
 * {@link CuriosTagKeys#test}: the same stack.is(curios:&lt;slot id&gt;) || stack.is(curios:curio) with the two TagKeys kept
 * instead of building two ResourceLocations and interning two TagKeys per call (why that is identical: CuriosTagKeys).
 * Off: Curios' lambda runs unchanged. Shadow mode returns Curios' own answer and compares. CuriosImplMixinHooks sits in a
 * package named "mixin" but is an ordinary class (Curios' mixin config package is top.theillusivec4.curios.mixin.core).
 * No other method of the class is touched (the curios_thread_safe_caches switch wraps getUuid; independent).
 */
@Mixin(value = CuriosImplMixinHooks.class, remap = false)
public abstract class TagPredicateKeysMixin {
    @WrapMethod(method = "lambda$static$9(Ltop/theillusivec4/curios/api/SlotResult;)Z")
    private static boolean bons$keptTagKeys(SlotResult slotResult, Operation<Boolean> original) {
        // null result / context / stack: Curios' own body, so its NullPointerException (and message) is unchanged
        if (!CuriosTagKeys.enabled || !CuriosTagKeys.handles(slotResult)) return original.call(slotResult);
        if (CuriosTagKeys.SHADOW) return CuriosTagKeys.shadow(slotResult, sr -> original.call(sr));
        return CuriosTagKeys.test(slotResult);
    }
}
