package bons.furious.mixin.oculus;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import net.irisshaders.iris.uniforms.custom.cached.CachedUniform;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * oculus_primitive_uniform_locations (Oculus 1.8.0 for Minecraft 1.20.1).
 *
 * push uploads a shader pass's custom uniforms whenever the pass is used. Iterating the location map with
 * Map.forEach(BiConsumer) boxed every uniform location into an Integer. When the map is Oculus' own
 * Object2IntOpenHashMap, its primitive entries are walked with Object2IntMaps.fastForEach instead; any other map
 * implementation keeps the original forEach call.
 */
@Mixin(value = CustomUniforms.class, remap = false)
public abstract class CustomUniformsPushMixin {
    @Shadow @Final private Map<Object, Object2IntMap<CachedUniform>> locationMap;

    /**
     * @author BonsUnleashed
     * @reason Push custom uniforms without boxing their locations.
     */
    @Overwrite
    public void push(Object pass) {
        Object2IntMap<CachedUniform> uniforms = this.locationMap.get(pass);
        if (uniforms != null) {
            ac$pushLocations(uniforms, CachedUniform::pushIfChanged);
        }
    }

    /** Calls pushIfChanged(location) for every entry; push is the original callback, used for other map types. */
    @Unique
    private static void ac$pushLocations(Object2IntMap<CachedUniform> locations, BiConsumer<CachedUniform, Integer> push) {
        if (locations.getClass() != Object2IntOpenHashMap.class) {
            locations.forEach(push);
            return;
        }
        Object2IntMaps.fastForEach(locations, entry -> entry.getKey().pushIfChanged(entry.getIntValue()));
    }
}
