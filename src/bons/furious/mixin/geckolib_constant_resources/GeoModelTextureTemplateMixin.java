package bons.furious.mixin.geckolib_constant_resources;

import bons.furious.patch.geckolib_constant_resources.ConstantResources;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;

/**
 * geckolib_texture_template_memo (GeckoLib 4.8.4, MIT; client; since 1.0.49).
 *
 * GeoModel.getTextureResource(animatable, renderer), which GeckoLib's renderers call at least once per entity per frame, makes
 * one virtual call of the model's own 1-argument texture getter; that call is wrapped (MixinExtras @WrapOperation; the
 * constant-location wrapper of geckolib_constant_resource_reuse on the same call, and any other mod's, still chains). For a
 * model class whose texture getter ConstantResources has proven, from its bytecode, to be MCreator's template
 * new ResourceLocation("ns", "textures/entities/" + entity.getTexture() + ".png"), and an animatable whose getTexture() is the
 * plain synched-data read, the location kept for an equal texture string is handed out again; anything else makes the
 * original call. See ConstantResources. No GeckoLib code is carried.
 */
@Mixin(value = GeoModel.class, remap = false)
public abstract class GeoModelTextureTemplateMixin {
    @WrapOperation(method = "getTextureResource(Lsoftware/bernie/geckolib/core/animatable/GeoAnimatable;Lsoftware/bernie/geckolib/renderer/GeoRenderer;)Lnet/minecraft/resources/ResourceLocation;",
            at = @At(value = "INVOKE", target = "Lsoftware/bernie/geckolib/model/GeoModel;getTextureResource(Lsoftware/bernie/geckolib/core/animatable/GeoAnimatable;)Lnet/minecraft/resources/ResourceLocation;"),
            require = 1, allow = 1)
    private ResourceLocation bons$textureTemplate(GeoModel<?> model, GeoAnimatable animatable, Operation<ResourceLocation> original) {
        return ConstantResources.texture(model, animatable, original);
    }
}
