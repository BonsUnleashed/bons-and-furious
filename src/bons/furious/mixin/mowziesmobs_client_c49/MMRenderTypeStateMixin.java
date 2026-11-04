package bons.furious.mixin.mowziesmobs_client_c49;

import bons.furious.patch.mowziesmobs_client_c49.EffectStates;
import com.bobmowzie.mowziesmobs.client.render.MMRenderType;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;

/**
 * mowziesmobs_effect_render_state_memo (Mowzie's Mobs 1.7.3, client, since 1.0.49): MMRenderType.getGlowingEffect and
 * getSolarFlare are wrapped; bons.furious.patch.mowziesmobs_client_c49.EffectStates makes each later render type of a
 * texture from the parameters and render state the original made for it first, or runs the original.
 */
@Mixin(value = MMRenderType.class, remap = false)
public abstract class MMRenderTypeStateMixin {
    @WrapMethod(method = "getGlowingEffect(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;", remap = false)
    private static RenderType bons$glowState(ResourceLocation texture, Operation<RenderType> original) {
        return EffectStates.glow(texture, original);
    }

    @WrapMethod(method = "getSolarFlare(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;", remap = false)
    private static RenderType bons$flareState(ResourceLocation texture, Operation<RenderType> original) {
        return EffectStates.flare(texture, original);
    }
}
