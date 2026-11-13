package bons.furious.patch.attribute_lookup;

import bons.furious.guard.Declared;
import bons.furious.mixin.attribute_lookup.AttributeMapRefreshAccessor;
import bons.furious.mixin.attribute_lookup.MobEffectTemplatesAccessor;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.Objects;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch vanilla_effect_refresh_modifier_keep (Minecraft 1.20.1, Forge 47.4.28; server; since 1.0.49).
 * Idea: Collections Of Optimizations (dungeonsdelight.leanYamAura), idea text only; ours works for every effect refresh.
 *
 * LivingEntity.onEffectUpdated(instance, forced = true, source) (m_141973_: an effect re-added while the entity has it, e.g.
 * Dungeons Delight's Monster Yam giving every undead within 6 blocks Strength and Speed every tick, beacons, conduits, auras)
 * calls MobEffect.removeAttributeModifiers and addAttributeModifiers: per template a removeModifier and an
 * addPermanentModifier(new AttributeModifier(id, descriptionId + " " + amplifier, getAttributeModifierValue(amplifier,
 * template), operation)), each marking the attribute dirty (a recompute, a ClientboundUpdateAttributesPacket for syncable
 * attributes such as movement speed, and Apothic Attributes' value-event pair) although nothing changed.
 * LivingEntityEffectRefreshMixin skips both calls when the pair would change nothing (unchanged): the effect's class keeps
 * MobEffect's own addAttributeModifiers, removeAttributeModifiers and getAttributeModifierValue (PLAIN), and every template's
 * attribute instance already holds a PERMANENT modifier with the template's UUID and operation, exactly the name the add
 * would give and exactly (raw bits) the amount; an attribute without an instance must be one the entity cannot have (the
 * stock calls skip it too). Both wrapped calls ask the same pure question on the same state, so both run or both are skipped.
 * Every other hook on onEffectUpdated (Apothic Attributes, Relics) keeps running.
 *
 * Exact variant, the one difference: refreshing an effect at an unchanged amplifier no longer re-adds its attribute
 * modifiers (no recompute, no attribute sync packet, no value-event pair); the held modifier objects (equal in every field)
 * and their order in the operation's modifier set stay as they were. -Dbons_and_furious.vanillaEffectRefreshModifierKeep=false
 * runs both calls every time.
 */
public final class EffectRefreshKeep {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.vanillaEffectRefreshModifierKeep", "true"));
    /** Refreshes answered without the two calls (statistics; plain field, racy; counted at the add call). */
    public static long kept;
    private static volatile boolean announced;

    private static final MethodType MODIFIERS_SIG = MethodType.methodType(void.class, net.minecraft.world.entity.LivingEntity.class, AttributeMap.class, int.class),
            VALUE_SIG = MethodType.methodType(double.class, int.class, AttributeModifier.class);
    /**
     * True for MobEffect classes that keep MobEffect's own addAttributeModifiers (m_6385_), removeAttributeModifiers (m_6386_) and
     * getAttributeModifierValue (m_7048_). Each is asked through the JVM's resolution of that one method
     * (bons.furious.guard.Declared), not Class.getDeclaredMethods: the listing resolves the types of every method an effect class
     * declares, and on a dedicated server it threw for an effect class with a method naming a client-only class, after Forge's red
     * "invalid dist" ERROR line when that class is marked client-only (bug sweep 2026-11-12). An effect class that cannot be asked
     * runs both calls, as it did then.
     */
    static final ClassValue<Boolean> PLAIN = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return Declared.inherits(type, MobEffect.class, "m_6385_", MODIFIERS_SIG) && Declared.inherits(type, MobEffect.class, "m_6386_", MODIFIERS_SIG)
                    && Declared.inherits(type, MobEffect.class, "m_7048_", VALUE_SIG);
        }
    };

    private EffectRefreshKeep() {
    }

    /** True when removeAttributeModifiers + addAttributeModifiers(effect, map, amplifier) would leave every modifier as it is. */
    public static boolean unchanged(MobEffect effect, AttributeMap map, int amplifier) {
        if (!enabled || effect == null || map == null || !AttributeReapply.available()) return false;
        if (!(effect instanceof MobEffectTemplatesAccessor templatesAccess) || !(map instanceof AttributeMapRefreshAccessor raw)) return false;
        if (!PLAIN.get(effect.getClass())) return false;
        Map<Attribute, AttributeModifier> templates = templatesAccess.bons$refreshTemplates();
        Map<Attribute, AttributeInstance> instances = raw.bons$refreshInstances();
        AttributeSupplier supplier = raw.bons$refreshSupplier();
        if (templates == null || instances == null || supplier == null) return false;
        String id = null;
        for (Map.Entry<Attribute, AttributeModifier> e : templates.entrySet()) {
            Attribute attribute = e.getKey();
            AttributeModifier template = e.getValue();
            if (template == null) return false;
            AttributeInstance instance = instances.get(attribute);
            if (instance == null) {
                if (attribute == null || supplier.m_22258_(attribute)) return false;   // hasAttribute: the stock calls would create it
                continue;
            }
            AttributeModifier held = instance.m_22111_(template.m_22209_());           // getModifier(uuid)
            if (held == null || held.m_22217_() != template.m_22217_()) return false;    // getOperation
            double amount = effect.m_7048_(amplifier, template);                          // getAttributeModifierValue (MobEffect's own)
            if (Double.doubleToRawLongBits(held.m_22218_()) != Double.doubleToRawLongBits(amount)) return false;
            if (id == null) id = effect.m_19481_();                                       // getDescriptionId, as the add asks it
            if (!nameIs(held.m_22214_(), id, amplifier)) return false;
            if (!AttributeReapply.isPermanent(instance, held)) return false;
        }
        return true;
    }

    /** Counted once per skipped refresh (the add call). */
    public static void count() {
        kept++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_effect_refresh_modifier_keep applies (refreshing an effect at an unchanged amplifier keeps its attribute modifiers)");
        }
    }

    /** name equals id + " " + amplifier (what addAttributeModifiers builds), compared without building the string. */
    static boolean nameIs(String name, String id, int amplifier) {
        if (name == null) return false;
        if (id == null) return Objects.equals(name, "null " + amplifier);   // string concatenation of a null id (never in practice)
        int n = id.length();
        if (!name.startsWith(id) || name.length() <= n + 1 || name.charAt(n) != ' ') return false;
        return intAt(name, n + 1, amplifier);
    }

    /** s.substring(from) equals Integer.toString(v), without allocating. */
    static boolean intAt(String s, int from, int v) {
        long x = v;
        boolean neg = x < 0;
        if (neg) x = -x;
        int digits = 1;
        for (long t = x; t >= 10; t /= 10) digits++;
        if (s.length() - from != digits + (neg ? 1 : 0)) return false;
        int i = from;
        if (neg && s.charAt(i++) != '-') return false;
        long p = 1;
        for (int k = 1; k < digits; k++) p *= 10;
        for (; p > 0; p /= 10) {
            int d = (int) (x / p % 10);
            if (s.charAt(i++) != (char) ('0' + d)) return false;
        }
        return true;
    }
}
