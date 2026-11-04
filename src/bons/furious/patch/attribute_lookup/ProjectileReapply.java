package bons.furious.patch.attribute_lookup;

import bons.furious.mixin.attribute_lookup.AttributeMapReapplyAccessor;
import com.google.common.collect.Multimap;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch projectiledamage_weapon_modifier_unchanged_skip (Projectile Damage 3.2.3+1.20.1, GPL-3.0; server;
 * since 1.0.49). Idea: Collections Of Optimizations (projectiledamage.skipAppliedWeaponModifiers), idea text only.
 *
 * Projectile Damage's ServerWorldMixin.pre_spawnEntity (HEAD of ServerLevel.addFreshEntity) re-applies the shooter's
 * weapon modifiers for every arrow it spawns: AttributeMap.addTransientAttributeModifiers(stack.getAttributeModifiers(slot)),
 * i.e. per modifier removeModifier + addTransientModifier, each marking the attribute dirty (a recompute, the attribute in the
 * next ClientboundUpdateAttributesPacket when it is syncable, and Apothic Attributes' value-event pair), although the
 * weapon's equipment change applied the same modifiers already (multishot: three times per shot).
 * ProjectileDamageReapplyMixin skips that one call when it would change nothing: for every (attribute, modifier) entry the
 * attribute's instance already holds a TRANSIENT modifier with that UUID and exactly that name, amount (raw bits) and
 * operation (AttributeReapply.holdsSame), no UUID repeats within one attribute, and an attribute without an instance is one
 * the entity cannot have (the stock call skips it too; one the supplier has but not yet created runs the stock call).
 * Any mismatch runs the stock call for the whole multimap.
 *
 * Exact variant, the one difference: re-applying unchanged weapon modifiers no longer marks the attributes dirty (no
 * recompute, no attribute sync packet, no value-event pair); the held modifier objects (equal in every field) and their
 * order in the operation's modifier set stay as they were. -Dbons_and_furious.projectiledamageWeaponModifierUnchangedSkip=
 * false runs the stock call every time.
 */
public final class ProjectileReapply {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.projectiledamageWeaponModifierUnchangedSkip", "true"));
    /** Re-applies answered without the stock call (statistics for probes; plain field, racy). */
    public static long skipped;
    private static volatile boolean announced;

    private ProjectileReapply() {
    }

    /** True when addTransientAttributeModifiers(mods) on {@code map} would leave every modifier as it is. */
    public static boolean unchanged(AttributeMap map, Multimap<Attribute, AttributeModifier> mods) {
        if (!enabled || map == null || mods == null || !AttributeReapply.available()) return false;
        if (!(map instanceof AttributeMapReapplyAccessor raw)) return false;
        Map<Attribute, AttributeInstance> instances = raw.bons$reapplyInstances();
        AttributeSupplier supplier = raw.bons$reapplySupplier();
        if (instances == null || supplier == null) return false;
        for (Map.Entry<Attribute, Collection<AttributeModifier>> e : mods.asMap().entrySet()) {
            Attribute attribute = e.getKey();
            AttributeInstance instance = instances.get(attribute);
            if (instance == null) {
                if (attribute == null || supplier.m_22258_(attribute)) return false;   // hasAttribute: the stock call would create it
                continue;                                                             // the stock call skips this entry too
            }
            Collection<AttributeModifier> list = e.getValue();
            if (list.size() > 1 && repeats(list)) return false;
            for (AttributeModifier m : list) if (!AttributeReapply.holdsSame(instance, m, false)) return false;
        }
        skipped++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: projectiledamage_weapon_modifier_unchanged_skip applies (unchanged weapon modifiers are no longer re-applied per arrow)");
        }
        return true;
    }

    private static boolean repeats(Collection<AttributeModifier> list) {
        UUID[] seen = new UUID[list.size()];
        int n = 0;
        for (AttributeModifier m : list) {
            UUID id = m == null ? null : m.m_22209_();
            if (id == null) return true;
            for (int i = 0; i < n; i++) if (seen[i].equals(id)) return true;
            seen[n++] = id;
        }
        return false;
    }
}
