package bons.furious.mixin.emf_lean;

import bons.furious.patch.emf_lean.EmfNbtTickMemo;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import traben.entity_model_features.models.animation.math.methods.emf.NBTMethod;
import traben.entity_texture_features.features.property_reading.properties.optifine_properties.NBTProperty;
import traben.entity_texture_features.features.state.ETFEntityRenderState;

/**
 * emf_nbt_tick_memo (Entity Model Features 3.2.4, also 3.3.10 / 3.3.12; client only). Trade-off switch, off by default.
 * Since 1.0.49.
 *
 * MixinExtras @WrapOperation of the NBTProperty.testEntity(ETFEntityRenderState, boolean) call in NBTMethod's compiled
 * path (nbtMethodStatic) and in its interpreted path (the supplier lambda of the constructor, lambda$new$0): EmfNbtTickMemo
 * answers a repeat within the same client tick from the first result of that tick, or calls the original. Both methods are
 * static and have the same call in EMF 3.2.x and 3.3.x (the entity state comes from EMFAnimationEntityContext in 3.2.x and
 * from EMFState in 3.3.x; the call itself is unchanged). require = 0: a method another mod already changed keeps EMF's
 * own call. A wrap, not a redirect: Collections Of Optimizations 5.8 (emf.cacheNbtPerTick) hooks nbtMethodStatic too.
 */
@Mixin(value = NBTMethod.class, remap = false)
public abstract class EmfNbtTickMemoMixin {
    @WrapOperation(method = {"nbtMethodStatic", "lambda$new$0"}, require = 0, expect = 0, remap = false, at = @At(value = "INVOKE",
            target = "Ltraben/entity_texture_features/features/property_reading/properties/optifine_properties/NBTProperty;testEntity(Ltraben/entity_texture_features/features/state/ETFEntityRenderState;Z)Z",
            remap = false))
    private static boolean bons$nbtTickMemo(NBTProperty property, ETFEntityRenderState state, boolean update, Operation<Boolean> original) {
        return EmfNbtTickMemo.test(property, state, update, original);
    }
}
