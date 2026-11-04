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
 * immersiveaircraft_hud_blit_batch (Immersive Aircraft 1.1.8, client): OverlayRenderer.renderAircraftHealth (the vehicle
 * health hearts, every frame while riding one of its vehicles). @WrapMethod opens a batch for the call and draws it when
 * the call ends (also on an exception); each of its three GuiGraphics.blit(RL, x, y, u, v, w, h, tw, th) (m_280163_)
 * calls is wrapped so that, while the batch is open, the quad innerBlit would draw goes into it (HudQuadBatch.blit; the
 * same vertices in the same order, one draw per texture instead of one per heart). Batch not opened (switch off,
 * ImmediatelyFast HUD batching, Vivecraft VR, off the render thread, builder busy): every blit runs as shipped.
 * renderAircraftGui (engine and power gauges) is left alone: its blits change shader colour and blending in between.
 * Immersive Aircraft is GPL-3.0-only; no code of it is carried (the wrappers only decide where its unchanged blit calls
 * go). No other mod's mixin targets this class (both mixin indexes, 2026-10-28).
 */
@Mixin(value = OverlayRenderer.class, remap = false)
public abstract class AircraftHealthBatchMixin {
    @WrapMethod(method = "renderAircraftHealth")
    private void bons$healthBatch(Minecraft minecraft, GuiGraphics context, VehicleEntity vehicle, Operation<Void> original) {
        if (!HudQuadBatch.begin(context)) {
            original.call(minecraft, context, vehicle);
            return;
        }
        try {
            original.call(minecraft, context, vehicle);
        } finally {
            HudQuadBatch.end(context);
        }
    }

    @WrapOperation(method = "renderAircraftHealth", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;m_280163_(Lnet/minecraft/resources/ResourceLocation;IIFFIIII)V"))
    private void bons$healthBlit(GuiGraphics g, ResourceLocation atlas, int x, int y, float u, float v, int w, int h, int texW, int texH,
                                 Operation<Void> original) {
        if (!HudQuadBatch.blit(g, atlas, x, y, u, v, w, h, texW, texH)) original.call(g, atlas, x, y, u, v, w, h, texW, texH);
    }
}
