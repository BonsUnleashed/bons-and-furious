package bons.furious.mixin.emf_render;

import bons.furious.patch.emf_render.EmfRender;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import traben.entity_model_features.models.animation.EMFAnimationEntityContext;

/**
 * emf_anger_map_prune (Entity Model Features 3.2.4 on Minecraft 1.20.1 / Forge 47.4.16, client only). Fix.
 *
 * EMFAnimationEntityContext.getAngerTime() (the anger_time animation variable) keeps, per entity UUID, the highest anger
 * time seen in a static HashMap. For a neutral mob that is not angry it does knownHighestAngerTimeByUUID.put(uuid, 0), so
 * every neutral mob ever drawn with a model that reads anger_time keeps an entry (UUID + Integer + node) until the game
 * closes; nothing ever removes one. Every read of the map is getOrDefault(uuid, 0) (getAngerTime, getAngerTimeStart), so
 * an absent key and a key mapped to 0 read the same.
 *
 * This redirects that first put (ordinal 0, the anger-ended branch) to remove(uuid): the same value for every later read,
 * and the map no longer grows with mobs that are not angry. The second put (a new highest anger time) is unchanged.
 */
@Mixin(value = EMFAnimationEntityContext.class, remap = false)
public abstract class EmfAngerMapMixin {
    @Redirect(method = "getAngerTime", at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
            ordinal = 0), remap = false)
    private static Object bons$angerEnded(Map<Object, Object> map, Object uuid, Object zero) {
        if (EmfRender.angerMapPrune) {
            EmfRender.announceAnger();
            return map.remove(uuid);
        }
        return map.put(uuid, zero);
    }
}
