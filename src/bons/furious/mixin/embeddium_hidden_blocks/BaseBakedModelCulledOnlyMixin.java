package bons.furious.mixin.embeddium_hidden_blocks;

import bons.furious.patch.embeddium_hidden_blocks.HiddenBlocks;
import com.supermartijn642.fusion.model.types.base.BaseBakedModel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * fusion_hidden_block_models (Fusion 1.3.14+a, client): Fusion's base model (plain and connecting textures) answers
 * "face quads only" when it is exactly BaseBakedModel and its quad table holds no quad for the null side. getQuads
 * builds its answer for a side from that side's list alone (processQuads(quads.get(side), ...)), and the table is
 * fixed when the model is baked.
 */
@Mixin(value = BaseBakedModel.class, remap = false)
public abstract class BaseBakedModelCulledOnlyMixin implements HiddenBlocks.CulledOnly {
    @Shadow @Final private BaseBakedModel.Quads quads;

    @Override
    public boolean bons$culledOnly() {
        return (Object) this.getClass() == BaseBakedModel.class && ((QuadsGetInvoker) (Object) this.quads).bons$get(null).isEmpty();
    }
}
