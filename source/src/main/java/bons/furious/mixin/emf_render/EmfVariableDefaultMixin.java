package bons.furious.mixin.emf_render;

import bons.furious.patch.emf_render.EmfRender;
import java.util.HashMap;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import traben.entity_model_features.models.animation.math.EMFMath;
import traben.entity_model_features.models.animation.state.EMFEntityRenderState;

/**
 * emf_variable_default_unboxed (tested build: Entity Model Features 3.3.9 for NeoForge 1.21.1,
 * entity_model_features-3.3.9-1.21-neoforge.jar, on Minecraft 1.21.1 / NeoForge 21.1.252, client only; first written for
 * EMF 3.2.4 on Minecraft 1.20.1 / Forge 47.4.16).
 *
 * EMFMath.getEntityVariable(name, default) is
 * {@code state == null ? default : state.variableMap().getOrDefault(name, Float.valueOf(default)).floatValue()} with
 * {@code state = emfState()}: every read of an entity variable from an animation (ModelVariableFactory's var.* and varb.*
 * suppliers call it per read) boxes its default value first, read or not.
 *
 * Overwritten (EMF is LGPL-3.0: this is a modified copy of EMF's method, distributed with its source; the original
 * expression is kept as the fallback) so that, for the HashMap EMF keeps per entity (MixinEntity: the map and its GUI
 * copy) and per block entity (MixinBlockEntity), the lookup passes a private sentinel as the default instead: a stored
 * non-null Float is returned unboxed, the sentinel back means "absent" and the default is returned as is. Everything else
 * evaluates the original expression on the same map object - another map class, a key stored with a null value (whose
 * unboxing throws the same NullPointerException with the same message) - and a null map evaluates the original expression
 * itself (the same exception and message). Why identical: HashMap.getOrDefault returns the stored value for a present key
 * and the given default otherwise, without storing or inspecting the default; the sentinel never enters any map; emfState()
 * and variableMap() are each called once, in the original order.
 *
 * Ported to 1.21.1: the method moved from EMFAnimationEntityContext (3.2.4, a static emfState field) to EMFMath (3.3.9,
 * a local from the private static emfState(), i.e. EMFState.state()); same expression, same suppliers, same map providers.
 * The fallback now reuses the map it already fetched (3.3.9 calls variableMap() once), except for a null map.
 */
@Mixin(value = EMFMath.class, remap = false)
public abstract class EmfVariableDefaultMixin {
    @Shadow(remap = false)
    private static EMFEntityRenderState emfState() {
        throw new AssertionError();
    }

    /**
     * @author Bons and Furious (emf_variable_default_unboxed)
     * @reason read entity variables without boxing the default value; the original expression is the fallback
     */
    @Overwrite(remap = false)
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static float getEntityVariable(String variable, float defaultValue) {
        EMFEntityRenderState state = emfState();
        if (state == null) return defaultValue;
        if (EmfRender.variableDefaults) {
            Map<String, Float> map = state.variableMap();
            if (map != null) {
                if (map.getClass() == HashMap.class) {
                    Object v = ((Map) map).getOrDefault(variable, EmfRender.ABSENT);
                    if (v == EmfRender.ABSENT) {
                        EmfRender.announceVariables();
                        return defaultValue;
                    }
                    if (v != null) return (Float) v;
                }
                return map.getOrDefault(variable, defaultValue);
            }
        }
        return state.variableMap().getOrDefault(variable, defaultValue);
    }
}
