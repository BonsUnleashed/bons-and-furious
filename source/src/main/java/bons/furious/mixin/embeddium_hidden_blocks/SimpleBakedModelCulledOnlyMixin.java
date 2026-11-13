package bons.furious.mixin.embeddium_hidden_blocks;

import bons.furious.patch.embeddium_hidden_blocks.HiddenBlocks;
import java.util.List;
import net.minecraft.client.resources.model.SimpleBakedModel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * embeddium_hidden_block_skip: a vanilla simple model answers "face quads only" when it is exactly SimpleBakedModel (a
 * subclass may build its quads differently) and its unculled list (unculledFaces), the list getQuads returns for a null
 * side, is empty. The list is fixed when the model is baked. Ported to 1.21.1: the same field (Mojang name); Embeddium
 * 1.0.15 answers NeoForge's five-argument getQuads with the three-argument one (its SimpleBakedModelMixin).
 */
@Mixin(value = SimpleBakedModel.class, remap = false)
public abstract class SimpleBakedModelCulledOnlyMixin implements HiddenBlocks.CulledOnly {
    @Shadow @Final protected List<?> unculledFaces;

    @Override
    public boolean bons$culledOnly() {
        return (Object) this.getClass() == SimpleBakedModel.class && this.unculledFaces.isEmpty();
    }
}
