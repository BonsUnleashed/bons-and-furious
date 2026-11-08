package bons.furious.mixin.immersive_overlays;

import bons.furious.patch.immersive_overlays.HudQuadBatch;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import immersive_aircraft.client.OverlayRenderer;
import immersive_aircraft.entity.VehicleEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * immersiveaircraft_hud_blit_batch (Immersive Aircraft, GPL-3.0-only; 1.21.1 tested build 1.7.0+1.21.1; client):
 * OverlayRenderer.renderAircraftHealth (the vehicle health hearts, every frame while riding one of its vehicles). @WrapMethod
 * opens a batch for the call and draws it when the call ends (also on an exception); each of its three
 * GuiGraphics.blit(RL, x, y, u, v, w, h, tw, th) calls is wrapped so that, while the batch is open, the quad innerBlit would
 * draw goes into it (HudQuadBatch.blit; the same vertices in the same order, one draw per texture instead of one per heart).
 * Batch not opened (switch off, ImmediatelyFast HUD batching on this GuiGraphics, Vivecraft VR, off the render thread):
 * every blit runs as shipped. renderAircraftGui (engine and power gauges) is left alone: its blits change shader colour and
 * blending in between. No code of Immersive Aircraft is carried (the wrappers only decide where its unchanged blit calls go).
 *
 * Ported to 1.21.1: renderAircraftHealth gained the bar height offset parameter (Minecraft, GuiGraphics, VehicleEntity, int);
 * the three blits are the same overload with the same ICONS_TEX.
 */
@Mixin(value = OverlayRenderer.class, remap = false)
public abstract class AircraftHealthBatchMixin {
    @WrapMethod(method = "renderAircraftHealth(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/gui/GuiGraphics;Limmersive_aircraft/entity/VehicleEntity;I)V")
    private void bons$healthBatch(Minecraft minecraft, GuiGraphics context, VehicleEntity vehicle, int barHeightOffset, Operation<Void> original) {
        if (!HudQuadBatch.begin(context)) {
            original.call(minecraft, context, vehicle, barHeightOffset);
            return;
        }
        try {
            original.call(minecraft, context, vehicle, barHeightOffset);
        } finally {
            HudQuadBatch.end(context);
        }
    }

    @WrapOperation(method = "renderAircraftHealth(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/gui/GuiGraphics;Limmersive_aircraft/entity/VehicleEntity;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIFFIIII)V"))
    private void bons$healthBlit(GuiGraphics g, ResourceLocation atlas, int x, int y, float u, float v, int w, int h, int texW, int texH,
                                 Operation<Void> original) {
        if (!HudQuadBatch.blit(g, atlas, x, y, u, v, w, h, texW, texH)) original.call(g, atlas, x, y, u, v, w, h, texW, texH);
    }
}
