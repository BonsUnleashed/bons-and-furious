package bons.furious.patch.fallentraits_coo49;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch fallentraits_relic_modifier_unchanged_skip (Fallen Traits & Relics 1.0.0, LGPL-2.1-or-later;
 * server side in practice: the relics' curio ticks return on client levels). Ours. Idea: Collections Of Optimizations
 * (fallentraits.skipUnchangedRelicModifiers), idea text only.
 *
 * FuryOfInfernal, LawOfScale and TitansHeart call their private setOrRemoveModifier(wearer, attr, id, name, value, op) for
 * two or three attributes on every curio tick: getAttribute, then removeModifier(id) when one is held, then (value != 0)
 * addTransientModifier(new AttributeModifier(id, name, value, op)). With unchanged values that pair marks MAX_HEALTH, ARMOR,
 * ATTACK_SPEED, MOVEMENT_SPEED (players: an attribute packet each), crit and size attributes dirty twice per tick and posts
 * Apothic Attributes' value-change event pairs.
 *
 * unchanged() answers whether value != 0 and the instance already holds a TRANSIENT modifier with that id whose name, amount
 * (bit for bit) and operation equal the call's; then remove + add would leave the modifier maps equal, so the method returns
 * through its own early return for a missing instance. The permanent-set test is the attribute re-apply helper's
 * (bons.furious.patch.attribute_lookup.AttributeReapply.isPermanent, reached by a method handle; absent or failing: every
 * call runs as shipped). value == 0 (the removal path) always runs as shipped.
 *
 * Exact variant: re-applying an unchanged attribute modifier no longer marks the attribute dirty (no recompute, no attribute
 * sync packet, no value-change event pair); the held modifier is the old object (equal by UUID and in every field) and a
 * later recompute keeps the old order of the operation's modifier set.
 *
 * Flag: -Dbons_and_furious.fallentraitsRelicModifierUnchangedSkip=false.
 */
public final class FtRelics {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.fallentraitsRelicModifierUnchangedSkip", "true"));
    private static final MethodHandle IS_PERMANENT = find("isPermanent", MethodType.methodType(boolean.class, AttributeInstance.class, AttributeModifier.class));
    private static final MethodHandle AVAILABLE = find("available", MethodType.methodType(boolean.class));
    private static volatile boolean announced;

    private FtRelics() {
    }

    private static MethodHandle find(String name, MethodType type) {
        try {
            Class<?> c = Class.forName("bons.furious.patch.attribute_lookup.AttributeReapply", true, FtRelics.class.getClassLoader());
            return MethodHandles.lookup().findStatic(c, name, type);
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.warn("Bons and Furious: fallentraits_relic_modifier_unchanged_skip cannot reach the attribute re-apply helper ({}); the relics' modifiers are re-applied as shipped", e.toString());
            return null;
        }
    }

    /** True when setOrRemoveModifier(id, name, value, op) on {@code instance} would leave its modifiers as they are. */
    public static boolean unchanged(AttributeInstance instance, UUID id, String name, double value, AttributeModifier.Operation op) {
        if (!enabled || value == 0.0 || instance == null || id == null || op == null || IS_PERMANENT == null || AVAILABLE == null) return false;
        try {
            if (!(boolean) AVAILABLE.invokeExact()) return false;
            AttributeModifier held = instance.m_22111_(id);                              // getModifier(uuid)
            if (held == null || held.m_22217_() != op) return false;                     // getOperation
            if (Double.doubleToRawLongBits(held.m_22218_()) != Double.doubleToRawLongBits(value)) return false;    // getAmount
            if (!Objects.equals(held.m_22214_(), name)) return false;                    // getName
            if ((boolean) IS_PERMANENT.invokeExact(instance, held)) return false;        // the relics add transient modifiers
        } catch (Throwable t) {
            return false;
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: fallentraits_relic_modifier_unchanged_skip applies (Fallen Traits relics no longer re-apply unchanged attribute modifiers)");
        }
        return true;
    }
}
