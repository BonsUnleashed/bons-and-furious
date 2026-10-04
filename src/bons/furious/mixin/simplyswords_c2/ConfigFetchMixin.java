package bons.furious.mixin.simplyswords_c2;

import bons.furious.patch.simplyswords_c2.ConfigFetchMemo;
import com.google.gson.JsonObject;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.io.File;
import java.util.HashMap;
import net.sweenus.simplyswords.config.Config;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * simplyswords_config_unchanged_reuse (Simply Swords 1.56.0-1.20.1, Timefall Development License; both sides).
 *
 * Four wrappers, no Simply Swords code: the whole safeValueFetch (our lock and call context), its readFile(file) calls
 * (the file's bytes decide between applying the puts kept for those bytes and handing the text made from them to the
 * parse), its getJsonObject(text) calls (skipped when the kept puts were applied) and its four map.put calls (recorded
 * while the original runs). See {@link ConfigFetchMemo} for why the maps end up the same.
 */
@Mixin(value = Config.class, remap = false)
public abstract class ConfigFetchMixin {
    @WrapMethod(method = "safeValueFetch")
    private static void bons$fetch(String type, String parent, Operation<Void> original) {
        ConfigFetchMemo.fetch(type, parent, original);
    }

    @WrapOperation(method = "safeValueFetch", require = 7, allow = 7, at = @At(value = "INVOKE",
            target = "Lnet/sweenus/simplyswords/config/Config;readFile(Ljava/io/File;)Ljava/lang/String;"))
    private static String bons$read(File file, Operation<String> original) {
        return ConfigFetchMemo.readFile(file, original);
    }

    @WrapOperation(method = "safeValueFetch", require = 7, allow = 7, at = @At(value = "INVOKE",
            target = "Lnet/sweenus/simplyswords/config/Config;getJsonObject(Ljava/lang/String;)Lcom/google/gson/JsonObject;"))
    private static JsonObject bons$parse(String text, Operation<JsonObject> original) {
        return ConfigFetchMemo.json(text, original);
    }

    @WrapOperation(method = "safeValueFetch", require = 4, allow = 4, at = @At(value = "INVOKE",
            target = "Ljava/util/HashMap;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private static Object bons$put(HashMap<Object, Object> map, Object key, Object value, Operation<Object> original) {
        return ConfigFetchMemo.put(map, key, value, original);
    }
}
