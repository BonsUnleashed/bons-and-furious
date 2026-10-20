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
 * geckolib_constant_resource_reuse (GeckoLib 4.8.4, MIT; client).
 *
 * GeoModel's 2-argument entry points, which GeckoLib's renderers call (getModelResource(animatable, renderer) and
 * getTextureResource(animatable, renderer) every frame, getAnimation(animatable, name) when an animation is looked up), each
 * make one virtual call of the model's own 1-argument getter. Each of those three calls is wrapped (MixinExtras
 * @WrapOperation; another mod's wrapper on the same call still chains): when ConstantResources has proven, from the getter's
 * bytecode, that the model class answers a constant new ResourceLocation("ns", "path"), the location from the first call is
 * handed out again; otherwise, and for an argument of another type than the model's (so the bridge's ClassCastException is
 * unchanged), the original call is made. See ConstantResources. No GeckoLib code is carried.
 */
@Mixin(value = GeoModel.class, remap = false)
public abstract class GeoModelConstantResourceMixin {
    @WrapOperation(method = "getModelResource(Lsoftware/bernie/geckolib/core/animatable/GeoAnimatable;Lsoftware/bernie/geckolib/renderer/GeoRenderer;)Lnet/minecraft/resources/ResourceLocation;",
            at = @At(value = "INVOKE", target = "Lsoftware/bernie/geckolib/model/GeoModel;getModelResource(Lsoftware/bernie/geckolib/core/animatable/GeoAnimatable;)Lnet/minecraft/resources/ResourceLocation;"),
            require = 1, allow = 1)
    private ResourceLocation bons$modelResource(GeoModel<?> model, GeoAnimatable animatable, Operation<ResourceLocation> original) {
        return ConstantResources.get(model, animatable, ConstantResources.MODEL, original);
    }

    @WrapOperation(method = "getTextureResource(Lsoftware/bernie/geckolib/core/animatable/GeoAnimatable;Lsoftware/bernie/geckolib/renderer/GeoRenderer;)Lnet/minecraft/resources/ResourceLocation;",
            at = @At(value = "INVOKE", target = "Lsoftware/bernie/geckolib/model/GeoModel;getTextureResource(Lsoftware/bernie/geckolib/core/animatable/GeoAnimatable;)Lnet/minecraft/resources/ResourceLocation;"),
            require = 1, allow = 1)
    private ResourceLocation bons$textureResource(GeoModel<?> model, GeoAnimatable animatable, Operation<ResourceLocation> original) {
        return ConstantResources.get(model, animatable, ConstantResources.TEXTURE, original);
    }

    @WrapOperation(method = "getAnimation(Lsoftware/bernie/geckolib/core/animatable/GeoAnimatable;Ljava/lang/String;)Lsoftware/bernie/geckolib/core/animation/Animation;",
            at = @At(value = "INVOKE", target = "Lsoftware/bernie/geckolib/model/GeoModel;getAnimationResource(Lsoftware/bernie/geckolib/core/animatable/GeoAnimatable;)Lnet/minecraft/resources/ResourceLocation;"),
            require = 1, allow = 1)
    private ResourceLocation bons$animationResource(GeoModel<?> model, GeoAnimatable animatable, Operation<ResourceLocation> original) {
        return ConstantResources.get(model, animatable, ConstantResources.ANIMATION, original);
    }
}
