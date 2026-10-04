package bons.furious.mixin.module_resources;

import bons.furious.patch.module_resources.KiwiManifestLookup;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.io.InputStream;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * kiwi_manifest_lookup_index (Kiwi 11.8.28, both sides): AnnotatedTypeLoader.get's
 * ClassLoader.getResourceAsStream("/<modid>.kiwi.json") goes through KiwiManifestLookup / GameLayerResources, which gives
 * the same stream (or null) without asking every game-layer jar; elsewhere the call is unchanged. Kiwi is MIT; no Kiwi
 * code is carried.
 */
@Mixin(targets = "snownee.kiwi.loader.AnnotatedTypeLoader", remap = false)
public abstract class KiwiManifestLookupMixin {
    @WrapOperation(method = "get()Lsnownee/kiwi/loader/KiwiConfiguration;",
            at = @At(value = "INVOKE", target = "Ljava/lang/ClassLoader;getResourceAsStream(Ljava/lang/String;)Ljava/io/InputStream;"))
    private InputStream bons$indexedLookup(ClassLoader loader, String name, Operation<InputStream> original) {
        return KiwiManifestLookup.stream(loader, name, () -> original.call(loader, name));
    }
}
