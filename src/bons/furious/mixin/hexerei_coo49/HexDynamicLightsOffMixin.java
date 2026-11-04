package bons.furious.mixin.hexerei_coo49;

import bons.furious.patch.hexerei_coo49.HexLights;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * hexerei_dynamic_lights_off (Hexerei 1.20.1-0.4.2.3, MIT: no code carried; client; trade-off, off by default):
 * LightManager.shouldUpdateDynamicLight answers false, as Hexerei's own dynamic light toggle does when it is off, so
 * Hexerei never registers a light source and its light handlers return the vanilla value at once. See HexLights.
 */
@Mixin(targets = "net.joefoxe.hexerei.light.LightManager", remap = false)
public abstract class HexDynamicLightsOffMixin {
    @ModifyReturnValue(method = "shouldUpdateDynamicLight()Z", at = @At("RETURN"))
    private static boolean bons$hexereiLightsOff(boolean original) {
        return HexLights.shouldUpdate(original);
    }
}
