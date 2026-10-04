package bons.furious.mixin.farmersdelight_c2;

import bons.furious.patch.farmersdelight_c2.OverlayLookupMemo;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.NamedGuiOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import vectorwing.farmersdelight.client.gui.ComfortHealthOverlay;

/**
 * farmersdelight_overlay_lookup_memo (Farmer's Delight 1.3.4, MIT; client), call site 2 of 2.
 *
 * ComfortHealthOverlay.onRenderGuiOverlayPost runs for every HUD overlay every frame and compares the event's overlay
 * with GuiOverlayManager.findOverlay(PLAYER_HEALTH_ELEMENT). That lookup goes through OverlayLookupMemo.COMFORT, which
 * returns the very object findOverlay returns (same id object, same Forge overlay table object); the comparison and the
 * rest of the listener are untouched.
 */
@Mixin(value = ComfortHealthOverlay.class, remap = false)
public abstract class ComfortOverlayLookupMixin {
    @WrapOperation(method = "onRenderGuiOverlayPost", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/client/gui/overlay/GuiOverlayManager;findOverlay(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraftforge/client/gui/overlay/NamedGuiOverlay;"))
    private NamedGuiOverlay bons$rememberedOverlay(ResourceLocation id, Operation<NamedGuiOverlay> original) {
        return OverlayLookupMemo.COMFORT.find(id, original);
    }
}
