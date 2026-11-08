package bons.furious.mixin.immersive_overlays;

import bons.furious.patch.immersive_overlays.SteampunkOverlay;
import immersive_armors.Main;
import immersive_armors.client.OverlayRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * immersivearmors_steampunk_item_identity (Immersive Armors, GPL-3.0-only; 1.21.1 tested build 1.7.6+1.21.1; client): HEAD
 * of OverlayRenderer.renderOverlay (called by the mod's Gui mixin at the TAIL of Gui.renderHotbarAndDecorations, every
 * frame). With the steampunk chestplate resolved (SteampunkOverlay.chestplate), the same outer tests run (GUI not hidden, a
 * game mode and a player), the same four armour slots are walked in the same order, and renderSteampunkHud runs once for
 * every slot whose item is that chestplate; the stock body is then skipped. Stock asked the registry for each slot item's
 * id and compared it with a new ResourceLocation; the identity test is the same test (see SteampunkOverlay). Unresolved
 * chestplate or the runtime switch off: the stock body runs. No Immersive Armors code is carried: the injection only calls
 * the mod's own renderSteampunkHud and Main.locate; the loop and the test are ours.
 */
@Mixin(value = OverlayRenderer.class, remap = false)
public abstract class ImmersiveArmorsOverlayMixin {
    @Shadow
    private static void renderSteampunkHud(GuiGraphics context) {
        throw new AssertionError();
    }

    @Inject(method = "renderOverlay(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At("HEAD"), cancellable = true)
    private static void bons$steampunkByIdentity(GuiGraphics context, CallbackInfo ci) {
        if (!SteampunkOverlay.enabled) return;
        Item chestplate = SteampunkOverlay.resolved();
        if (chestplate == null) {
            if (SteampunkOverlay.gaveUp()) return;
            chestplate = SteampunkOverlay.chestplate(Main.locate("steampunk_chestplate"));
            if (chestplate == null) return;
        }
        Minecraft client = Minecraft.getInstance();
        if (!client.options.hideGui && client.gameMode != null && client.player != null) {
            for (ItemStack item : client.player.getArmorSlots()) {
                if (SteampunkOverlay.matches(item, chestplate)) renderSteampunkHud(context);
            }
        }
        ci.cancel();
    }
}
