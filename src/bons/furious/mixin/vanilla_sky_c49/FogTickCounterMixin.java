package bons.furious.mixin.vanilla_sky_c49;

import bons.furious.patch.vanilla_sky_c49.SkySample;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** vanilla_fog_color_sample_tick_memo, part 2 of 2: Minecraft.tick (m_91398_) counts the client ticks the memo keys on. */
@Mixin(value = Minecraft.class, remap = false)
public abstract class FogTickCounterMixin {
    @Inject(method = "m_91398_", at = @At("HEAD"))
    private void bons$countFogTick(CallbackInfo ci) {
        SkySample.fogTick();
    }
}