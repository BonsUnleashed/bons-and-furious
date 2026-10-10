package bons.furious.mixin.cataclysm_client;

import bons.furious.patch.cataclysm_client.ClientLeaks;
import com.github.L_Ender.cataclysm.ClientProxy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * cataclysm_client_leaks (ClientProxy, L_Ender's Cataclysm, CC-BY-NC-ND-4.0: no Cataclysm code is carried; 1.21.1 tested
 * build L_Ender's Cataclysm 1.21.1-3.33; client). Fix. When Cataclysm's client proxy is made (once, at mod construction on
 * a client), ClientLeaks registers its two listeners (client entity leaves a level, client level unloads).
 *
 * Ported to 1.21.1: Cataclysm 3.33's ClientProxy no longer declares init() (the Cataclysm constructor builds new
 * ClientProxy() only when FMLLoader's dist is the client and then calls the inherited ServerProxy.init()), so the hook sits
 * at the RETURN of ClientProxy's constructor: the same moment (mod construction), the same side (client only).
 */
@Mixin(value = ClientProxy.class, remap = false)
public abstract class ClientProxyLeakHooksMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void bons$registerLeakHooks(CallbackInfo ci) {
        ClientLeaks.register();
    }
}
