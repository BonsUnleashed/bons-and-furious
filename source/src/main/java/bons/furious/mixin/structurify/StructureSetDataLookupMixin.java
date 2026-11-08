package bons.furious.mixin.structurify;

import bons.furious.patch.structurify.SetDataLookup;
import com.faboslav.structurify.common.util.RandomSpreadUtil;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * structurify_set_data_single_lookup (Structurify 2.0.42 for NeoForge 1.21.1).
 *
 * getStructureSetData walks its TreeMap twice (containsKey, then get) on every placement-setting lookup. Both calls are
 * redirected to SetDataLookup, which walks it once and hands the value to the get; the method's result is the same in
 * every case. Since 1.0.49 SetDataLookup answers both calls from a hash index of the same map (same keys, same value
 * objects), rebuilt whenever the map asked is another object or holds another number of entries, so no TreeMap walk is
 * left. Structurify's licence allows no derivatives, so only the two map calls are redirected and none of its code is
 * carried here.
 */
@Mixin(value = RandomSpreadUtil.class, remap = false)
public abstract class StructureSetDataLookupMixin {
    @Redirect(method = "getStructureSetData", at = @At(value = "INVOKE", target = "Ljava/util/Map;containsKey(Ljava/lang/Object;)Z"))
    private static boolean bons$lookupOnce(Map<?, ?> map, Object key) {
        return SetDataLookup.containsKey(map, key);
    }

    @Redirect(method = "getStructureSetData", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private static Object bons$valueJustRead(Map<?, ?> map, Object key) {
        return SetDataLookup.get(map, key);
    }
}
