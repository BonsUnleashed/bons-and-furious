package bons.furious.mixin.embeddium_hidden_blocks;

import bons.furious.patch.embeddium_hidden_blocks.HiddenBlocks;
import com.supermartijn642.fusion.model.EmptyBakedModel;
import org.spongepowered.asm.mixin.Mixin;

/**
 * fusion_hidden_block_models (Fusion 1.3.15b+mc1.21.1, client): Fusion's empty model has no quads at all (on 1.21.1 its
 * getQuads still returns the empty list, and it has no five-argument getQuads of its own).
 */
@Mixin(value = EmptyBakedModel.class, remap = false)
public abstract class EmptyBakedModelCulledOnlyMixin implements HiddenBlocks.CulledOnly {
    @Override
    public boolean bons$culledOnly() {
        return (Object) this.getClass() == EmptyBakedModel.class;
    }
}
