package bons.furious.mixin.extrahnn_coo49;

import bons.furious.patch.extrahnn_coo49.StoredModelsMemo;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * extrahnn_stored_models_memo (Extra Hostile Neural Networks 1.2.2, both sides; exact): @Redirect of the one
 * new ResourceLocation(String) in ExtraDataModelItem.getStoredModels (the per-id parse of the stored model list) to the
 * location kept for that id string. The method's other code, the empty-stack and tagless paths included, is the shipped
 * code. See StoredModelsMemo for why the answers are the same. Our own logic only (MIT target; none of its code is carried).
 */
@Mixin(targets = "net.lmor.extrahnn.item.ExtraDataModelItem", remap = false)
public abstract class ExtraStoredModelsMemoMixin {
    @Redirect(method = "getStoredModels(Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;",
            at = @At(value = "NEW", target = "(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;"), require = 1, allow = 1)
    private static ResourceLocation bons$extrahnn_coo49Id(String id) {
        return StoredModelsMemo.id(id);
    }
}
