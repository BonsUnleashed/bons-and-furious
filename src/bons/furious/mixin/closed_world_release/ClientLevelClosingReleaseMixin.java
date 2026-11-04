package bons.furious.mixin.closed_world_release;

import bons.furious.patch.closed_world_release.ClosedWorldRelease;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.client.ForgeHooksClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * closed_world_reference_release (Forge 47.4.28, client; since 1.0.49). Fix. ForgeHooksClient.handleClientLevelClosing
 * (Minecraft.clearLevel calls it once when the client leaves its world), at its start: Citadel's book preview entities,
 * which keep the ClientLevel they were created in, are dropped (ClosedWorldRelease). The method runs unchanged.
 */
@Mixin(value = ForgeHooksClient.class, remap = false)
public abstract class ClientLevelClosingReleaseMixin {
    @Inject(method = "handleClientLevelClosing", at = @At("HEAD"), remap = false, require = 1, allow = 1)
    private static void bons$releaseOnLevelClosing(ClientLevel level, CallbackInfo ci) {
        ClosedWorldRelease.clientLevelClosing(level);
    }
}
