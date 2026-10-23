package bons.furious.mixin.distanthorizons_level_wrappers;

import bons.furious.patch.distanthorizons_level_wrappers.KeyedLevelPins;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.seibel.distanthorizons.common.wrappers.level.KeyedClientLevelManager_neoforge;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * distanthorizons_keyed_level_pins (Distant Horizons 3.3.2), client only. [FIX]
 *
 * KeyedClientLevelManager_neoforge.getServerKeyedLevel reads keyedLevelsCache.get(level) (the first Map.get of the method;
 * the second reads keysByDimensionName) and stores a new wrapper with keyedLevelsCache.put(level, wrapper). The put stores
 * the wrapper behind a weak reference and pins it on the ClientLevel; the get opens that reference (helper
 * bons.furious.patch.distanthorizons_level_wrappers.KeyedLevelPins). The removeIf and clear calls elsewhere in the class
 * work on keys and stay as they are. With the switch off the put stores the wrapper as DH does. No Distant Horizons code is
 * carried.
 */
@Mixin(value = KeyedClientLevelManager_neoforge.class, remap = false)
public abstract class KeyedLevelCacheMixin {
    @WrapOperation(method = "getServerKeyedLevel", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;", ordinal = 0))
    @SuppressWarnings({"rawtypes"})
    private Object bons$pinnedGet(Map cache, Object level, Operation<Object> original) {
        return KeyedLevelPins.get(cache, level, original.call(cache, level));
    }

    @WrapOperation(method = "getServerKeyedLevel", at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    @SuppressWarnings({"rawtypes"})
    private Object bons$pinnedPut(Map cache, Object level, Object wrapper, Operation<Object> original) {
        return original.call(cache, level, KeyedLevelPins.valueFor(cache, level, wrapper));
    }
}
