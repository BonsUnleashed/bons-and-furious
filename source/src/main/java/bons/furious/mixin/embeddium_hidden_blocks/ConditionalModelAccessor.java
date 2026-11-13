package bons.furious.mixin.embeddium_hidden_blocks;

import net.minecraft.client.resources.model.BakedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * fusion_hidden_block_models: the model of one conditional entry of a Fusion block-model modifier
 * (BlockModelModifierBakedModel.ConditionalModel.model).
 */
@Mixin(targets = "com.supermartijn642.fusion.model.modifiers.block.BlockModelModifierBakedModel$ConditionalModel", remap = false)
public interface ConditionalModelAccessor {
    @Accessor(value = "model", remap = false)
    BakedModel bons$model();
}
