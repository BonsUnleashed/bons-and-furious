package bons.furious.mixin.vanilla;

import bons.furious.patch.vanilla.FogSample;
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
 * vanilla_fog_color_sample_memo (Minecraft 1.20.1 client), part 1 of 2: the 216-colour fog sample in
 * FogRenderer.setupColor goes through FogSample, which returns the same frame's identical sample. The brightness the
 * sample's fetcher captures is setupColor's local 12 (guarded by the method fingerprint); the level is its parameter.
 * BCLib, Blast from the Past, Oculus and YUNG's Cave Biomes hook other points of setupColor.
 */
@Mixin(value = FogRenderer.class, remap = false)
public abstract class FogColorSampleMixin {
    @WrapOperation(method = "setupColor", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/CubicSampler;gaussianSampleVec3(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/util/CubicSampler$Vec3Fetcher;)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 bons$frameSample(Vec3 pos, CubicSampler.Vec3Fetcher fetcher, Operation<Vec3> original,
                                         @Local(argsOnly = true) ClientLevel level, @Local(index = 12) float brightness) {
        return FogSample.sample(pos, fetcher, original, level, brightness);
    }
}
