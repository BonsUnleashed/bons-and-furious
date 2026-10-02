package bons.furious.mixin.embeddium_meshing;

import bons.furious.patch.embeddium_meshing.VisibleFacesFirst;
import java.util.List;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.WeightedBakedModel;
import net.minecraft.util.random.WeightedEntry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * embeddium_visible_faces_first (Minecraft 1.20.1 WeightedBakedModel, client): remembers whether every variant of a
 * weighted model is an allowlisted leaf (SimpleBakedModel or Fusion's BaseBakedModel), so the per-face allowlist check
 * does not walk the variant list. The list never changes after construction; the answer is computed on first use and
 * stored as a byte (0 = not yet, 1 = yes, 2 = no; a racing thread at worst computes the same answer again).
 */
@Mixin(value = WeightedBakedModel.class, remap = false)
public abstract class VariantAllowlistMixin implements VisibleFacesFirst.AllowlistedVariants {
    @Shadow
    @Final
    private List<WeightedEntry.Wrapper<BakedModel>> f_119541_;

    @Unique
    private byte bons$variantsAllowlisted;

    @Override
    public boolean bons$variantsAllowlisted() {
        byte state = this.bons$variantsAllowlisted;
        if (state == 0) {
            state = 1;
            for (WeightedEntry.Wrapper<BakedModel> w : this.f_119541_) {
                if (!VisibleFacesFirst.leaf(w.m_146310_(), false)) {
                    state = 2;
                    break;
                }
            }
            this.bons$variantsAllowlisted = state;
        }
        return state == 1;
    }
}
