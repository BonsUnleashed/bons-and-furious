package bons.furious.mixin.emf_render;

import bons.furious.patch.emf_render.EmfRender;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import traben.entity_model_features.models.animation.math.EMFMath;

/**
 * emf_anger_map_prune (tested build: Entity Model Features 3.3.9 for NeoForge 1.21.1,
 * entity_model_features-3.3.9-1.21-neoforge.jar, on Minecraft 1.21.1 / NeoForge 21.1.252, client only; first written for
 * EMF 3.2.4 on Minecraft 1.20.1 / Forge 47.4.16). Fix.
 *
 * EMFMath.getAngerTime() (the anger_time animation variable) keeps, per entity UUID, the highest anger time seen in a
 * private static HashMap (EMFMath$1, whose only override is get = getOrDefault(key, 0)). For a neutral mob that is not
 * angry it does knownHighestAngerTimeByUUID.put(uuid, 0), so every neutral mob ever drawn with a model that reads
 * anger_time keeps an entry (UUID + Integer + node) until the game closes; nothing ever removes one. The map is read only
 * by getAngerTime and getAngerTimeStart, both with getOrDefault(uuid, 0), so an absent key and a key mapped to 0 read the
 * same.
 *
 * This redirects that first put (ordinal 0, the anger-ended branch) to remove(uuid): the same value for every later read,
 * and the map no longer grows with mobs that are not angry. The second put (a new highest anger time) is unchanged; the
 * returned previous value is discarded by getAngerTime in both cases. EMF is LGPL-3.0; no EMF code is carried.
 *
 * Ported to 1.21.1: the method and the map moved from EMFAnimationEntityContext (3.2.4) to EMFMath (3.3.9) with the same
 * body (state from emfState(), NeutralMob.getRemainingPersistentAngerTime, the same two puts in the same order) and the
 * same two readers; the map's anonymous class is EMFMath$1.
 */
@Mixin(value = EMFMath.class, remap = false)
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
