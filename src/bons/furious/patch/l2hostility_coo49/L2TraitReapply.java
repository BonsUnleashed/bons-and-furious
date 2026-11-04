package bons.furious.patch.l2hostility_coo49;

import dev.xkmc.l2library.util.math.MathHelper;
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
 * Bons and Furious switch l2hostility_trait_attribute_unchanged_skip (L2 Hostility 2.5.19 with L2 Library 2.5.5, both
 * treated as All Rights Reserved; server side in practice: the traits that call it tick on server levels). Ours; no L2 code
 * is carried. Idea: Collections Of Optimizations (fallentraits.skipUnchangedDayNightModifiers), idea text only.
 *
 * TraitManager.addAttribute(le, attr, name, factor, op) builds an AttributeModifier whose UUID L2 Library derives from the
 * name (MathHelper.getUUIDFromString), removes the held modifier of that UUID if any and adds the new one as permanent.
 * Traits call it again and again with the same values (Fallen Traits' Daywalker / Nightcrawler every 20 ticks, also at
 * amount 0); each call marks the attribute dirty twice: the value is recomputed, a ClientboundUpdateAttributesPacket goes
 * to trackers for syncable attributes, and Apothic Attributes posts a value-change event pair.
 *
 * unchanged() answers whether the instance already holds a PERMANENT modifier with that UUID whose name, amount (bit for
 * bit) and operation equal the call's; then the remove + add would leave the modifier maps equal, so the caller returns at
 * once (the same early return the method takes for a missing instance). The permanent-set test is the attribute re-apply
 * helper's (bons.furious.patch.attribute_lookup.AttributeReapply.isPermanent, reached by a method handle; absent or failing:
 * every call runs as shipped). The UUID comes from L2 Library's own MathHelper.getUUIDFromString and is kept per name in a
 * small table (the same name gives the same UUID).
 *
 * Exact variant: re-applying an unchanged attribute modifier no longer marks the attribute dirty (no recompute, no attribute
 * sync packet, no value-change event pair); the held modifier is the old object (equal by UUID and in every field) and a
 * later recompute keeps the old order of the operation's modifier set.
 *
 * Flag: -Dbons_and_furious.l2hostilityTraitAttributeUnchangedSkip=false.
 */
public final class L2TraitReapply {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.l2hostilityTraitAttributeUnchangedSkip", "true"));
    private static final MethodHandle IS_PERMANENT = find("isPermanent", MethodType.methodType(boolean.class, AttributeInstance.class, AttributeModifier.class));
    private static final MethodHandle AVAILABLE = find("available", MethodType.methodType(boolean.class));
    private static final int SLOTS = 64;
    private static final String[] NAMES = new String[SLOTS];
    private static final UUID[] IDS = new UUID[SLOTS];
    private static volatile boolean announced;

    private L2TraitReapply() {
    }

    private static MethodHandle find(String name, MethodType type) {
        try {
            Class<?> c = Class.forName("bons.furious.patch.attribute_lookup.AttributeReapply", true, L2TraitReapply.class.getClassLoader());
            return MethodHandles.lookup().findStatic(c, name, type);
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.warn("Bons and Furious: l2hostility_trait_attribute_unchanged_skip cannot reach the attribute re-apply helper ({}); TraitManager.addAttribute runs as shipped", e.toString());
            return null;
        }
    }

    /** L2 Library's UUID for {@code name}, kept per name (a small direct-mapped table; a miss asks L2 Library). */
    public static UUID idFor(String name) {
        int h = name.hashCode();
        int slot = (h ^ (h >>> 16)) & (SLOTS - 1);
        synchronized (IDS) {
            String n = NAMES[slot];
            if (n != null && (n == name || n.equals(name))) return IDS[slot];
        }
        UUID id = MathHelper.getUUIDFromString(name);
        synchronized (IDS) {
            NAMES[slot] = name;
            IDS[slot] = id;
        }
        return id;
    }

    /** True when addAttribute(name, factor, op) on {@code instance} would leave its modifiers as they are. */
    public static boolean unchanged(AttributeInstance instance, String name, double factor, AttributeModifier.Operation op) {
        if (!enabled || instance == null || name == null || op == null || IS_PERMANENT == null || AVAILABLE == null) return false;
        try {
            if (!(boolean) AVAILABLE.invokeExact()) return false;
            AttributeModifier held = instance.m_22111_(idFor(name));                     // getModifier(uuid)
            if (held == null || held.m_22217_() != op) return false;                     // getOperation
            if (Double.doubleToRawLongBits(held.m_22218_()) != Double.doubleToRawLongBits(factor)) return false;   // getAmount
            if (!Objects.equals(held.m_22214_(), name)) return false;                    // getName
            if (!(boolean) IS_PERMANENT.invokeExact(instance, held)) return false;
        } catch (Throwable t) {
            return false;
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: l2hostility_trait_attribute_unchanged_skip applies (L2 Hostility trait attributes already applied with the same values are not re-applied)");
        }
        return true;
    }
}
