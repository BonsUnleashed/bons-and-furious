package bons.furious.mixin.punchy;

import bons.furious.patch.punchy.LazyGeoScans;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import punchy.client.model.ResourcePackGeoRegistry;

/**
 * punchy_lazy_resource_scans (Punchy 2.8g, client): the two resource scans of ResourcePackGeoRegistry.load wait for their
 * first reader (LazyGeoScans explains why every value a reader sees is stock's). Our own logic only (Punchy is All Rights
 * Reserved): HEAD injections that cancel loadGeoModels / loadModelBones while LazyGeoScans defers them, drop a stale wait at
 * load(), and run the waiting scans at the head of getGeoModel / getBonesForTarget.
 */
@Mixin(value = ResourcePackGeoRegistry.class, remap = false)
public abstract class GeoRegistryLazyScanMixin {
    @Inject(method = "load(Lnet/minecraft/server/packs/resources/ResourceManager;)V", at = @At("HEAD"))
    private static void bons$punchyLoad(ResourceManager manager, CallbackInfo ci) {
        LazyGeoScans.onLoad();
    }

    @Inject(method = "loadGeoModels(Lnet/minecraft/server/packs/resources/ResourceManager;)V", at = @At("HEAD"), cancellable = true)
    private static void bons$punchyGeoScan(ResourceManager manager, CallbackInfo ci) {
        if (LazyGeoScans.defer(manager)) ci.cancel();
    }

    @Inject(method = "loadModelBones(Lnet/minecraft/server/packs/resources/ResourceManager;)V", at = @At("HEAD"), cancellable = true)
    private static void bons$punchyModelScan(ResourceManager manager, CallbackInfo ci) {
        if (LazyGeoScans.defer(manager)) ci.cancel();
    }

    @Inject(method = "getGeoModel(Lnet/minecraft/resources/ResourceLocation;)Lpunchy/client/model/BedrockGeoModel;", at = @At("HEAD"))
    private static void bons$punchyReadGeo(ResourceLocation id, CallbackInfoReturnable<?> cir) {
        LazyGeoScans.beforeRead();
    }

    @Inject(method = "getBonesForTarget(Lnet/minecraft/resources/ResourceLocation;)Lpunchy/client/model/BedrockGeoModel;", at = @At("HEAD"))
    private static void bons$punchyReadBones(ResourceLocation id, CallbackInfoReturnable<?> cir) {
        LazyGeoScans.beforeRead();
    }
}
