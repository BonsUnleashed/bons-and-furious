package bons.furious.mixin.cataclysm_client;

import bons.furious.patch.cataclysm_client.ShieldLayerPose;
import com.github.L_Ender.cataclysm.client.render.layer.Ignis_Shield_Layer;
import com.github.L_Ender.cataclysm.client.model.entity.Ignis_Model;
import com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.Ignis_Entity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * cataclysm_shield_layer_pose (Ignis_Shield_Layer, L_Ender's Cataclysm, CC-BY-NC-ND-4.0: no Cataclysm code is carried; 1.21.1
 * tested build L_Ender's Cataclysm 1.21.1-3.33; client).
 *
 * The layer copies the renderer model's properties into its own Ignis_Model and runs its full setupAnim with the renderer's
 * arguments. When the renderer's model has just finished setupAnim for this entity with the same arguments, the layer
 * model gets that pose copied instead (ShieldLayerPose.copied); otherwise, and in shadow mode, setupAnim runs as shipped.
 *
 * Ported to 1.21.1: Ignis_Shield_Layer.render in 3.33 keeps its descriptor and its copyPropertiesTo / setupAnim /
 * renderToBuffer order (renderToBuffer now takes the packed colour); the wrapped call is unchanged.
 */
@Mixin(value = Ignis_Shield_Layer.class, remap = false)
public abstract class IgnisShieldLayerPoseMixin {
    @WrapOperation(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILcom/github/L_Ender/cataclysm/entity/AnimationMonster/BossMonsters/Ignis_Entity;FFFFFF)V", at = @At(value = "INVOKE", target = "Lcom/github/L_Ender/cataclysm/client/model/entity/Ignis_Model;setupAnim(Lcom/github/L_Ender/cataclysm/entity/AnimationMonster/BossMonsters/Ignis_Entity;FFFFF)V"))
    private void bons$reuseParentPose(Ignis_Model layer, Ignis_Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
            float headPitch, Operation<Void> original) {
        Ignis_Model parent = (Ignis_Model) ((RenderLayer<?, ?>) (Object) this).getParentModel();
        boolean shadow = ShieldLayerPose.shadowWanted(parent, entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        if (ShieldLayerPose.copied(parent, layer, entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch)) return;
        original.call(layer, entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        if (shadow) ShieldLayerPose.shadowCompare(parent, layer, entity);
    }
}
