package bons.furious.mixin.embeddium_hidden_blocks;

import bons.furious.patch.embeddium_hidden_blocks.HiddenBlocks;
import java.util.List;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.WeightedBakedModel;
import net.minecraft.util.random.WeightedEntry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * embeddium_hidden_block_skip: a vanilla weighted model answers "face quads only" when it is exactly WeightedBakedModel
 * and every model in its list (list) does: whichever variant the block's seed picks, the answer holds. Asked anew
 * every time (a handful of list reads), so the answer follows its variants. Ported to 1.21.1: the same field; the
 * wrapper is a record there (WeightedEntry.Wrapper.data()); Mojang names.
 */
@Mixin(value = WeightedBakedModel.class, remap = false)
public abstract class WeightedBakedModelCulledOnlyMixin implements HiddenBlocks.CulledOnly {
    @Shadow @Final private List<WeightedEntry.Wrapper<BakedModel>> list;

    @Override
    public boolean bons$culledOnly() {
        if ((Object) this.getClass() != WeightedBakedModel.class) return false;
        List<WeightedEntry.Wrapper<BakedModel>> list = this.list;
        int n = list.size();
        if (n == 0) return false;
        for (int i = 0; i < n; i++) {
            if (!HiddenBlocks.culledOnly(list.get(i).data())) return false;
        }
        return true;
    }
}
