package bons.furious.patch.attribute_lookup;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ATTRIBUTE_REAPPLY helper (Minecraft 1.21.1; since 1.0.49): the public API for caller switches in other groups that
 * re-apply an attribute modifier every few ticks with removeModifier(id) + addPermanentModifier/addTransientModifier,
 * reached through a MethodHandle looked up by name (isPermanent, available, holdsSame), and the holder test of the
 * switches that read AttributeMap's instance map directly (plainHolder). Ours entirely; reads only.
 *
 * In 1.21.1 AttributeInstance.removeModifier(ResourceLocation) calls setDirty() when the id is present, and addModifier
 * calls it again: the cached value is recomputed and the instance joins the AttributeMap's dirty sets (attributesToUpdate,
 * which LivingEntity.refreshDirtyAttributes reads, and for syncable attributes attributesToSync, a
 * ClientboundUpdateAttributesPacket). When the instance already holds a modifier with the same id, operation and amount
 * (bit for bit), and its permanent-modifier map holds that very object under the id (permanent re-apply) or nothing under
 * the id (transient re-apply), the pair leaves the modifier maps equal (AttributeModifier is a record of id, amount and
 * operation), so a caller may skip it.
 *
 * Exact variant (the caller's properties wording): "re-applying an unchanged attribute modifier no longer marks the
 * attribute dirty (no recompute, no attribute sync packet)". The kept modifier is the old object (equal in every field),
 * and the maps keep their order: a remove and add moves the modifier to the end of the Object2ObjectArrayMaps that hold the
 * modifiers by id and the permanent ones (getModifiers() and the saved modifier list), and can move it inside the
 * operation's Object2ObjectOpenHashMap, which can change the last bit of a sum of three or more modifiers.
 */
public final class AttributeReapply {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    /** AttributeInstance.permanentModifiers (id -> modifier), or null when that field is not there (then every check answers false). */
    private static final VarHandle PERMANENT = permanent();

    private AttributeReapply() {
    }

    private static VarHandle permanent() {
        try {
            return MethodHandles.privateLookupIn(AttributeInstance.class, MethodHandles.lookup()).findVarHandle(AttributeInstance.class, "permanentModifiers", Map.class);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Bons and Furious: the attribute re-apply helper stands down (no AttributeInstance.permanentModifiers field: {})", e.toString());
            return null;
        }
    }

    /**
     * True for the holder classes the game and NeoForge hand out for attributes: exactly Holder.Reference (registry entries),
     * Holder.Direct, or a NeoForge DeferredHolder. False for null and for every other class, for example another
     * Holder.Reference subclass such as Puffish Attributes' RegistryEntryReferenceWrapper, which Puffish's hook at the head of
     * AttributeMap.getInstance replaces by the holder it wraps before the map is read.
     */
    public static boolean plainHolder(Holder<?> holder) {
        if (holder == null) return false;
        Class<?> c = holder.getClass();
        return c == Holder.Reference.class || c == Holder.Direct.class || holder instanceof DeferredHolder<?, ?>;
    }

    /** True when the instance's permanent-modifier map holds {@code held} itself (a modifier the instance holds) under its id; false when unknown. */
    public static boolean isPermanent(AttributeInstance instance, AttributeModifier held) {
        if (instance == null || held == null || PERMANENT == null) return false;
        @SuppressWarnings("unchecked") Map<ResourceLocation, AttributeModifier> perm = (Map<ResourceLocation, AttributeModifier>) PERMANENT.get(instance);
        return perm != null && perm.get(held.id()) == held;
    }

    /** False when the permanent-map field is missing (every check then answers "changed" and the stock calls run). */
    public static boolean available() {
        return PERMANENT != null;
    }

    /**
     * True when {@code instance} already holds a modifier with {@code modifier}'s id whose amount (bit for bit) and operation
     * equal {@code modifier}'s, and its permanent-modifier map holds that held object under the id when {@code permanent},
     * nothing under the id otherwise. False for any null argument.
     */
    public static boolean holdsSame(AttributeInstance instance, AttributeModifier modifier, boolean permanent) {
        if (instance == null || modifier == null || PERMANENT == null) return false;
        AttributeModifier current = instance.getModifier(modifier.id());                            // getModifier(id)
        if (current == null || current.operation() != modifier.operation()) return false;
        if (Double.doubleToRawLongBits(current.amount()) != Double.doubleToRawLongBits(modifier.amount())) return false;
        @SuppressWarnings("unchecked") Map<ResourceLocation, AttributeModifier> perm = (Map<ResourceLocation, AttributeModifier>) PERMANENT.get(instance);
        if (perm == null) return false;
        return permanent ? perm.get(current.id()) == current : !perm.containsKey(current.id());
    }
}
