package bons.furious.mixin.embeddium_meshing;

import bons.furious.patch.embeddium_meshing.VisibleFacesFirst;
import com.supermartijn642.fusion.model.modifiers.block.ModelsByRandomOffset;
import java.util.List;
import net.minecraft.client.resources.model.BakedModel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * embeddium_visible_faces_first (Fusion 1.3.14+a, client): read access to a random-offset entry's sub-model list, so the
 * allowlist check can see which models the entry combines. Adds one read-only interface method; changes nothing else.
 * Fusion is All Rights Reserved: no Fusion code is carried.
 */
@Mixin(value = ModelsByRandomOffset.Entry.class, remap = false)
public abstract class EntryModelsMixin implements VisibleFacesFirst.EntryModels {
    @Shadow
    @Final
    List<BakedModel> models;

    @Override
    public List<BakedModel> bons$models() {
        return this.models;
    }
}
