package bons.furious.mixin.cataclysm_client_c49;

import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * cataclysm_pose_reset_loop (client; since 1.0.49): a model part's children (read only). Accessor only. Its name carries
 * the group (the models group adds its own bons$children accessor to ModelPart; two accessors of one name and descriptor
 * on one target are a duplicate member).
 */
@Mixin(value = ModelPart.class, remap = false)
public interface ModelPartChildrenAccessor {
    @Accessor(value = "children", remap = false)
    Map<String, ModelPart> bons$cataclysmChildren();
}
