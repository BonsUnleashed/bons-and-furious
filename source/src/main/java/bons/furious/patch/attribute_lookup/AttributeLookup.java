package bons.furious.patch.attribute_lookup;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch vanilla_attribute_instance_fast_lookup (Minecraft 1.21.1, both sides, since 1.0.49). Idea:
 * Collections Of Optimizations (vanilla.fastAttributeInstanceLookup), idea text only. Ours entirely: no Minecraft code is
 * carried.
 *
 * AttributeMap.getInstance(Holder<Attribute>) (every LivingEntity.getAttribute, MobEffect's modifier add and remove,
 * AttributeMap.addTransientAttributeModifiers) is attributes.computeIfAbsent(attribute, a ->
 * supplier.createInstance(this::onAttributeModified, a)): a capturing lambda per call, and for an attribute the entity
 * type does not have, the lambda runs every time, builds a method reference, asks the supplier, gets null and stores
 * nothing. The map in that field (vanilla's Object2ObjectOpenHashMap, or the Object2ReferenceOpenHashMap Radium 0.13.1
 * puts there) does not override computeIfAbsent(K, Function), so java.util.Map's default runs: get(key), the function
 * only when that answer is null, and nothing stored when the function answers null. AttributeMapFastLookupMixin answers
 * first:
 *  - attributes.get(attribute) non-null: that instance (what the default returns without calling the function);
 *  - else !supplier.hasAttribute(attribute) (containsKey on the supplier's map, whose values are never null): null (the
 *    function would call createInstance, whose map get answers null, so it returns null and nothing is stored);
 *  - else the original computeIfAbsent (it creates and stores the instance, with every other hook on that path as before).
 * Same instance or null and the same map contents for every input.
 *
 * Those answers read the maps with the argument exactly as the caller passed it, so they are given only for the holder
 * classes the game and NeoForge hand out for attributes (AttributeReapply.plainHolder: Holder.Reference itself,
 * Holder.Direct, NeoForge's DeferredHolder). Any other Holder class runs the original, so a hook at the head of
 * getInstance that replaces such a holder before the map is read (Puffish Attributes 0.8.3 unwraps its
 * RegistryEntryReferenceWrapper there) still sees the call.
 *
 * -Dbons_and_furious.vanillaAttributeInstanceFastLookup=false runs the original every time. .shadow=true (verification runs
 * only) also runs the original on every fast answer and counts non-identical answers in SHADOW_CHECKS / SHADOW_MISMATCHES.
 */
public final class AttributeLookup {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.vanillaAttributeInstanceFastLookup", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.vanillaAttributeInstanceFastLookup.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();

    static {
        // the class is initialised by the first attribute lookup through the mixin: one INFO line, nothing on the hot path
        LOGGER.info("Bons and Furious: vanilla_attribute_instance_fast_lookup applies (attribute lookups answer existing and impossible instances without computeIfAbsent){}",
                SHADOW ? " - shadow verification on" : "");
    }

    private AttributeLookup() {
    }

    /** Shadow check of a fast answer: the original's answer wins. */
    public static AttributeInstance shadow(Holder<Attribute> attribute, AttributeInstance ours, AttributeInstance asked) {
        SHADOW_CHECKS.incrementAndGet();
        if (asked != ours && SHADOW_MISMATCHES.incrementAndGet() <= 20)
            LOGGER.warn("Bons and Furious: vanilla_attribute_instance_fast_lookup shadow mismatch for {}: fast {}, original {}", attribute, ours, asked);
        return asked;
    }
}
