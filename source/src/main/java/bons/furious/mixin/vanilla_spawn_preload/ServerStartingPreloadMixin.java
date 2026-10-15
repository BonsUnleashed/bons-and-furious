package bons.furious.mixin.vanilla_spawn_preload;

import bons.furious.patch.vanilla_spawn_preload.SpawnAreaPreload;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prepare the first-login area before the server-starting listeners and before logins open. */
@Mixin(value = ServerLifecycleHooks.class, remap = false)
public abstract class ServerStartingPreloadMixin {
    @Inject(method = "handleServerStarting", at = @At(value = "INVOKE", target = "Lnet/neoforged/bus/api/IEventBus;post(Lnet/neoforged/bus/api/Event;)Lnet/neoforged/bus/api/Event;"), remap = false, require = 1, allow = 1)
    private static void bons$prepareSpawnArea(MinecraftServer server, CallbackInfo ci) {
        SpawnAreaPreload.prepare(server);
    }
}
