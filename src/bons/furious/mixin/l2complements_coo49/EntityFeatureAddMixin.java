package bons.furious.mixin.l2complements_coo49;

import bons.furious.patch.l2complements_coo49.AttackChecks;
import dev.xkmc.l2complements.content.feature.FeaturePredicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * l2complements_attack_checks_cheap_first (L2 Complements 2.6.1, both sides), part 3 of 3: at the RETURN of
 * EntityFeature.add (the only way L2 Complements and other mods give a feature another predicate) AttackChecks is told to
 * check the features' predicates again before its next test. Our own logic only (All Rights Reserved target; none of its
 * code is carried).
 */
@Mixin(targets = "dev.xkmc.l2complements.content.feature.EntityFeature", remap = false)
public abstract class EntityFeatureAddMixin {
    @Inject(method = "add(Ldev/xkmc/l2complements/content/feature/FeaturePredicate;)V", at = @At("RETURN"), require = 1, allow = 1)
    private void bons$l2complements_coo49Added(FeaturePredicate predicate, CallbackInfo ci) {
        AttackChecks.changed();
    }
}
