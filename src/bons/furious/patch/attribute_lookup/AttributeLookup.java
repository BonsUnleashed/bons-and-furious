package bons.furious.patch.attribute_lookup;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch vanilla_attribute_instance_fast_lookup (Minecraft 1.20.1, both sides, since 1.0.49). Idea:
 * Collections Of Optimizations (vanilla.fastAttributeInstanceLookup; caelus.skipGroundedNonPlayers), idea text only. Ours
 * entirely: no Minecraft code is carried.
 *
 * AttributeMap.getInstance(Attribute) (every getAttribute / getAttributeValue of every living entity) is
 * attributes.computeIfAbsent(attribute, a -> supplier.createInstance(this::onAttributeModified, a)): a capturing lambda per
 * call, and for an attribute the entity type does not have, the lambda runs every time, builds a method reference,
 * asks the supplier, gets null and stores nothing. AttributeMapFastLookupMixin answers first:
 *  - attributes.get(attribute) non-null: that instance (computeIfAbsent returns the mapped non-null value without calling
 *    the function, on vanilla's HashMap and on Radium's Reference2ReferenceOpenHashMap alike);
 *  - else !supplier.hasAttribute(attribute) (containsKey on the supplier's map): null (the function would call
 *    createInstance, whose supplier map get answers null, so it returns null and computeIfAbsent stores nothing);
 *  - else the original computeIfAbsent (it creates and stores the instance, with Apothic's and every other hook on that
 *    path as before).
 * Same instance or null and the same map contents for every input. The only internal difference: a HashMap whose table
 * was never allocated stays unallocated until something is stored (computeIfAbsent resizes first), which no Map method
 * can observe.
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
    public static AttributeInstance shadow(Attribute attribute, AttributeInstance ours, AttributeInstance asked) {
        SHADOW_CHECKS.incrementAndGet();
        if (asked != ours && SHADOW_MISMATCHES.incrementAndGet() <= 20)
            LOGGER.warn("Bons and Furious: vanilla_attribute_instance_fast_lookup shadow mismatch for {}: fast {}, original {}", attribute, ours, asked);
        return asked;
    }
}
