package bons.furious.mixin.vanilla_mob_warmup;

import bons.furious.patch.vanilla_mob_warmup.MobClassWarmup;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * vanilla_mob_class_warmup (NeoForge 21.1.252): when a server has started (ServerLifecycleHooks.handleServerStarted, after
 * the ServerStartedEvent listeners ran), start the once-per-JVM background warm-up of the mob classes. The method itself
 * runs unchanged. Helper: {@link MobClassWarmup}.
 */
@Mixin(value = ServerLifecycleHooks.class, remap = false)
public abstract class ServerStartedWarmupMixin {
    @Inject(method = "handleServerStarted", at = @At("RETURN"), remap = false, require = 1, allow = 1)
    private static void bons$warmMobClasses(MinecraftServer server, CallbackInfo ci) {
        MobClassWarmup.start();
    }
}
