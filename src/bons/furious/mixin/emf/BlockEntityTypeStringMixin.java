package bons.furious.mixin.emf;

import bons.furious.patch.emf.TypeStrings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import traben.entity_model_features.models.animation.state.EMFEntityRenderStateViaReference;
import traben.entity_model_features.utils.EMFEntity;

/**
 * emf_block_entity_type_string (Entity Model Features 3.2.4, client).
 *
 * EMF looks up the variation roots of every rendered block entity by a type string, which for block entities it
 * rebuilt every frame as BlockEntityType.toString() (Object.toString). The string is now built once per type (see
 * TypeStrings) and the same equal string is returned; entities and any unusual block entity keep EMF's own method.
 */
@Mixin(value = EMFEntityRenderStateViaReference.class, remap = false)
public abstract class BlockEntityTypeStringMixin {
    @Redirect(method = "typeString", at = @At(value = "INVOKE", target = "Ltraben/entity_model_features/utils/EMFEntity;emf$getTypeString()Ljava/lang/String;"))
    private String bons$typeString(EMFEntity entity) {
        return TypeStrings.of(entity);
    }
}
