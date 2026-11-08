package bons.furious.mixin.oculus_passes;

import bons.furious.patch.oculus_passes.ShadowSkips;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * oculus_shadow_skip_item_glint (Minecraft 1.21.1 on NeoForge 21.1.252 ItemStack, client; Iris 1.8.12+mc1.21.1 for
 * NeoForge; `change`, off by default). Item, armour and elytra renderers ask ItemStack.hasFoil whether to add the
 * enchantment glint layer (the ENCHANTMENT_GLINT_OVERRIDE component, else Item.isFoil), a second draw of the model with a
 * glint render type, and the shield renderer passes it on to the banner pattern draw; Iris binds its glint program for
 * those draws in the shadow pass too. MixinExtras @ModifyReturnValue at its RETURN: false while Iris renders its shadow map
 * and the caller is the render thread (ShadowSkips.ITEM_GLINT.skipOnRenderThread, asked only when the answer is true), so
 * the glint layer is left out of the shadow map; glint render types write colour only, never depth, so the shadow's shape is
 * unchanged. Every other caller and thread gets the stock answer; a mod that calls hasFoil for its own glint draw (Hexerei's
 * witch armour layer) gets the same answer. Priority 1500: lands in a replacement of the method by another mod's @Overwrite
 * of default priority; require = 0. Carries no Minecraft code.
 */
@Mixin(value = ItemStack.class, priority = 1500, remap = false)
public abstract class ItemGlintShadowMixin {
    @ModifyReturnValue(method = "hasFoil()Z", require = 0, at = @At("RETURN"))
    private boolean bons$shadowGlint(boolean foil) {
        return foil && !ShadowSkips.ITEM_GLINT.skipOnRenderThread();
    }
}
