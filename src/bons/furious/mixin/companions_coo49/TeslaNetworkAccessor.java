package bons.furious.mixin.companions_coo49;

import bons.furious.patch.companions_coo49.TeslaDistances;
import dev.xylonity.companions.common.tesla.TeslaNetwork;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * companions_tesla_lazy_distances: hands TeslaNetwork.NETWORKS to TeslaDistances once, at the end of TeslaNetwork's static
 * initializer, so a distance read finds its network without TeslaNetwork.get (which would create one). Changes nothing in
 * TeslaNetwork.
 */
@Mixin(value = TeslaNetwork.class, remap = false)
public abstract class TeslaNetworkAccessor {
    @Shadow
    @Final
    private static Map<ResourceKey<Level>, TeslaNetwork> NETWORKS;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void bons$shareNetworks(CallbackInfo ci) {
        TeslaDistances.networks = NETWORKS;
    }
}
