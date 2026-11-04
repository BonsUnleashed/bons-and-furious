package bons.furious.patch.puffishattributes_coo49;

import bons.furious.mixin.puffishattributes_coo49.PuffishModifierMapAccessor;
import java.util.List;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.puffish.attributesmod.mixin.EntityAttributeInstanceInvoker;
import net.puffish.attributesmod.util.Signed;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch puffish_attributes_empty_modifier_fast_path (Pufferfish's Attributes 0.8.2, LGPL-3.0; both
 * sides). Idea: Collections Of Optimizations (puffishattributes.skipEmptyModifiers), idea text only; the code is ours.
 *
 * <p>Every hook of the mod (about 25: the stealth range in TargetingConditions.test for every target candidate, movement
 * speed, mining speed, jump, fall damage, healing, damage, armour, bows...) builds a DynamicModification, asks the entity
 * for one of the mod's attributes (entity.getAttribute: null when the entity's attribute supplier lacks it), wraps the
 * answer with its sign in a Signed and adds it to the modification's private list, then applyTo walks that list four
 * times: flat bonuses (ADDITION), percent of base (MULTIPLY_BASE), percent of total (MULTIPLY_TOTAL), each read through
 * the mod's own invoker of AttributeInstance.getModifiersOrEmpty (a plain getOrDefault), and finally the attribute's
 * sanitizeValue. Every pass skips entries whose instance is null.
 *
 * <p>{@link #allEmpty} + {@link #sanitizeChain}: when no entry has an ADDITION, MULTIPLY_BASE or MULTIPLY_TOTAL modifier,
 * the first three passes leave the value as it is, so the answer is the value passed through each entry's sanitizeValue
 * in list order, which is what the fourth pass computes. The test reads each instance's modifier map first
 * (PuffishModifierMapAccessor: a modifier there sends the call to the original after one read), then the three operation
 * sets through the mod's own invoker (what the passes read: a set can hold a modifier the map lacks). Any modifier, a null
 * entry or an entry the original would fail on: the original applyTo runs. Same results bit for bit, only pure reads of
 * the instances (no getModifiers computeIfAbsent), same exceptions. Runtime flag
 * -Dbons_and_furious.puffishAttributesEmptyModifierFastPath=false.
 */
public final class PuffishLean {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.puffishAttributesEmptyModifierFastPath", "true"));
    private static volatile boolean announced;

    private PuffishLean() {
    }

    /** True when every entry of the list has no ADDITION, MULTIPLY_BASE and MULTIPLY_TOTAL modifier (see the class comment). */
    public static boolean allEmpty(List<?> list) {
        if (!enabled) return false;
        for (int i = 0, n = list.size(); i < n; i++) {
            if (!(list.get(i) instanceof Signed<?> s)) return false;           // null or foreign: the original decides
            Object v = s.value();
            if (v == null) continue;
            if (!(v instanceof PuffishModifierMapAccessor raw) || !(v instanceof EntityAttributeInstanceInvoker inv)) return false;
            if (!raw.bons$puffishattributesCoo49ById().isEmpty()) return false;
            if (!inv.invokeGetModifiersByOperation(AttributeModifier.Operation.ADDITION).isEmpty()) return false;
            if (!inv.invokeGetModifiersByOperation(AttributeModifier.Operation.MULTIPLY_BASE).isEmpty()) return false;
            if (!inv.invokeGetModifiersByOperation(AttributeModifier.Operation.MULTIPLY_TOTAL).isEmpty()) return false;
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: puffish_attributes_empty_modifier_fast_path applies (Pufferfish's Attributes skips its modifier passes when no modifier is there)");
        }
        return true;
    }

    /** The fourth pass alone: initial through each non-null entry's sanitizeValue, in list order. */
    public static double sanitizeChain(List<?> list, double initial) {
        double result = initial;
        for (int i = 0, n = list.size(); i < n; i++) {
            Object v = ((Signed<?>) list.get(i)).value();
            if (v == null) continue;
            result = ((AttributeInstance) v).m_22099_().m_6740_(result);
        }
        return result;
    }
}
