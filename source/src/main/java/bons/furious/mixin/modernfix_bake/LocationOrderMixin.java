package bons.furious.mixin.modernfix_bake;

import bons.furious.patch.modernfix_bake.BakeLocations;
import bons.furious.patch.modernfix_bake.LocationOrderSet;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import org.embeddedt.modernfix.neoforge.dynresources.ModelBakeEventHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * modernfix_bake_location_order (ModernFix, LGPL-3.0; 1.21.1 tested build modernfix-neoforge-5.27.24+mc1.21.1;
 * client only).
 *
 * ModelBakeEventHelper.&lt;init&gt; (ModernFix's emulated ModelEvent.ModifyBakingResult and BakingCompleted, once per
 * event per resource reload, only with ModernFix's dynamic resources on) stores
 * {@code new ObjectLinkedOpenHashSet(blockStates + items)} in its topLevelModelLocations field, fills it with every
 * top-level model location and hands it to every mod's handler through read-only views. With the switch on, that one
 * expression creates a {@link LocationOrderSet} with the same expected size: the same fastutil set (class hierarchy, adds,
 * lookups, size, hashCode all its own) that also records its insertion order in an array, so the handlers that walk all
 * of it (keySet, entrySet, replaceAll, Sets.filter) iterate sequential memory instead of following the link chain. Same
 * elements, same objects, same order (LocationOrderSet explains why). Off, or when the start-up self-test of the library
 * fails: ModernFix's own expression.
 *
 * Ported to 1.21.1: the helper moved to org.embeddedt.modernfix.neoforge.dynresources with the same constructor
 * expression (the one NEW ObjectLinkedOpenHashSet(int) of &lt;init&gt;); its set now holds ModelResourceLocation records
 * instead of ResourceLocations, and ModernFix 5.27.24 builds a helper for BakingCompleted as well, so both sets are covered.
 */
@Mixin(value = ModelBakeEventHelper.class, remap = false)
public abstract class LocationOrderMixin {
    @WrapOperation(method = "<init>", at = @At(value = "NEW", target = "(I)Lit/unimi/dsi/fastutil/objects/ObjectLinkedOpenHashSet;"))
    private ObjectLinkedOpenHashSet<?> bons$orderedLocations(int expected, Operation<ObjectLinkedOpenHashSet<?>> original) {
        if (BakeLocations.active()) return new LocationOrderSet(expected);
        return original.call(expected);
    }
}
