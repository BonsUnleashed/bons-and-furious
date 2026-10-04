package bons.furious.mixin.farmersdelight_c2;

import bons.furious.patch.farmersdelight_c2.OverlayLookupMemo;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.NamedGuiOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import vectorwing.farmersdelight.client.gui.NourishmentHungerOverlay;

/**
 * farmersdelight_overlay_lookup_memo (Farmer's Delight 1.3.4, MIT; client), call site 1 of 2.
 *
 * NourishmentHungerOverlay.onRenderGuiOverlayPost runs for every HUD overlay every frame and compares the event's
 * overlay with GuiOverlayManager.findOverlay(FOOD_LEVEL_ELEMENT). That lookup goes through OverlayLookupMemo.NOURISHMENT,
 * which returns the very object findOverlay returns (same id object, same Forge overlay table object); the comparison
 * and the rest of the listener are untouched.
 */
@Mixin(value = NourishmentHungerOverlay.class, remap = false)
public abstract class NourishmentOverlayLookupMixin {
    @WrapOperation(method = "onRenderGuiOverlayPost", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/client/gui/overlay/GuiOverlayManager;findOverlay(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraftforge/client/gui/overlay/NamedGuiOverlay;"))
    private NamedGuiOverlay bons$rememberedOverlay(ResourceLocation id, Operation<NamedGuiOverlay> original) {
        return OverlayLookupMemo.NOURISHMENT.find(id, original);
    }
}
