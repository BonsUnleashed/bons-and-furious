package bons.furious.mixin.emf_render;

import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * emf_arm_walk_gate (EMF 3.2.4 on Minecraft 1.20.1, client only): read access to ModelPart.children (f_104213_), the map
 * EMF's walk iterates (EMF parts replace it with their own HashMap).
 */
@Mixin(value = ModelPart.class, remap = false)
public interface EmfModelPartChildrenAccessor {
    @Accessor(value = "f_104213_", remap = false)
    Map<String, ModelPart> bons$emfChildren();
}
