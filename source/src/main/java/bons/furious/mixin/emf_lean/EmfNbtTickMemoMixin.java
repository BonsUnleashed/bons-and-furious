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
 * emf_nbt_tick_memo (tested build: Entity Model Features 3.3.9 for NeoForge 1.21.1, entity_model_features-3.3.9-1.21-neoforge.jar,
 * with Entity Texture Features 7.2.4; client only). Trade-off switch, off by default.
 *
 * MixinExtras @WrapOperation of the NBTProperty.testEntity(ETFEntityRenderState, boolean) call in NBTMethod's compiled
 * path (nbtMethodStatic) and in its interpreted path (the supplier lambda of the constructor, lambda$new$0): EmfNbtTickMemo
 * answers a repeat within the same client tick from the first result of that tick, or calls the original. Both methods are
 * static; the entity state comes from EMFState.state(). require = 0: a method another mod already changed keeps EMF's own
 * call.
 *
 * Ported to 1.21.1: EMF 3.3.9's NBTMethod has the same two call sites as the 3.3.10 / 3.3.12 Forge builds.
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
