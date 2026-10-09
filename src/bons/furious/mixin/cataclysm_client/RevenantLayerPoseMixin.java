package bons.furious.mixin.cataclysm_client;

import bons.furious.patch.cataclysm_client.ShieldLayerPose;
import com.github.L_Ender.cataclysm.client.render.layer.Revenant_Layer;
import com.github.L_Ender.cataclysm.client.model.entity.Ignited_Revenant_Model;
import com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.Ignited_Revenant_Entity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * cataclysm_shield_layer_pose (Revenant_Layer, L_Ender's Cataclysm 3.16, CC-BY-NC-ND-4.0: no Cataclysm code is carried; client).
 *
 * The layer copies the renderer model's properties into its own Ignited_Revenant_Model and runs its full setupAnim with the renderer's
 * arguments. When the renderer's model has just finished setupAnim for this entity with the same arguments, the layer
 * model gets that pose copied instead (ShieldLayerPose.copied); otherwise, and in shadow mode, setupAnim runs as shipped.
 */
@Mixin(value = Revenant_Layer.class, remap = false)
public abstract class RevenantLayerPoseMixin {
    @WrapOperation(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILcom/github/L_Ender/cataclysm/entity/AnimationMonster/BossMonsters/Ignited_Revenant_Entity;FFFFFF)V", at = @At(value = "INVOKE", target = "Lcom/github/L_Ender/cataclysm/client/model/entity/Ignited_Revenant_Model;setupAnim(Lcom/github/L_Ender/cataclysm/entity/AnimationMonster/BossMonsters/Ignited_Revenant_Entity;FFFFF)V"))
    private void bons$reuseParentPose(Ignited_Revenant_Model layer, Ignited_Revenant_Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
            float headPitch, Operation<Void> original) {
        Ignited_Revenant_Model parent = (Ignited_Revenant_Model) ((RenderLayer<?, ?>) (Object) this).m_117386_();
        boolean shadow = ShieldLayerPose.shadowWanted(parent, entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        if (ShieldLayerPose.copied(parent, layer, entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch)) return;
        original.call(layer, entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        if (shadow) ShieldLayerPose.shadowCompare(parent, layer, entity);
    }
}
