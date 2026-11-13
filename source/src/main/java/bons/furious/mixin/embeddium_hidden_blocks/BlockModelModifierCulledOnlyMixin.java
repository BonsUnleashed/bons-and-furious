package bons.furious.mixin.embeddium_hidden_blocks;

import bons.furious.patch.embeddium_hidden_blocks.FusionGroups;
import bons.furious.patch.embeddium_hidden_blocks.HiddenBlocks;
import com.supermartijn642.fusion.model.modifiers.block.BlockModelModifierBakedModel;
import java.util.List;
import net.minecraft.client.resources.model.BakedModel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * fusion_hidden_block_models (Fusion 1.3.15b+mc1.21.1, client): a Fusion block-model modifier draws its original model or one
 * default override, plus any of its append models, chosen per block from the neighbours (collectByOffset hands that
 * choice to Fusion's Embeddium renderer, which draws each group through the normal renderModel). It answers "face quads
 * only" when it is exactly BlockModelModifierBakedModel and every model it could choose does (the original, every
 * default override's model, every append list's models): then no choice can produce a quad for the null side. Asked
 * anew every time, so the answer follows its parts. FusionGroups.FACE_LOOP: the groups Fusion's renderer makes are
 * drawn by Embeddium's own face loop. Ported to 1.21.1: the three fields, collectByOffset and Fusion's Embeddium
 * renderer are unchanged where this reads them.
 */
@Mixin(value = BlockModelModifierBakedModel.class, remap = false)
public abstract class BlockModelModifierCulledOnlyMixin implements HiddenBlocks.CulledOnly {
    @Shadow @Final private BakedModel original;
    @Shadow @Final private List<?> defaultModelOverrides;
    @Shadow @Final private List<? extends List<?>> appendModels;

    @Override
    public boolean bons$culledOnly() {
        if ((Object) this.getClass() != BlockModelModifierBakedModel.class || !FusionGroups.FACE_LOOP || !HiddenBlocks.culledOnly(this.original)) return false;
        List<?> overrides = this.defaultModelOverrides;
        for (int i = 0, n = overrides.size(); i < n; i++) {
            if (!HiddenBlocks.culledOnly(((ConditionalModelAccessor) overrides.get(i)).bons$model())) return false;
        }
        List<? extends List<?>> appends = this.appendModels;
        for (int i = 0, n = appends.size(); i < n; i++) {
            List<?> list = appends.get(i);
            for (int j = 0, m = list.size(); j < m; j++) {
                if (!HiddenBlocks.culledOnly(((ConditionalModelAccessor) list.get(j)).bons$model())) return false;
            }
        }
        return true;
    }
}
