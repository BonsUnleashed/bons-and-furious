package bons.furious.mixin.emf_lean;

import bons.furious.patch.emf_lean.EmfListedMisses;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import traben.entity_model_features.utils.EMFResourceCaching;

/**
 * emf_listed_resource_misses (tested build: Entity Model Features 3.3.9 for NeoForge 1.21.1,
 * entity_model_features-3.3.9-1.21-neoforge.jar; client only). Trade-off switch, off by default.
 *
 * Three hooks in EMFResourceCaching, all with EMF's own calls kept: the listResources call in scanPrefix is wrapped to note
 * that the listing of that prefix returned normally; the getResource call in resourceExists is wrapped so that
 * EmfListedMisses can answer a miss from the listing (see there for the exact conditions and what differs); clearCache
 * (EMF's reset on every reload) starts a new listing. require = 0: a method another mod already changed keeps EMF's own
 * call.
 *
 * Ported to 1.21.1: EMF 3.3.9's EMFResourceCaching has the same cache, scanPrefix, resourceExists and clearCache as the
 * 3.3.10 / 3.3.12 Forge builds; EMFManager.resetInstance still calls clearCache on every reload.
 */
@Mixin(value = EMFResourceCaching.class, remap = false)
public abstract class EmfListedMissesMixin {
    @WrapOperation(method = "scanPrefix", require = 0, expect = 0, remap = false, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/packs/resources/ResourceManager;listResources(Ljava/lang/String;Ljava/util/function/Predicate;)Ljava/util/Map;",
            remap = false))
    private static Map<ResourceLocation, Resource> bons$listedPrefix(ResourceManager resources, String path, Predicate<ResourceLocation> filter,
                                                                     Operation<Map<ResourceLocation, Resource>> original) {
        return EmfListedMisses.listed(resources, path, filter, original);
    }

    @WrapOperation(method = "resourceExists", require = 0, expect = 0, remap = false, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/packs/resources/ResourceManager;getResource(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;",
            remap = false))
    private static Optional<Resource> bons$listedMiss(ResourceManager resources, ResourceLocation location, Operation<Optional<Resource>> original) {
        return EmfListedMisses.lookup(resources, location, original);
    }

    @Inject(method = "clearCache", at = @At("HEAD"), require = 0, expect = 0, remap = false)
    private static void bons$listingCleared(CallbackInfo ci) {
        EmfListedMisses.cleared();
    }
}
