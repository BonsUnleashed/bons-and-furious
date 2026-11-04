package bons.furious.mixin.oculus_passes;

import bons.furious.patch.oculus_passes.ShadowSkips;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * oculus_shadow_skip_item_glint (Minecraft 1.20.1 on Forge 47.4.28 ItemStack, client; Oculus 1.8.0 tested; `change`, off
 * by default). Item, armour, elytra and trident renderers ask ItemStack.hasFoil (m_41790_) whether to add the enchantment
 * glint layer, a second draw of the model with a glint render type; Oculus binds its glint program for those draws in the
 * shadow pass too. MixinExtras @ModifyReturnValue at its RETURN: false while Oculus renders its shadow map and the caller is
 * the render thread (ShadowSkips.ITEM_GLINT.skipOnRenderThread, asked only when the answer is true), so the glint layer is
 * left out of the shadow map; glint render types write colour only, never depth, so the shadow's shape is unchanged.
 * Every other caller and thread gets the stock answer. CIT Reforged's HEAD hook (ItemStackMixin, which answers false
 * itself when its own enchantment textures replace the default glint) runs as before. Priority 1500: lands in a
 * replacement of the method by another mod's @Overwrite of default priority; require = 0. Carries no Minecraft code.
 */
@Mixin(value = ItemStack.class, priority = 1500, remap = false)
public abstract class ItemGlintShadowMixin {
    @ModifyReturnValue(method = "m_41790_", require = 0, at = @At("RETURN"))
    private boolean bons$shadowGlint(boolean foil) {
        return foil && !ShadowSkips.ITEM_GLINT.skipOnRenderThread();
    }
}
