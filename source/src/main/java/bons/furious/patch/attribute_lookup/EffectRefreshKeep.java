package bons.furious.patch.attribute_lookup;

import bons.furious.mixin.attribute_lookup.AttributeMapRefreshAccessor;
import bons.furious.mixin.attribute_lookup.MobEffectTemplatesAccessor;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch vanilla_effect_refresh_modifier_keep (Minecraft 1.21.1 on NeoForge 21.1.252; server; since
 * 1.0.49). Idea: Collections Of Optimizations (dungeonsdelight.leanYamAura), idea text only; ours works for every effect
 * refresh.
 *
 * LivingEntity.onEffectUpdated(instance, forced = true, source) (an effect re-added while the entity has it, e.g. Dungeons
 * Delight's Monster Yam giving every undead around it Strength and Speed every tick, beacons, conduits, auras) calls
 * MobEffect.removeAttributeModifiers(map) and addAttributeModifiers(map, amplifier): per template a
 * removeModifier(template.id()) and an addPermanentModifier(template.create(amplifier)), each marking the attribute dirty
 * (a recompute, a ClientboundUpdateAttributesPacket for syncable attributes such as movement speed), and then
 * refreshDirtyAttributes, which runs onAttributeUpdated for every dirty attribute, although nothing changed.
 * LivingEntityEffectRefreshMixin skips both calls when the pair would change nothing (unchanged): the effect's class keeps
 * MobEffect's own addAttributeModifiers and removeAttributeModifiers (PLAIN), no template has a NeoForge amplifier curve,
 * every template's attribute is a plain holder (AttributeReapply.plainHolder), and every template's attribute instance
 * already holds the modifier with the template's id and operation and exactly (raw bits) the amount create(amplifier)
 * gives (amount * (amplifier + 1)), as the very object its permanent-modifier map holds under that id; an attribute without
 * an instance must be one the entity type cannot have (the stock calls skip it too; for one it can have they would create
 * the instance). Both wrapped calls ask the same pure question on the same state, so both run or both are skipped. Every
 * other hook on onEffectUpdated (Iron's Spellbooks, Relics, Simply Swords) keeps running.
 *
 * Exact variant, the one difference: refreshing an effect at an unchanged amplifier no longer re-adds its attribute
 * modifiers (no recompute, no attribute sync packet; refreshDirtyAttributes, which still runs, does not see those
 * attributes, so onAttributeUpdated does not compare health and absorption with their unchanged maximum again: every
 * change of either maximum marks its instance dirty, and a dirty instance stays in the set until a refreshDirtyAttributes
 * call handles it, at the latest the one at the end of LivingEntity.tick); the held modifier objects (equal in every
 * field) and their order in the instance's maps stay as they were.
 * -Dbons_and_furious.vanillaEffectRefreshModifierKeep=false runs both calls every time.
 */
public final class EffectRefreshKeep {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.vanillaEffectRefreshModifierKeep", "true"));
    /** Refreshes answered without the two calls (statistics; plain field, racy; counted at the add call). */
    public static long kept;
    private static volatile boolean announced;

    /** True for MobEffect classes that keep MobEffect's own addAttributeModifiers and removeAttributeModifiers. */
    static final ClassValue<Boolean> PLAIN = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> k = type; k != null && k != MobEffect.class; k = k.getSuperclass()) {
                try {
                    for (java.lang.reflect.Method m : k.getDeclaredMethods()) {
                        String n = m.getName();
                        if (n.equals("addAttributeModifiers") || n.equals("removeAttributeModifiers")) return Boolean.FALSE;
                    }
                } catch (Throwable t) {
                    return Boolean.FALSE;
                }
            }
            return Boolean.TRUE;
        }
    };

    private EffectRefreshKeep() {
    }

    /** True when removeAttributeModifiers(map) + addAttributeModifiers(map, amplifier) of {@code effect} would leave every modifier as it is. */
    public static boolean unchanged(MobEffect effect, AttributeMap map, int amplifier) {
        if (!enabled || effect == null || map == null || !AttributeReapply.available()) return false;
        if (!(effect instanceof MobEffectTemplatesAccessor templatesAccess) || !(map instanceof AttributeMapRefreshAccessor raw)) return false;
        if (!PLAIN.get(effect.getClass())) return false;
        Map<Holder<Attribute>, MobEffect.AttributeTemplate> templates = templatesAccess.bons$refreshTemplates();
        Map<Holder<Attribute>, AttributeInstance> instances = raw.bons$refreshInstances();
        AttributeSupplier supplier = raw.bons$refreshSupplier();
        if (templates == null || instances == null || supplier == null) return false;
        if (templates instanceof Object2ObjectMap<Holder<Attribute>, MobEffect.AttributeTemplate> fast) {
            ObjectIterator<Object2ObjectMap.Entry<Holder<Attribute>, MobEffect.AttributeTemplate>> it = Object2ObjectMaps.fastIterator(fast);
            while (it.hasNext()) {
                Object2ObjectMap.Entry<Holder<Attribute>, MobEffect.AttributeTemplate> e = it.next();
                if (!holds(e.getKey(), e.getValue(), instances, supplier, amplifier)) return false;
            }
            return true;
        }
        for (Map.Entry<Holder<Attribute>, MobEffect.AttributeTemplate> e : templates.entrySet()) {
            if (!holds(e.getKey(), e.getValue(), instances, supplier, amplifier)) return false;
        }
        return true;
    }

    /** One template: the stock pair would leave its attribute's modifiers as they are. */
    private static boolean holds(Holder<Attribute> attribute, MobEffect.AttributeTemplate template, Map<Holder<Attribute>, AttributeInstance> instances,
                                 AttributeSupplier supplier, int amplifier) {
        if (template == null || template.curve() != null || !AttributeReapply.plainHolder(attribute)) return false;
        AttributeInstance instance = instances.get(attribute);
        if (instance == null) return !supplier.hasAttribute(attribute);                      // hasAttribute: the stock calls would create it
        AttributeModifier held = instance.getModifier(template.id());                        // getModifier(id)
        if (held == null || held.operation() != template.operation()) return false;
        double amount = template.amount() * (double) (amplifier + 1);                        // AttributeTemplate.create without a curve
        if (Double.doubleToRawLongBits(held.amount()) != Double.doubleToRawLongBits(amount)) return false;
        return AttributeReapply.isPermanent(instance, held);
    }

    /** Counted once per skipped refresh (the add call). */
    public static void count() {
        kept++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_effect_refresh_modifier_keep applies (refreshing an effect at an unchanged amplifier keeps its attribute modifiers)");
        }
    }
}
