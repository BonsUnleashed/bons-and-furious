package bons.furious.mixin.closed_world_release;

import bons.furious.patch.closed_world_release.ClosedWorldRelease;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * closed_world_reference_release (Minecraft 1.21.1 on NeoForge 21.1.252, client; since 1.0.49). Fix. The client leaves its
 * world in Minecraft.disconnect(Screen, boolean) (after LevelEvent.Unload and, for a single-player world, after the
 * integrated server has shut down) and in Minecraft.clearClientLevel(Screen) (the server sends the client back to
 * configuration); right before either sets Minecraft.level to null, while a level is still open, Citadel's book preview
 * entities, which keep the ClientLevel they were created in, are dropped (ClosedWorldRelease). Both methods run unchanged.
 */
@Mixin(value = Minecraft.class, remap = false)
public abstract class ClientLevelClosingReleaseMixin {
    @Shadow
    public ClientLevel level;

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V", require = 1, allow = 1, at = @At(value = "FIELD",
            opcode = Opcodes.PUTFIELD, target = "Lnet/minecraft/client/Minecraft;level:Lnet/minecraft/client/multiplayer/ClientLevel;"))
    private void bons$releaseOnDisconnect(Screen next, boolean keepResourcePacks, CallbackInfo ci) {
        if (this.level != null) ClosedWorldRelease.clientLevelClosing(this.level);
    }

    @Inject(method = "clearClientLevel(Lnet/minecraft/client/gui/screens/Screen;)V", require = 1, allow = 1, at = @At(value = "FIELD",
            opcode = Opcodes.PUTFIELD, target = "Lnet/minecraft/client/Minecraft;level:Lnet/minecraft/client/multiplayer/ClientLevel;"))
    private void bons$releaseOnReconfiguration(Screen next, CallbackInfo ci) {
        if (this.level != null) ClosedWorldRelease.clientLevelClosing(this.level);
    }
}
