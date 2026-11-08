package bons.furious.mixin.l2archery_coo49;

import bons.furious.patch.l2archery_coo49.ArrowTextures;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.xkmc.l2archery.content.entity.GenericArrowEntity;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * l2archery_arrow_texture_memo (L2 Archery, LGPL-2.1; 1.21.1 tested build 3.0.4; client; exact):
 * GenericArrowRenderer.getTextureLocation answers an arrow item seen before with the location kept for it (HEAD inject,
 * cancellable) and keeps the shipped code's answer at its return. See ArrowTextures for why the answers are the same. Our
 * own logic only (none of L2 Archery's code is carried).
 */
@Mixin(targets = "dev.xkmc.l2archery.content.entity.GenericArrowRenderer", remap = false)
public abstract class ArrowTextureMemoMixin {
    @Inject(method = "getTextureLocation(Ldev/xkmc/l2archery/content/entity/GenericArrowEntity;)Lnet/minecraft/resources/ResourceLocation;",
            at = @At("HEAD"), cancellable = true)
    private void bons$l2archery_coo49TextureProbe(GenericArrowEntity entity, CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation kept = ArrowTextures.probe(entity);
        if (kept != null) cir.setReturnValue(kept);
    }

    @ModifyReturnValue(method = "getTextureLocation(Ldev/xkmc/l2archery/content/entity/GenericArrowEntity;)Lnet/minecraft/resources/ResourceLocation;",
            at = @At("RETURN"))
    private ResourceLocation bons$l2archery_coo49TextureFill(ResourceLocation result, GenericArrowEntity entity) {
        return ArrowTextures.fill(entity, result);
    }
}
