package bons.furious.mixin.emf_render;

import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import traben.entity_model_features.models.animation.EMFAttachments;
import traben.entity_model_features.models.parts.EMFModelPartCustom;

/**
 * emf_arm_walk_gate (EMF 3.2.4, client only): read access to EMFModelPartCustom.attachments (null when the part has no
 * left/right_handheld_item point; set once in its constructor).
 */
@Mixin(value = EMFModelPartCustom.class, remap = false)
public interface EmfPartAttachmentsAccessor {
    @Accessor(value = "attachments", remap = false)
    List<EMFAttachments> bons$attachments();
}
