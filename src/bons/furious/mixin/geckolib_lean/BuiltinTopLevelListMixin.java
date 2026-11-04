package bons.furious.mixin.geckolib_lean;

import bons.furious.patch.geckolib_lean.GeckoBoneMemo;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.loading.object.BakedModelFactory;

/**
 * geckolib_bone_lookup_memo (GeckoLib 4.8.4, client; since 1.0.49; exact variant): the built-in model factory
 * (BakedModelFactory$Builtin.constructGeoModel) creates the baked model's top-level bone list as a CountingBoneList. Models
 * from other factories keep their lists and GeckoLib's search (GeckoBoneMemo proves only counting trees). GeckoLib is MIT
 * licensed; no GeckoLib code is carried.
 */
@Mixin(value = BakedModelFactory.Builtin.class, remap = false)
public abstract class BuiltinTopLevelListMixin {
    @Redirect(method = "constructGeoModel", at = @At(value = "NEW", target = "it/unimi/dsi/fastutil/objects/ObjectArrayList", ordinal = 0))
    private static ObjectArrayList<GeoBone> bons$countedTopLevel() {
        return GeckoBoneMemo.newList();
    }
}
