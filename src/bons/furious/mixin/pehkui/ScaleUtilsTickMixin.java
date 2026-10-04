package bons.furious.mixin.pehkui;

import bons.furious.patch.pehkui.PehkuiScaleTick;
import java.util.Collection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import virtuoel.pehkui.api.ScaleData;
import virtuoel.pehkui.api.ScaleEventCallback;
import virtuoel.pehkui.api.ScaleType;
import virtuoel.pehkui.util.ScaleUtils;

/**
 * pehkui_scale_tick_callbacks (Pehkui 3.8.2+1.20.1-forge, MIT, both sides).
 *
 * ScaleUtils.tickScale(data) runs once per scale type per entity per tick. It was
 *   type.getPreTickEvent().forEach(s -> s.onEvent(data)); data.tick(); type.getPostTickEvent().forEach(s -> s.onEvent(data));
 * so it built two capturing lambdas per call; the lambda reaches the megamorphic accept call inside ArrayList.forEach and
 * is allocated even though both lists are empty unless an addon registers a callback.
 *
 * The body below is Pehkui's, with each forEach (and the lambda it needs) run only when its list is non-empty. Why the
 * result is identical: ScaleType's callback lists are private final ArrayLists created in its (private) constructor,
 * and ArrayList.forEach on an empty list calls no callback and throws nothing; a non-empty list runs the original call
 * with the same lambda, in the same order (pre-tick list, data.tick(), post-tick list), each list read once, as before.
 * With the runtime switch off both lists are always passed to forEach, which is the original method.
 */
@Mixin(value = ScaleUtils.class, remap = false)
public abstract class ScaleUtilsTickMixin {
    /**
     * @author BonsUnleashed
     * @reason Build the callback lambdas only for a non-empty callback list (pehkui_scale_tick_callbacks).
     */
    @Overwrite
    public static void tickScale(ScaleData data) {
        boolean lean = PehkuiScaleTick.enabled;
        ScaleType type = data.getScaleType();
        Collection<ScaleEventCallback> pre = type.getPreTickEvent();
        if (!lean || !pre.isEmpty()) {
            pre.forEach(s -> s.onEvent(data));
        }
        data.tick();
        Collection<ScaleEventCallback> post = type.getPostTickEvent();
        if (!lean || !post.isEmpty()) {
            post.forEach(s -> s.onEvent(data));
        }
    }
}
