package bons.furious.mixin.vanilla_sky_c49;

import bons.furious.patch.vanilla_sky_c49.SkySample;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * vanilla_sky_color_sample_tick_memo (Minecraft 1.21.1, client), part 2 of 2: the start of Minecraft.tick() (once per client
 * tick) counts the client ticks the memo keys on. The tick itself runs unchanged.
 */
@Mixin(value = Minecraft.class, remap = false)
public abstract class SkyTickCounterMixin {
    @Inject(method = "tick()V", at = @At("HEAD"))
    private void bons$countSkyTick(CallbackInfo ci) {
        SkySample.skyTick();
    }
}
