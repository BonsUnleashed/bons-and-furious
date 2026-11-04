package bons.furious.mixin.geckolib_lean;

import bons.furious.patch.geckolib_lean.GeckoBoneMemo;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Optional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * geckolib_bone_lookup_memo (GeckoLib 4.8.4, client; since 1.0.49; exact variant): MixinExtras @WrapMethod around
 * BakedGeoModel.getBone(String). The model's memo (GeckoBoneMemo.Memo, a new field) is built again whenever the global
 * bone-tree version moved; a model the memo cannot prove runs GeckoLib's search unchanged. GeckoLib is MIT licensed; no
 * GeckoLib code is carried.
 */
@Mixin(value = BakedGeoModel.class, remap = false)
public abstract class BakedGeoModelBoneMemoMixin {
    @Unique
    private volatile GeckoBoneMemo.Memo bons$boneMemo;

    @WrapMethod(method = "getBone")
    private Optional<GeoBone> bons$memoBone(String name, Operation<Optional<GeoBone>> original) {
        if (!GeckoBoneMemo.enabled) return original.call(name);
        long version = GeckoBoneMemo.version();
        GeckoBoneMemo.Memo memo = this.bons$boneMemo;
        if (memo == null || memo.version() != version) {
            memo = GeckoBoneMemo.build((BakedGeoModel) (Object) this, version);
            this.bons$boneMemo = memo;
        }
        return GeckoBoneMemo.getBone((BakedGeoModel) (Object) this, memo, name, original);
    }
}
