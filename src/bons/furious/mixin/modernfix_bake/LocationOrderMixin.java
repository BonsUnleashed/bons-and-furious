package bons.furious.mixin.modernfix_bake;

import bons.furious.patch.modernfix_bake.BakeLocations;
import bons.furious.patch.modernfix_bake.LocationOrderSet;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import org.embeddedt.modernfix.forge.dynresources.ModelBakeEventHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * modernfix_bake_location_order (ModernFix 5.27.77+mc1.20.1, LGPL-3.0; tested build modernfix-forge-5.27.77+mc1.20.1;
 * client only).
 *
 * ModelBakeEventHelper.&lt;init&gt; (ModernFix's emulated ModelEvent.ModifyBakingResult, once per resource reload) stores
 * {@code new ObjectLinkedOpenHashSet(blockStates + items)} in its topLevelModelLocations field, fills it with every
 * top-level model location (~2.4 million here) and hands it to every mod's handler through read-only views. With the
 * switch on, that one expression creates a {@link LocationOrderSet} with the same expected size: the same fastutil set
 * (class hierarchy, adds, lookups, size, hashCode all its own) that also records its insertion order in an array, so the
 * handlers that walk all of it (keySet, entrySet, replaceAll, Sets.filter) iterate sequential memory instead of following
 * the link chain. Same elements, same objects, same order (LocationOrderSet explains why). Off, or when the start-up
 * self-test of the library fails: ModernFix's own expression.
 */
@Mixin(value = ModelBakeEventHelper.class, remap = false)
public abstract class LocationOrderMixin {
    @WrapOperation(method = "<init>", at = @At(value = "NEW", target = "(I)Lit/unimi/dsi/fastutil/objects/ObjectLinkedOpenHashSet;"))
    private ObjectLinkedOpenHashSet<?> bons$orderedLocations(int expected, Operation<ObjectLinkedOpenHashSet<?>> original) {
        if (BakeLocations.active()) return new LocationOrderSet(expected);
        return original.call(expected);
    }
}
