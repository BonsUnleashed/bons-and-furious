package bons.furious.mixin.client_class_warmup;

import bons.furious.patch.client_class_warmup.ClassWarmup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * client_class_warmup (Minecraft 1.21.1 on NeoForge 21.1.252, client; exact variant), part 3 of 3: at the HEAD of the two
 * methods that leave a world - Minecraft.disconnect(Screen, boolean) (disconnect() and disconnect(Screen) call it; the
 * connection's onDisconnect too) and Minecraft.clearClientLevel(Screen) (the server sends the client back to
 * configuration) - the recorded window closes early and the warm-up stops. Both methods run unchanged. Helper:
 * {@link ClassWarmup}.
 */
@Mixin(value = Minecraft.class, remap = false)
public abstract class MinecraftWarmupLeaveMixin {
    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V", at = @At("HEAD"), require = 0)
    private void bons$classWarmupLeave(Screen next, boolean keepResourcePacks, CallbackInfo ci) {
        ClassWarmup.onLeave();
    }

    @Inject(method = "clearClientLevel(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("HEAD"), require = 0)
    private void bons$classWarmupReconfigure(Screen next, CallbackInfo ci) {
        ClassWarmup.onLeave();
    }
}
