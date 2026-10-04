package bons.furious.mixin.module_resources;

import bons.furious.patch.module_resources.IceAndFireTabulaLookup;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.io.InputStream;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * iceandfire_tabula_lookup_index (Ice and Fire 2.1.13-1.20.1-beta-5, client): TabulaModelHandlerHelper.loadTabulaModel's
 * ClassLoader.getResourceAsStream("/assets/iceandfire/models/tabula/....tbl") goes through IceAndFireTabulaLookup /
 * GameLayerResources, which gives the same stream (or null) without asking every game-layer jar; elsewhere the call is
 * unchanged. Ice and Fire is LGPL; no Ice and Fire code is carried.
 */
@Mixin(targets = "com.github.alexthe666.iceandfire.client.model.util.TabulaModelHandlerHelper", remap = false)
public abstract class IceAndFireTabulaLookupMixin {
    @WrapOperation(method = "loadTabulaModel(Ljava/lang/String;)Lcom/github/alexthe666/citadel/client/model/container/TabulaModelContainer;",
            at = @At(value = "INVOKE", target = "Ljava/lang/ClassLoader;getResourceAsStream(Ljava/lang/String;)Ljava/io/InputStream;"))
    private static InputStream bons$indexedLookup(ClassLoader loader, String name, Operation<InputStream> original) {
        return IceAndFireTabulaLookup.stream(loader, name, () -> original.call(loader, name));
    }
}
