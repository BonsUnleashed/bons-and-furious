package bons.furious.mixin.emf_render;

import bons.furious.patch.emf_render.EmfRender;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import traben.entity_model_features.models.parts.EMFModelPartCustom;

/**
 * emf_arm_walk_gate (Entity Model Features 3.2.4 + Embeddium 0.3.31, Minecraft 1.20.1 / Forge 47.4.16, client only).
 *
 * EMFModelPartCustom.render (m_104306_) first calls processArmItemOverrides(matrices). A custom part with no hand
 * attachments of its own runs EMFModelPart's walk there: pushPose, translateAndRotate, the same walk for every child (a
 * capturing lambda per call), popPose. Every custom child then repeats it for its own subtree when it renders, in the main
 * pass, the shadow pass and ETF's emissive/enchant re-renders, so the cost is subtree size times depth per entity per
 * pass (a 64-mob profiling scene: the walk 3.2 % inclusive of the render thread, the stack under rotateZYX's 4.1 %, and a
 * large share of pushPose's 47.8 % of render-thread allocation).
 *
 * This redirects that one call: when EmfRender.walkIsInert proves for this call that the walk can do nothing but balanced
 * push/pop pairs (plain PoseStack with Embeddium's pose cache off, a plain custom part without attachments, every part
 * below it one of EMF's own classes without attachments, every children map present), the call is skipped; otherwise the
 * original call runs. Why identical: such a walk ends with the stack exactly as it began (the same Pose objects, the same
 * matrices; each pushed Pose is new and is dropped by its pop), sets no attachment and no arm override, and throws nothing.
 */
@Mixin(value = EMFModelPartCustom.class, remap = false)
public abstract class EmfArmWalkGateMixin {
    @Redirect(method = "m_104306_", at = @At(value = "INVOKE",
            target = "Ltraben/entity_model_features/models/parts/EMFModelPartCustom;processArmItemOverrides(Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
    private void bons$armWalk(EMFModelPartCustom self, PoseStack matrices) {
        if (EmfRender.armWalkGate && EmfRender.walkIsInert(self, matrices)) {
            if (EmfRender.SHADOW_WALK) {
                EmfRender.shadowWalk(self, matrices);
                return;
            }
            EmfRender.announceWalk();
            return;
        }
        self.processArmItemOverrides(matrices);
    }
}
