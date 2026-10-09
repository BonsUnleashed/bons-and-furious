package bons.furious.mixin.emf_render;

import bons.furious.patch.emf_render.EmfRender;
import java.util.HashMap;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import traben.entity_model_features.models.animation.EMFAnimationEntityContext;
import traben.entity_model_features.models.animation.state.EMFEntityRenderState;

/**
 * emf_variable_default_unboxed (Entity Model Features 3.2.4 on Minecraft 1.20.1 / Forge 47.4.16, client only).
 *
 * EMFAnimationEntityContext.getEntityVariable(name, default) is
 * {@code emfState == null ? default : emfState.variableMap().getOrDefault(name, Float.valueOf(default))}: every read of an
 * entity variable from an animation boxes its default value first, read or not (a 64-mob profiling scene: 1.8 % self of
 * the render thread, the Float boxing 1.6 % of render-thread allocation).
 *
 * Overwritten (EMF is LGPL-3.0; the original expression is kept as the fallback) so that, for the HashMap EMF keeps per
 * entity (and its GUI copy), the lookup passes a private sentinel as the default instead: a stored non-null Float is
 * returned unboxed, the sentinel back means "absent" and the default is returned as is. Everything else - another map
 * class, a key stored with a null value (whose unboxing throws), no map - evaluates the original expression, which also
 * gives the original exception and message. Why identical: HashMap.getOrDefault returns the stored value for a present key
 * and the given default otherwise, without storing or inspecting the default; the sentinel never enters any map.
 */
@Mixin(value = EMFAnimationEntityContext.class, remap = false)
public abstract class EmfVariableDefaultMixin {
    @Shadow(remap = false)
    private static EMFEntityRenderState emfState;

    /**
     * @author Bons and Furious (emf_variable_default_unboxed)
     * @reason read entity variables without boxing the default value; the original expression is the fallback
     */
    @Overwrite(remap = false)
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static float getEntityVariable(String variable, float defaultValue) {
        if (emfState == null) return defaultValue;
        if (EmfRender.variableDefaults) {
            Map<String, Float> map = emfState.variableMap();
            if (map != null && map.getClass() == HashMap.class) {
                Object v = ((Map) map).getOrDefault(variable, EmfRender.ABSENT);
                if (v == EmfRender.ABSENT) {
                    EmfRender.announceVariables();
                    return defaultValue;
                }
                if (v != null) return (Float) v;
            }
        }
        return emfState.variableMap().getOrDefault(variable, defaultValue);
    }
}
