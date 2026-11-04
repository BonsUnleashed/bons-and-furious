package bons.furious.mixin.mahoutsukai_coo49;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * mahoutsukai_projector_enchant_precheck (Mahou Tsukai's EffectUtil, client): a probe mixin. Priority 3000, so Mixin applies
 * it after every other mod's mixin on the class; MahouPlugin.postApply then checks that hasEnchantment / getEnchantmentLevel
 * are as shipped (MahouProjectorProbe). It only adds one empty private static method (so an offline apply keeps it).
 */
@Mixin(targets = "stepsword.mahoutsukai.util.EffectUtil", priority = 3000, remap = false)
public abstract class MahouEffectUtilProbeMixin {
    @Unique
    private static void bons$mahoutsukaiCoo49ProbeEffectUtil() {
    }
}
