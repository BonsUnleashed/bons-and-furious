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
 * vanilla_sky_color_sample_tick_memo (Minecraft 1.20.1 client; a trade-off, off by default), part 1 of 2: around the sky
 * colour sample of ClientLevel.getSkyColor, one sample per client tick and level (SkySample.skyPerTick). Priority 1100:
 * applied after the exact per-frame memo, so it is the outer wrapper.
 */
@Mixin(value = ClientLevel.class, priority = 1100, remap = false)
public abstract class SkyTickMemoMixin {
    @WrapOperation(method = "m_171660_", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/CubicSampler;m_130038_(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/util/CubicSampler$Vec3Fetcher;)Lnet/minecraft/world/phys/Vec3;"),
            require = 1, allow = 1)
    private Vec3 bons$tickSkySample(Vec3 pos, CubicSampler.Vec3Fetcher fetcher, Operation<Vec3> original, @Local(ordinal = 0) BiomeManager manager) {
        return SkySample.skyPerTick(pos, fetcher, original, (ClientLevel) (Object) this, manager);
    }
}