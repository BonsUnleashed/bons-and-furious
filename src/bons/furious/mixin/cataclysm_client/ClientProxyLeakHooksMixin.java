package bons.furious.mixin.cataclysm_client;

import bons.furious.patch.cataclysm_client.ClientLeaks;
import com.github.L_Ender.cataclysm.ClientProxy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * cataclysm_client_leaks (ClientProxy, L_Ender's Cataclysm 3.16, CC-BY-NC-ND-4.0: no Cataclysm code is carried; client).
 * Fix. When Cataclysm's client proxy initialises (once, at mod construction on a client), ClientLeaks registers its two
 * Forge listeners (client entity leaves a level, client level unloads).
 */
@Mixin(value = ClientProxy.class, remap = false)
public abstract class ClientProxyLeakHooksMixin {
    @Inject(method = "init()V", at = @At("RETURN"))
    private void bons$registerLeakHooks(CallbackInfo ci) {
        ClientLeaks.register();
    }
}
