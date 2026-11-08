package bons.furious.mixin.oculus_passes;

import bons.furious.patch.oculus_passes.ShadowSkips;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.resources.model.Material;
import net.minecraft.world.item.DyeColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * oculus_shadow_skip_banner_patterns (Minecraft 1.21.1 BannerRenderer, client; Iris 1.8.12+mc1.21.1 for NeoForge; `change`,
 * off by default). BannerRenderer.renderPatterns (the ten-argument method; banners, banner items and shields, the
 * nine-argument overload delegates to it) first renders the flag or shield plate with the base material (entitySolid),
 * then the dyed base layer (renderPatternLayer with Sheets.BANNER_BASE / SHIELD_BASE and the base colour), then for each
 * pattern (up to 16, `i < 16 && i < patterns.layers().size()`) the same model part again with the pattern's material and
 * colour; every layer is an entityNoOutline draw, colour only, no depth. While Iris renders its shadow map, a MixinExtras
 * @WrapWithCondition leaves out the dyed base layer (the first renderPatternLayer call) and a @ModifyExpressionValue on the
 * List.size() of the loop condition answers 0, so only the base render is drawn into the shadow map, as on 1.20.1, where the
 * dyed base was the first entry of the same loop. Outside the shadow pass both answer the stock values. Iris's own
 * MixinBannerRenderer (a HEAD @ModifyVariable that groups and tags the layers, ended at RETURN) and Entity Model Features'
 * HEAD counter (MixinBannerRenderer.emf$injected) run as before; nothing is cancelled. Difference (trade-off): pattern
 * layers no longer tint coloured shadows; the depth shape in the shadow map is the base render's either way. Priority 1500,
 * require = 0: a mod that replaces the method (an @Overwrite of default priority) keeps it, and this switch finds no call to
 * change there. Carries no Minecraft code.
 */
@Mixin(value = BannerRenderer.class, priority = 1500, remap = false)
public abstract class BannerPatternShadowMixin {
    @WrapWithCondition(method = "renderPatterns(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/resources/model/Material;ZLnet/minecraft/world/item/DyeColor;Lnet/minecraft/world/level/block/entity/BannerPatternLayers;Z)V",
            require = 0, at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/client/renderer/blockentity/BannerRenderer;renderPatternLayer(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/resources/model/Material;Lnet/minecraft/world/item/DyeColor;)V"))
    private static boolean bons$shadowBaseLayer(PoseStack pose, MultiBufferSource buffers, int light, int overlay, ModelPart part, Material material,
                                                DyeColor color) {
        return ShadowSkips.drawBaseLayer();
    }

    @ModifyExpressionValue(method = "renderPatterns(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/resources/model/Material;ZLnet/minecraft/world/item/DyeColor;Lnet/minecraft/world/level/block/entity/BannerPatternLayers;Z)V",
            require = 0, at = @At(value = "INVOKE", target = "Ljava/util/List;size()I"))
    private static int bons$shadowPatternCount(int size) {
        return ShadowSkips.patternCount(size);
    }
}
