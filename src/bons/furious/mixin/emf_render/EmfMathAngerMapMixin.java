package bons.furious.mixin.emf_render;

import bons.furious.patch.emf_render.EmfRender;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * emf_anger_map_prune for Entity Model Features 3.3.x (tested: 3.3.12, the newest 1.20.1 Forge build, and 3.3.10, the build
 * Collections Of Optimizations 5.8 compiles against; Minecraft 1.20.1 / Forge 47.4.28, client only). Fix. Since 1.0.49.
 *
 * EMF 3.3 moved the anger_time variable from EMFAnimationEntityContext (3.2.x, EmfAngerMapMixin) to the static
 * EMFMath.getAngerTime(), with the same leak: for a neutral mob that is not angry it does
 * knownHighestAngerTimeByUUID.put(uuid, 0), so every neutral mob ever drawn with a model that reads anger_time keeps an entry
 * (UUID + Integer + node) until the game closes. The map is private to EMFMath; its only readers are getAngerTime's and
 * getAngerTimeStart's getOrDefault(uuid, 0) and the map's own get override (getOrDefault(key, 0)), so an absent key and a key
 * mapped to 0 read the same.
 *
 * This wraps that first put (ordinal 0, the anger-ended branch) into remove(uuid): the same value for every later read, and
 * the map no longer grows with mobs that are not angry. The second put (a new highest anger time) is unchanged. A wrap rather
 * than the 3.2.x mixin's @Redirect, because Collections Of Optimizations 5.8 (emf.dropZeroAngerEntries) hooks this method too
 * and wrappers chain where two redirects of one call cannot both apply. Exactly one of the two anger mixins applies: EMFMath
 * does not exist in 3.2.x and EMFAnimationEntityContext does not exist in 3.3.x; the key's guard accepts 3.2.4 (tested) or
 * the 3.3.x builds (alternative fingerprints emf-3.3.10 / emf-3.3.12), and the group's plugin
 * (bons.furious.patch.emf_render.EmfRenderPlugin) offers Mixin only the mixin whose class the matched build has (@Pseudo
 * is a backstop only). require = 0: if another mod already replaced the call, nothing is wrapped and EMF behaves as shipped.
 * Runtime flag: -Dbons_and_furious.emfAngerMapPrune=false (shared with the 3.2.x mixin, EmfRender.angerMapPrune).
 */
@Pseudo
@Mixin(targets = "traben.entity_model_features.models.animation.math.EMFMath", remap = false)
public abstract class EmfMathAngerMapMixin {
    @WrapOperation(method = "getAngerTime()F", require = 0, expect = 0, remap = false, at = @At(value = "INVOKE",
            target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", ordinal = 0, remap = false))
    private static Object bons$angerEndedMath(Map<Object, Object> map, Object uuid, Object zero, Operation<Object> original) {
        if (EmfRender.angerMapPrune) {
            EmfRender.announceAnger();
            return map.remove(uuid);
        }
        return original.call(map, uuid, zero);
    }
}
