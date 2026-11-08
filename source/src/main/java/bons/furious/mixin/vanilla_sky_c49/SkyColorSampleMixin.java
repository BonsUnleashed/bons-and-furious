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
 * vanilla_sky_color_sample_memo (Minecraft 1.21.1, client), part 2 of 2: the 216-colour sky sample in
 * ClientLevel.getSkyColor(Vec3, float), the one CubicSampler.gaussianSampleVec3 call, goes through SkySample, which hands out
 * the same frame's identical sample for the same level, biome manager and sample position. The biome manager is the one the
 * sample's fetcher captured (the method's only BiomeManager local). Time of day, rain, thunder and the lightning flash after
 * the sample run on every call. Sodium's and Embeddium's FastCubicSampler redirect of the same call is wrapped.
 */
@Mixin(value = ClientLevel.class, remap = false)
public abstract class SkyColorSampleMixin {
    @WrapOperation(method = "getSkyColor(Lnet/minecraft/world/phys/Vec3;F)Lnet/minecraft/world/phys/Vec3;", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/CubicSampler;gaussianSampleVec3(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/util/CubicSampler$Vec3Fetcher;)Lnet/minecraft/world/phys/Vec3;"),
            require = 1, allow = 1)
    private Vec3 bons$frameSkySample(Vec3 pos, CubicSampler.Vec3Fetcher fetcher, Operation<Vec3> original, @Local(ordinal = 0) BiomeManager manager) {
        return SkySample.sky(pos, fetcher, original, (ClientLevel) (Object) this, manager);
    }
}
