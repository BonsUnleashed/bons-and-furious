package bons.furious.mixin.curios_tooltip;

import bons.furious.patch.curios_tooltip.CuriosTagKeys;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.mixin.CuriosImplMixinHooks;

/**
 * curios_tag_predicate_keys (Curios API, LGPL-3.0-or-later; 1.21.1 tested build curios-neoforge-9.5.1+1.21.1; both
 * sides).
 *
 * Wraps Curios' built-in "curios:tag" slot validator (the synthetic CuriosImplMixinHooks.lambda$static$8, bound to
 * curios:tag by invokedynamic #9 in CuriosImplMixinHooks.&lt;clinit&gt;; both guarded by fingerprint). With the switch
 * on, the validator's answer comes from {@link CuriosTagKeys#test}: the same stack.is(curios:&lt;slot id&gt;) ||
 * stack.is(curios:curio) with the two TagKeys kept instead of building two ResourceLocations and interning two TagKeys
 * per call (why that is identical: CuriosTagKeys). Off: Curios' lambda runs unchanged. Shadow mode returns Curios' own
 * answer and compares. CuriosImplMixinHooks sits in a package named "mixin" but is an ordinary class (Curios' mixin
 * config package is top.theillusivec4.curios.mixin.core). No other method of the class is touched.
 *
 * Ported to 1.21.1: the validator is lambda$static$8 in Curios 9.5.1 (lambda$static$9 in 5.14.1; the three built-in
 * predicates all/none/tag are lambda$static$6/7/8 by their bodies and the bootstrap methods) and builds its locations with
 * ResourceLocation.fromNamespaceAndPath instead of the removed public constructor.
 */
@Mixin(value = CuriosImplMixinHooks.class, remap = false)
public abstract class TagPredicateKeysMixin {
    @WrapMethod(method = "lambda$static$8(Ltop/theillusivec4/curios/api/SlotResult;)Z")
    private static boolean bons$keptTagKeys(SlotResult slotResult, Operation<Boolean> original) {
        // null result / context / stack: Curios' own body, so its NullPointerException (and message) is unchanged
        if (!CuriosTagKeys.enabled || !CuriosTagKeys.handles(slotResult)) return original.call(slotResult);
        if (CuriosTagKeys.SHADOW) return CuriosTagKeys.shadow(slotResult, sr -> original.call(sr));
        return CuriosTagKeys.test(slotResult);
    }
}
