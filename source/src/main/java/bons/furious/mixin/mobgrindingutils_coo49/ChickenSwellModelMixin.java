package bons.furious.mixin.mobgrindingutils_coo49;

import bons.furious.patch.mobgrindingutils_coo49.ChickenSwellModels;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mob_grinding_utils.events.RenderChickenSwell;
import mob_grinding_utils.models.ChickenBodyModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * mobgrindingutils_chicken_swell_model_reuse (Mob Grinding Utils 1.1.10+mc1.21.1, client side; no Mob Grinding Utils code is
 * carried). RenderChickenSwell.renderChickenSwell builds the swelling chicken body (layer definition, baked part tree,
 * model, texture location) for every fed chicken on every frame; the four wrapped calls hand back the ones made the first
 * time instead. Nothing moves a part of that model, so the vertices are the same. See ChickenSwellModels.
 */
@Mixin(value = RenderChickenSwell.class, remap = false)
public abstract class ChickenSwellModelMixin {
    @WrapOperation(method = "renderChickenSwell(Lnet/neoforged/neoforge/client/event/RenderLivingEvent$Post;)V", at = @At(value = "INVOKE",
            target = "Lmob_grinding_utils/models/ChickenBodyModel;createBodyLayer()Lnet/minecraft/client/model/geom/builders/LayerDefinition;"),
            require = 1, allow = 1)
    private LayerDefinition bons$mobgrindingutilsSwellLayer(Operation<LayerDefinition> original) {
        return ChickenSwellModels.layer(original);
    }

    @WrapOperation(method = "renderChickenSwell(Lnet/neoforged/neoforge/client/event/RenderLivingEvent$Post;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/geom/builders/LayerDefinition;bakeRoot()Lnet/minecraft/client/model/geom/ModelPart;"),
            require = 1, allow = 1)
    private ModelPart bons$mobgrindingutilsSwellPart(LayerDefinition definition, Operation<ModelPart> original) {
        return ChickenSwellModels.part(definition, original);
    }

    @WrapOperation(method = "renderChickenSwell(Lnet/neoforged/neoforge/client/event/RenderLivingEvent$Post;)V", at = @At(value = "NEW",
            target = "(Lnet/minecraft/client/model/geom/ModelPart;)Lmob_grinding_utils/models/ChickenBodyModel;"),
            require = 1, allow = 1)
    private ChickenBodyModel bons$mobgrindingutilsSwellModel(ModelPart root, Operation<ChickenBodyModel> original) {
        return ChickenSwellModels.model(root, original);
    }

    @WrapOperation(method = "renderChickenSwell(Lnet/neoforged/neoforge/client/event/RenderLivingEvent$Post;)V", at = @At(value = "INVOKE",
            target = "Lmob_grinding_utils/util/RL;mc(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;"),
            require = 1, allow = 1)
    private ResourceLocation bons$mobgrindingutilsSwellTexture(String path, Operation<ResourceLocation> original) {
        return ChickenSwellModels.texture(path, original);
    }
}
