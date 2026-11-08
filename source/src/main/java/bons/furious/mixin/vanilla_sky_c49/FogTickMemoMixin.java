package bons.furious.mixin.vanilla_sky_c49;

import bons.furious.patch.vanilla_sky_c49.SkySample;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.CubicSampler;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_fog_color_sample_tick_memo (Minecraft 1.21.1, client; a trade-off, off by default), part 1 of 2: around the fog
 * colour sample of FogRenderer.setupColor(Camera, float, ClientLevel, int, float), the one CubicSampler.gaussianSampleVec3
 * call, one sample per client tick, level and fetcher brightness. The brightness is setupColor's local 12 (the clamped
 * time-of-day factor its fetcher lambda$setupColor$0 captures, as vanilla_fog_color_sample_memo reads it; guarded by the
 * method fingerprint). Everything after the sample (sunrise tint, sky colour mix, rain, thunder, darkness, NeoForge's fog
 * colour event, clearColor) runs on every call. Priority 1100: the outer wrapper, around the exact per-frame fog memo and
 * Sodium's or Embeddium's redirect of the same call.
 */
@Mixin(value = FogRenderer.class, priority = 1100, remap = false)
public abstract class FogTickMemoMixin {
    @WrapOperation(method = "setupColor(Lnet/minecraft/client/Camera;FLnet/minecraft/client/multiplayer/ClientLevel;IF)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/CubicSampler;gaussianSampleVec3(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/util/CubicSampler$Vec3Fetcher;)Lnet/minecraft/world/phys/Vec3;"),
            require = 1, allow = 1)
    private static Vec3 bons$tickFogSample(Vec3 pos, CubicSampler.Vec3Fetcher fetcher, Operation<Vec3> original,
                                           @Local(argsOnly = true) ClientLevel level, @Local(index = 12) float brightness) {
        return SkySample.fogPerTick(pos, fetcher, original, level, brightness);
    }
}
