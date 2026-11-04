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
 * immersivearmors_steampunk_item_identity (Immersive Armors 1.6.1, client): HEAD of OverlayRenderer.renderOverlay (the
 * hotbar overlay hook that runs every frame). With the steampunk chestplate resolved (SteampunkOverlay.chestplate), the
 * same outer tests run (GUI not hidden, a game mode and a player), the same four armour slots are walked in the same order,
 * and renderSteampunkHud runs once for every slot whose item is that chestplate; the stock body is then skipped. Stock
 * asked the registry for each slot item's id and compared it with a new ResourceLocation; the identity test is the same
 * test (see SteampunkOverlay). Unresolved chestplate or the runtime switch off: the stock body runs. No Immersive Armors
 * code is carried: the injection only calls the mod's own renderSteampunkHud and Main.locate; the loop and the test are
 * ours. No other mod's mixin targets this class (both mixin indexes, 2026-10-28).
 */
@Mixin(value = OverlayRenderer.class, remap = false)
public abstract class ImmersiveArmorsOverlayMixin {
    @Shadow
    private static void renderSteampunkHud(GuiGraphics context) {
        throw new AssertionError();
    }

    @Inject(method = "renderOverlay", at = @At("HEAD"), cancellable = true)
    private static void bons$steampunkByIdentity(GuiGraphics context, CallbackInfo ci) {
        if (!SteampunkOverlay.enabled) return;
        Item chestplate = SteampunkOverlay.resolved();
        if (chestplate == null) {
            if (SteampunkOverlay.gaveUp()) return;
            chestplate = SteampunkOverlay.chestplate(Main.locate("steampunk_chestplate"));
            if (chestplate == null) return;
        }
        Minecraft client = Minecraft.m_91087_();
        if (!client.f_91066_.f_92062_ && client.f_91072_ != null && client.f_91074_ != null) {
            for (ItemStack item : client.f_91074_.m_6168_()) {
                if (SteampunkOverlay.matches(item, chestplate)) renderSteampunkHud(context);
            }
        }
        ci.cancel();
    }
}
