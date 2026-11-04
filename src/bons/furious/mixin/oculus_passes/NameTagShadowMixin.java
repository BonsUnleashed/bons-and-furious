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
 * oculus_shadow_skip_name_tags (Minecraft 1.20.1 on Forge 47.4.28 EntityRenderer, client; Oculus 1.8.0 tested; `change`,
 * off by default). EntityRenderer.renderNameTag (m_7649_) draws an entity's name tag as two text draws (the see-through
 * pass and the normal one) when it is within the name-tag distance. A cancellable @Inject at its HEAD returns at once
 * while Oculus renders its shadow map (ShadowSkips.NAME_TAGS), so name tags no longer cast letter-shaped shadows. The
 * RenderNameTagEvent that EntityRenderer.render posts before calling it still fires as before, renderers that override the
 * method (PlayerRenderer's score line) still run their own code and only their super calls return early, and Valkyrien
 * Skies' and Vivecraft's @WrapOperations inside the method are untouched outside the shadow pass. Priority 1500: lands in a
 * replacement of the method by another mod's @Overwrite of default priority instead of stopping the game. Carries no
 * Minecraft code.
 */
@Mixin(value = EntityRenderer.class, priority = 1500, remap = false)
public abstract class NameTagShadowMixin {
    @Inject(method = "m_7649_", at = @At("HEAD"), cancellable = true, require = 0)
    private void bons$shadowNameTag(Entity entity, Component name, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (ShadowSkips.NAME_TAGS.skip()) ci.cancel();
    }
}
