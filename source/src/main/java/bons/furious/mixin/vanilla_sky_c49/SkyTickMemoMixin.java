package bons.furious.mixin.vanilla_sky_c49;

import bons.furious.patch.vanilla_sky_c49.SkySample;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.CubicSampler;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_sky_color_sample_tick_memo (Minecraft 1.21.1, client; a trade-off, off by default), part 1 of 2: around the sky
 * colour sample of ClientLevel.getSkyColor(Vec3, float), one sample per client tick, level and biome manager
 * (SkySample.skyPerTick); time of day, rain, thunder and the lightning flash after the sample still follow every call.
 * Priority 1100: applied after the exact per-frame memo, so it is the outer wrapper.
 */
@Mixin(value = ClientLevel.class, priority = 1100, remap = false)
public abstract class SkyTickMemoMixin {
    @WrapOperation(method = "getSkyColor(Lnet/minecraft/world/phys/Vec3;F)Lnet/minecraft/world/phys/Vec3;", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/CubicSampler;gaussianSampleVec3(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/util/CubicSampler$Vec3Fetcher;)Lnet/minecraft/world/phys/Vec3;"),
            require = 1, allow = 1)
    private Vec3 bons$tickSkySample(Vec3 pos, CubicSampler.Vec3Fetcher fetcher, Operation<Vec3> original, @Local(ordinal = 0) BiomeManager manager) {
        return SkySample.skyPerTick(pos, fetcher, original, (ClientLevel) (Object) this, manager);
    }
}
