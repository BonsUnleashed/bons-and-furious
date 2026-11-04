package bons.furious.mixin.oculus_passes;

import bons.furious.patch.oculus_passes.ShadowSkips;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * oculus_shadow_skip_banner_patterns (Minecraft 1.20.1 BannerRenderer, client; Oculus 1.8.0 tested; `change`, off by
 * default). BannerRenderer.renderPatterns (m_112074_, static; banners, banner items and shields) first renders the flag or
 * shield base with its base material, then for each pattern (up to 16 of them, `i < 17 && i < patterns.size()`) looks up
 * the pattern's material and renders the same model part again with the dye colour. MixinExtras @ModifyExpressionValue on
 * the List.size() of that loop condition: while Oculus renders its shadow map the loop sees size 0 and ends before the first
 * pattern; the base render before it and Entity Model Features' HEAD counter (MixinBannerRenderer.emf$injected) run as
 * before, and outside the shadow pass the value is the list's size. Difference (trade-off): pattern layers no longer tint
 * coloured shadows; the depth shape in the shadow map is the base cloth's either way (the pattern layers are the same model
 * part). Priority 1500, require = 0: a mod that replaces the method (an @Overwrite of default priority) keeps it, and this
 * switch finds no call to change there. Carries no Minecraft code.
 */
@Mixin(value = BannerRenderer.class, priority = 1500, remap = false)
public abstract class BannerPatternShadowMixin {
    @ModifyExpressionValue(method = "m_112074_", require = 0, at = @At(value = "INVOKE", target = "Ljava/util/List;size()I"))
    private static int bons$shadowPatternCount(int size) {
        return ShadowSkips.patternCount(size);
    }
}
