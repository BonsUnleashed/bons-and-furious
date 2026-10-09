package bons.furious.mixin.lionfish_models;

import bons.furious.patch.lionfish_models.LionfishAnimatorReuse;
import com.github.L_Ender.lionfishapi.client.model.Animations.ModelAnimator;
import com.github.L_Ender.lionfishapi.client.model.Transform;
import com.github.L_Ender.lionfishapi.client.model.tools.AdvancedModelBox;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * lionfish_model_animator (Lionfish API 2.8, LGPL-3.0: no Lionfish code is carried; client, since 1.0.36).
 *
 * ModelAnimator keeps the keyframe transforms of the current and the previous keyframe in two HashMaps. Every
 * endKeyframe() of a playing animation runs prevTransformMap.clear(); prevTransformMap.putAll(transformMap);
 * transformMap.clear() (a new map node per animated part), and every getTransform(box) of the next keyframe makes a new
 * Transform. Four MixinExtras @WrapOperations make the same maps hold the same values without that churn:
 *  - putAll in endKeyframe: the two maps trade places (prevTransformMap becomes the map that holds exactly the entries
 *    putAll would copy; transformMap becomes the old previous map, which the stock code has just cleared and clears again);
 *  - the clear() of the previous map in endKeyframe and both clear() calls in update(): the Transforms they drop (held by
 *    no other map: after a swap a Transform is in one map only) go to a small per-animator spare list first;
 *  - computeIfAbsent in getTransform: a missing box gets a spare Transform reset to zero (resetRotation + resetOffset:
 *    the six floats a new Transform starts with) instead of a new one.
 * The loops in endKeyframe add each box's transform to that box only, so the iteration order of the maps cannot change
 * any result. See LionfishAnimatorReuse for the switch and the shadow mode.
 */
@Mixin(value = ModelAnimator.class, remap = false)
public abstract class ModelAnimatorReuseMixin {
    @Shadow
    private HashMap<AdvancedModelBox, Transform> transformMap;

    @Shadow
    private HashMap<AdvancedModelBox, Transform> prevTransformMap;

    @Unique
    private final ArrayDeque<Transform> bons$spare = new ArrayDeque<>();

    @WrapOperation(method = "endKeyframe(Z)V", at = @At(value = "INVOKE", target = "Ljava/util/HashMap;putAll(Ljava/util/Map;)V"))
    private void bons$tradeMaps(HashMap<AdvancedModelBox, Transform> prev, Map<AdvancedModelBox, Transform> current, Operation<Void> original) {
        if (!LionfishAnimatorReuse.enabled || prev != this.prevTransformMap || current != this.transformMap || !prev.isEmpty()) {
            original.call(prev, current);
            return;
        }
        this.prevTransformMap = this.transformMap;
        this.transformMap = prev;
    }

    @WrapOperation(method = "endKeyframe(Z)V", at = @At(value = "INVOKE", target = "Ljava/util/HashMap;clear()V", ordinal = 0))
    private void bons$keepDroppedPrevious(HashMap<AdvancedModelBox, Transform> map, Operation<Void> original) {
        if (LionfishAnimatorReuse.enabled && map == this.prevTransformMap) LionfishAnimatorReuse.keep(this.bons$spare, map);
        original.call(map);
    }

    @WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Ljava/util/HashMap;clear()V"))
    private void bons$keepDroppedOnUpdate(HashMap<AdvancedModelBox, Transform> map, Operation<Void> original) {
        if (LionfishAnimatorReuse.enabled && (map == this.transformMap || map == this.prevTransformMap) && this.transformMap != this.prevTransformMap)
            LionfishAnimatorReuse.keep(this.bons$spare, map);
        original.call(map);
    }

    @WrapOperation(method = "getTransform", at = @At(value = "INVOKE",
            target = "Ljava/util/HashMap;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"))
    private Object bons$spareTransform(HashMap<AdvancedModelBox, Transform> map, Object box, Function<Object, Object> make, Operation<Object> original) {
        if (!LionfishAnimatorReuse.enabled || this.bons$spare.isEmpty()) return original.call(map, box, make);
        return LionfishAnimatorReuse.transform(map, (AdvancedModelBox) box, this.bons$spare, make, original);
    }
}
