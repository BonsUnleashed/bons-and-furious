package bons.furious.mixin.module_resources;

import bons.furious.patch.module_resources.DhSqlScriptLookup;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.io.InputStream;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * distanthorizons_sql_script_lookup_index (Distant Horizons 3.3.2, both sides): both
 * ClassLoader.getResourceAsStream calls of DatabaseUpdater.getAutoUpdateScripts ("sqlScripts/scriptList.txt" and every
 * script it names) go through DhSqlScriptLookup / GameLayerResources, which gives the same stream (or null) without
 * asking every game-layer jar; elsewhere the calls are unchanged. Distant Horizons is LGPL-3.0; no DH code is carried.
 */
@Mixin(targets = "com.seibel.distanthorizons.core.sql.DatabaseUpdater", remap = false)
public abstract class DhSqlScriptLookupMixin {
    @WrapOperation(method = "getAutoUpdateScripts()Ljava/util/ArrayList;",
            at = @At(value = "INVOKE", target = "Ljava/lang/ClassLoader;getResourceAsStream(Ljava/lang/String;)Ljava/io/InputStream;"))
    private static InputStream bons$indexedLookup(ClassLoader loader, String name, Operation<InputStream> original) {
        return DhSqlScriptLookup.stream(loader, name, () -> original.call(loader, name));
    }
}
