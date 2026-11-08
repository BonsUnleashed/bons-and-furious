package bons.furious.mixin.l2complements_coo49;

import bons.furious.patch.l2complements_coo49.AttackChecks;
import java.util.ArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * l2complements_attack_checks_cheap_first (L2 Complements, LGPL-2.1; 1.21.1 tested build 3.1.3; both sides), part 2 of 3:
 * an accessor for EntityFeature's private predicate list, so AttackChecks can tell whether a feature's test is a pure
 * enchantment-level read. Adds nothing else to the enum.
 */
@Mixin(targets = "dev.xkmc.l2complements.content.feature.EntityFeature", remap = false)
public interface EntityFeatureListAccess extends AttackChecks.FeatureList {
    @Override
    @Accessor("list")
    ArrayList<?> bons$l2complements_coo49Predicates();
}
