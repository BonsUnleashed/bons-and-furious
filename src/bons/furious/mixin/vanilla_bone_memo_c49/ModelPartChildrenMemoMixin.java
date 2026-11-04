package bons.furious.mixin.vanilla_bone_memo_c49;

import bons.furious.patch.vanilla_bone_memo_c49.BoneMemo;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** vanilla_model_bone_lookup_memo, part 2 of 2: ModelPart's child map (f_104213_), for the memo's check of a kept bone. */
@Mixin(value = ModelPart.class, remap = false)
public interface ModelPartChildrenMemoMixin extends BoneMemo.Children {
    @Override
    @Accessor("f_104213_")
    Map<String, ModelPart> bons$vanilla_bone_memo_c49Children();
}