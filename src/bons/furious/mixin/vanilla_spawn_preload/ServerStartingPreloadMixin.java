package bons.furious.mixin.vanilla_spawn_preload;

import bons.furious.patch.vanilla_spawn_preload.SpawnAreaPreload;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * vanilla_spawn_area_preload (Forge 47.4.16): in ServerLifecycleHooks.handleServerStarting, right before ServerStartingEvent
 * is posted (the moment the former Bons Welcome Mat listened for), generate the spawn area and keep it loaded without
 * ticking. Logins open only later (handleServerStarted). The method itself runs unchanged. Helper: {@link SpawnAreaPreload}.
 */
@Mixin(value = ServerLifecycleHooks.class, remap = false)
public abstract class ServerStartingPreloadMixin {
    @Inject(method = "handleServerStarting",
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/eventbus/api/IEventBus;post(Lnet/minecraftforge/eventbus/api/Event;)Z", remap = false),
            remap = false, require = 1, allow = 1)
    private static void bons$prepareSpawnArea(MinecraftServer server, CallbackInfoReturnable<Boolean> cir) {
        SpawnAreaPreload.prepare(server);
    }
}
