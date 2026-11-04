package bons.furious.patch.attribute_lookup;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;
import java.util.Set;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ATTRIBUTE_REAPPLY helper (Minecraft 1.20.1; since 1.0.49): the public API for caller switches in other groups that
 * re-apply an attribute modifier every few ticks with removeModifier(uuid) + addPermanentModifier/addTransientModifier (L2
 * Hostility's TraitManager.addAttribute, Fallen Traits' relics, ...). Reached through a MethodHandle looked up by name.
 * Ours entirely; reads only.
 *
 * In 1.20.1 AttributeInstance.removeModifier(AttributeModifier) (m_22130_, reached from removeModifier(UUID) m_22120_ when
 * the UUID is present) calls setDirty() unconditionally, and adding calls it again: the cached value is recomputed, the
 * instance joins the AttributeMap's dirty set (a ClientboundUpdateAttributesPacket for syncable attributes) and Apothic
 * Attributes posts an AttributeChangedValueEvent drop and rise (its AttributeMap mixin on onAttributeModified). When the
 * instance already holds a modifier with the same UUID, name, amount (bit for bit) and operation, and the same permanence,
 * the pair leaves the modifier maps equal (keys, operation sets, permanent set: AttributeModifier.equals compares the UUID),
 * so a caller may skip it.
 *
 * Exact variant (the caller's properties wording): "re-applying an unchanged attribute modifier no longer marks the attribute
 * dirty (no recompute, no attribute sync packet, no value-change event pair)". The kept modifier is the old object (equal by
 * UUID and in every field), and a later recompute keeps the old iteration order of the operation's modifier set (a remove
 * and add can move a modifier inside a hash bucket, which can change the last bit of a sum of three or more modifiers).
 */
public final class AttributeReapply {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    /** AttributeInstance.permanentModifiers (f_22091_), or null when that field is not there (then holdsSame answers false). */
    private static final VarHandle PERMANENT = permanent();

    private AttributeReapply() {
    }

    private static VarHandle permanent() {
        try {
            return MethodHandles.privateLookupIn(AttributeInstance.class, MethodHandles.lookup()).findVarHandle(AttributeInstance.class, "f_22091_", Set.class);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Bons and Furious: the attribute re-apply helper stands down (no AttributeInstance.permanentModifiers field: {})", e.toString());
            return null;
        }
    }

    /** True when {@code held} (a modifier the instance holds) is in the instance's permanent set; false when unknown. */
    public static boolean isPermanent(AttributeInstance instance, AttributeModifier held) {
        if (instance == null || held == null || PERMANENT == null) return false;
        @SuppressWarnings("unchecked") Set<AttributeModifier> perm = (Set<AttributeModifier>) PERMANENT.get(instance);
        return perm != null && perm.contains(held);
    }

    /** False when the permanent-set field is missing (every check then answers "changed" and the stock calls run). */
    public static boolean available() {
        return PERMANENT != null;
    }

    /**
     * True when {@code instance} already holds a modifier with {@code modifier}'s UUID whose name, amount (bit for bit) and
     * operation equal {@code modifier}'s, and whose membership in the permanent set equals {@code permanent}. False for any
     * null argument.
     */
    public static boolean holdsSame(AttributeInstance instance, AttributeModifier modifier, boolean permanent) {
        if (instance == null || modifier == null || PERMANENT == null) return false;
        AttributeModifier current = instance.m_22111_(modifier.m_22209_());          // getModifier(uuid)
        if (current == null) return false;
        if (current.m_22217_() != modifier.m_22217_()) return false;                 // getOperation
        if (Double.doubleToRawLongBits(current.m_22218_()) != Double.doubleToRawLongBits(modifier.m_22218_())) return false;   // getAmount
        if (!Objects.equals(current.m_22214_(), modifier.m_22214_())) return false;  // getName
        @SuppressWarnings("unchecked") Set<AttributeModifier> perm = (Set<AttributeModifier>) PERMANENT.get(instance);
        return perm != null && perm.contains(current) == permanent;
    }
}
