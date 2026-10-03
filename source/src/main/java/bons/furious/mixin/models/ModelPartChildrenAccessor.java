package bons.furious.mixin.models;

import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * vanilla_model_bone_lookup (Minecraft 1.20.1 client models).
 *
 * BoneLookupMixin walks a model's parts in the order ModelPart.getAllParts would, which needs each part's children map.
 */
@Mixin(value = ModelPart.class, remap = false)
public interface ModelPartChildrenAccessor {
    @Accessor("children")
    Map<String, ModelPart> bons$children();
}
