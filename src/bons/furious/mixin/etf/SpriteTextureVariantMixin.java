package bons.furious.mixin.etf;

import bons.furious.patch.etf.SpriteTextureIds;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.Function;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SpriteCoordinateExpander;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import traben.entity_texture_features.features.ETFManager;
import traben.entity_texture_features.features.ETFRenderContext;
import traben.entity_texture_features.features.texture_handlers.ETFTexture;

/**
 * etf_sprite_texture_id_memo (Entity Texture Features 7.1, client).
 *
 * ETF's MixinSpriteIdentifier (LGPL-3.0) hooks Material.buffer, which chests, signs, beds, banners, shields, bells and
 * decorated pots use every frame, and derived the sprite's texture id with a string build and a newly validated
 * ResourceLocation on every call. While this switch applies, MixinSquared cancels ETF's mixin (patches/etf.json) and
 * this handler takes its place: ETF's code statement for statement, with the texture id taken from SpriteTextureIds,
 * which computes it once per sprite name. The same variant lookup, render-type swap and buffer result follow.
 */
@Mixin(value = Material.class, remap = false)
public abstract class SpriteTextureVariantMixin {
    @Inject(method = "m_119194_", at = @At("RETURN"), cancellable = true)
    private void bons$modifyIfRequired(MultiBufferSource vertexConsumers, Function<ResourceLocation, RenderType> layerFactory,
                                       CallbackInfoReturnable<VertexConsumer> cir) {
        if (cir.getReturnValue() instanceof SpriteCoordinateExpander expander) {
            ResourceLocation rawId = ((SpriteCoordinateExpanderAccessor) expander).bons$sprite().m_245424_().m_246162_();
            ResourceLocation actualTexture = SpriteTextureIds.of(rawId);
            ETFTexture texture = ETFManager.getInstance().getETFTextureVariant(actualTexture, ETFRenderContext.getCurrentEntityState());
            if (!actualTexture.equals(texture.thisIdentifier) || texture.isEmissive() || texture.isEnchanted()) {
                ETFRenderContext.preventRenderLayerTextureModify();
                RenderType layer = layerFactory.apply(texture.thisIdentifier);
                ETFRenderContext.allowRenderLayerTextureModify();
                if (layer != null) {
                    VertexConsumer consumer = vertexConsumers.m_6299_(layer);
                    if (consumer != null) {
                        cir.setReturnValue(consumer);
                    }
                }
            }
        }
    }
}
