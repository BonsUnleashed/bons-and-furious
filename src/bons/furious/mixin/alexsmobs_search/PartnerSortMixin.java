package bons.furious.mixin.alexsmobs_search;

import bons.furious.patch.alexsmobs_search.PartnerScan;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Comparator;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * alexsmobs_partner_min_scan (Alex's Mobs 1.22.9, LGPL-3.0, both sides; tested build 1.22.9).
 *
 * In the canUse (m_8036_) of the pupfish chase goal, the triops breed goal and the catfish food goal, the
 * `list.sort(Comparator.comparingDouble(mob::distanceToSqr))` before `list.get(0)` becomes PartnerScan.sortHead: the same
 * first element (the stable sort's head) found in one pass with the same comparator. Nothing of Alex's Mobs' code is carried.
 */
@Mixin(targets = {
        "com.github.alexthe666.alexsmobs.entity.EntityDevilsHolePupfish$ChaseGoal",
        "com.github.alexthe666.alexsmobs.entity.EntityTriops$BreedGoal",
        "com.github.alexthe666.alexsmobs.entity.EntityCatfish$TargetFoodGoal"}, remap = false)
public abstract class PartnerSortMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "m_8036_()Z", at = @At(value = "INVOKE", target = "Ljava/util/List;sort(Ljava/util/Comparator;)V"))
    private void bons$nearestOnly(List list, Comparator cmp, Operation<Void> original) {
        PartnerScan.sortHead(list, cmp, original);
    }
}
