package bons.furious.mixin.vanilla_bone_memo_c49;

import bons.furious.patch.vanilla_bone_memo_c49.BoneMemo;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * vanilla_model_bone_lookup_memo (Minecraft 1.21.1, client), part 2 of 2: read access to ModelPart's child map (children),
 * for the memo's check of a kept bone and its walk. Nothing about ModelPart changes.
 */
@Mixin(value = ModelPart.class, remap = false)
public interface ModelPartChildrenMemoMixin extends BoneMemo.Children {
    @Override
    @Accessor("children")
    Map<String, ModelPart> bons$vanilla_bone_memo_c49Children();
}
