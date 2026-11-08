package bons.furious.patch.l2hostility_coo49;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch l2hostility_trait_attribute_unchanged_skip (L2 Hostility 3.0.18 for Minecraft 1.21.1, LGPL-2.1;
 * server side in practice: the traits that call it run on server levels). Ours; no L2 code is carried. Idea: Collections Of
 * Optimizations (fallentraits.skipUnchangedDayNightModifiers), idea text only.
 *
 * TraitManager.addAttribute(le, attr, name, factor, op) builds an AttributeModifier whose id is L2Hostility.loc(name)
 * (ResourceLocation l2hostility:name), removes the held modifier of that id if any and adds the new one as permanent.
 * Callers repeat it with the same values (trait set-up of a mob whose modifier is already there, re-initialisation and
 * copies to split mobs; on 1.20.1 Fallen Traits' Daywalker / Nightcrawler every 20 ticks, also at amount 0); each call marks
 * the attribute dirty twice: the value is recomputed, LivingEntity's attribute update hook runs for it, and a
 * ClientboundUpdateAttributesPacket goes to trackers for syncable attributes.
 *
 * unchanged() answers whether the instance already holds, under that id, a modifier whose amount (bit for bit) and
 * operation equal the call's and which is the very modifier the instance's permanent map holds under that id; then the
 * remove + add would leave the same modifiers with the same permanence, so the caller returns at once (the same early
 * return the method takes for a missing instance). The permanent map is read through a VarHandle on
 * AttributeInstance.permanentModifiers (absent or failing: every call runs as shipped). The id is kept per name in a small
 * direct-mapped table (L2Hostility.loc is ResourceLocation.fromNamespaceAndPath("l2hostility", name): the same name gives
 * an equal id); a name that is not a valid path makes unchanged() answer false, so the method runs and fails as shipped.
 *
 * Exact variant: re-applying an unchanged attribute modifier no longer marks the attribute dirty (no recompute, no attribute
 * update hook, no attribute sync packet, no change events of mods that watch the dirty callback); the held modifier is the
 * old object (an equal record) and keeps its place in the instance's id and permanent maps (the remove + add moves it to
 * their end, which a later recompute or save iterates).
 *
 * Flag: -Dbons_and_furious.l2hostilityTraitAttributeUnchangedSkip=false.
 */
public final class L2TraitReapply {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.l2hostilityTraitAttributeUnchangedSkip", "true"));
    private static final String NAMESPACE = "l2hostility";
    /** AttributeInstance.permanentModifiers (id -> modifier), or null when that field is not there. */
    private static final VarHandle PERMANENT = permanent();
    private static final int SLOTS = 64;
    private static final String[] NAMES = new String[SLOTS];
    private static final ResourceLocation[] IDS = new ResourceLocation[SLOTS];
    private static volatile boolean announced;

    private L2TraitReapply() {
    }

    private static VarHandle permanent() {
        try {
            return MethodHandles.privateLookupIn(AttributeInstance.class, MethodHandles.lookup()).findVarHandle(AttributeInstance.class, "permanentModifiers", Map.class);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Bons and Furious: l2hostility_trait_attribute_unchanged_skip cannot read AttributeInstance.permanentModifiers ({}); TraitManager.addAttribute runs as shipped", e.toString());
            return null;
        }
    }

    /** L2 Hostility's modifier id for {@code name}, kept per name (a small direct-mapped table; a miss builds it). */
    public static ResourceLocation idFor(String name) {
        int h = name.hashCode();
        int slot = (h ^ (h >>> 16)) & (SLOTS - 1);
        synchronized (IDS) {
            String n = NAMES[slot];
            if (n != null && (n == name || n.equals(name))) return IDS[slot];
        }
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(NAMESPACE, name);
        synchronized (IDS) {
            NAMES[slot] = name;
            IDS[slot] = id;
        }
        return id;
    }

    /** True when addAttribute(name, factor, op) on {@code instance} would leave its modifiers as they are. */
    public static boolean unchanged(AttributeInstance instance, String name, double factor, AttributeModifier.Operation op) {
        if (!enabled || instance == null || name == null || op == null || PERMANENT == null) return false;
        try {
            ResourceLocation id = idFor(name);
            AttributeModifier held = instance.getModifier(id);
            if (held == null || held.operation() != op) return false;
            if (Double.doubleToRawLongBits(held.amount()) != Double.doubleToRawLongBits(factor)) return false;
            Map<?, ?> permanent = (Map<?, ?>) PERMANENT.get(instance);
            if (permanent == null || permanent.get(id) != held) return false;
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
