package bons.furious.mixin.oculus_passes;

import bons.furious.patch.oculus_passes.ShadowSkips;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * oculus_shadow_skip_name_tags (Minecraft 1.21.1 on NeoForge 21.1.252 EntityRenderer, client; Iris 1.8.12+mc1.21.1 for
 * NeoForge; `change`, off by default). EntityRenderer.renderNameTag (renderNameTag(Entity, Component, PoseStack,
 * MultiBufferSource, int, float)) draws an entity's name tag as two text draws (the see-through pass and the normal one)
 * when it is within the name-tag distance (ClientHooks.isNameplateInRenderDistance). A cancellable @Inject at its HEAD
 * returns at once while Iris renders its shadow map (ShadowSkips.NAME_TAGS), so name tags no longer cast letter-shaped
 * shadows. The RenderNameTagEvent that EntityRenderer.render posts before calling it still fires as before, renderers that
 * override the method (PlayerRenderer's score line) still run their own code and only their super calls return early, and
 * the other injections inside the method (Iris's name-tag entity id, set at Entity.getViewYRot and restored at RETURN only
 * when it was set; Pehkui's name-tag offset; Subtle Effects' sleeping translate) are skipped with it and leave nothing
 * behind. Priority 1500: lands in a replacement of the method by another mod's @Overwrite of default priority instead of
 * stopping the game. Carries no Minecraft code.
 */
@Mixin(value = EntityRenderer.class, priority = 1500, remap = false)
public abstract class NameTagShadowMixin {
    @Inject(method = "renderNameTag(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void bons$shadowNameTag(Entity entity, Component name, PoseStack pose, MultiBufferSource buffers, int light, float partialTick,
                                    CallbackInfo ci) {
        if (ShadowSkips.NAME_TAGS.skip()) ci.cancel();
    }
}
