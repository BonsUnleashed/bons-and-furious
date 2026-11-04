package bons.furious.mixin.photon_c49;

import bons.furious.patch.photon_c49.PhotonCachePrune;
import com.lowdragmc.photon.client.fx.BlockEffect;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.Function;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * photon_block_effect_cache_prune (Photon 1.1.17; client; kind fix): two @Redirects in BlockEffect. (1) The "new ArrayList()"
 * of start's mapping function lambda$start$1 (it runs only when start's computeIfAbsent has to create the position's
 * entry) builds the same empty list and sets PhotonCachePrune.inserted. (2) start's one Map.computeIfAbsent call runs as
 * stock; after it, when the flag is set, PhotonCachePrune.afterInsert removes the map's other empty entries once the map
 * has reached its mark. updateFXObjectTick (where stock leaves the empty entries behind) is not touched. Every reader of
 * BlockEffect.CACHE treats a missing entry as an empty list, so the effects started, skipped and removed are the same; only
 * the empty entries no longer pile up until the next level change. Our own logic only.
 */
@Mixin(value = BlockEffect.class, remap = false)
public abstract class BlockEffectCachePruneMixin {
    @Redirect(method = "start()V", at = @At(value = "INVOKE", target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"),
            require = 1, allow = 1, remap = false)
    private Object bons$photonC49EntryLookup(Map<Object, Object> cache, Object pos, Function<Object, Object> mapping) {
        Object entry = cache.computeIfAbsent(pos, mapping);
        if (PhotonCachePrune.inserted) PhotonCachePrune.afterInsert(cache, entry);
        return entry;
    }

    @Redirect(method = "lambda$start$1(Lnet/minecraft/core/BlockPos;)Ljava/util/List;", at = @At(value = "NEW", target = "()Ljava/util/ArrayList;"),
            require = 1, allow = 1, remap = false)
    private static ArrayList<Object> bons$photonC49NewEntry() {
        if (PhotonCachePrune.enabled) PhotonCachePrune.inserted = true;
        return new ArrayList<>();
    }
}
