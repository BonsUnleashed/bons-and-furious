package bons.furious.mixin.saintsdragons_coo49;

import bons.furious.patch.saintsdragons_coo49.SaintsHud;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * saintsdragons_ride_hud_once_per_frame (Saint's Dragons 0.9.88 and 0.9.85, client; trade-off, off by default): a
 * cancellable @Inject before the second LocalPlayer.getVehicle (m_20202_) call of DragonUIEventHandler.onRenderGuiOverlay,
 * which is where the per-layer riding HUD part starts (after the hotbar-only parts): left out while SaintsHud runs the
 * listener once per frame instead (SaintsHud.onFrame). See SaintsHud for what a player can notice.
 */
@Mixin(targets = "com.leon.saintsdragons.forge.client.event.DragonUIEventHandler", remap = false)
public abstract class DragonHudOncePerFrameMixin {
    @Inject(method = "onRenderGuiOverlay(Lnet/minecraftforge/client/event/RenderGuiOverlayEvent$Post;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;m_20202_()Lnet/minecraft/world/entity/Entity;", ordinal = 1),
            cancellable = true, require = 1, allow = 1)
    private static void bons$saintsdragonsCoo49HudTail(RenderGuiOverlayEvent.Post event, CallbackInfo ci) {
        if (SaintsHud.skipTail()) ci.cancel();
    }
}
