package bons.furious.patch.tonsofenchants_coo49;

import bons.furious.mixin.tonsofenchants_coo49.ToeAttributeInstanceAccessor;
import com.deltajay.tonsofenchants.util.PU;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious group tonsofenchants_coo49 (Tons Of Enchants 1.0, MIT; both sides): the helper of its four switches.
 * Idea: Collections Of Optimizations (tonsofenchants.skipAbsentAttributeRemoval, tonsofenchants.leanAttributeLookup,
 * tonsofenchants.frostbiteSkipClient, tonsofenchants.singlePhasePlayerTick), idea text only; the code is ours.
 *
 * <ul>
 * <li>tonsofenchants_absent_modifier_removal_skip (exact variant): PU.AttributeHelper.removeModifier ends in
 *   AttributeInstance.removeModifier(modifier), which always ends in setDirty: the value is marked for a recompute, and the
 *   change notice puts a client-synced attribute such as movement speed into the entity's attribute set that the server
 *   sends to the entity and every player tracking it. Slowness, Swimmer's Boots and Enhanced Handling call it every player
 *   tick, on both sides, for every player WITHOUT the enchantment, so the modifier is never there. {@link #removeIsNoOp}
 *   answers "the removal would change no modifier": no modifier with its UUID is in the instance (getModifier(uuid)), it
 *   is not in the set of its operation (asked through AttributeInstance.getModifiers(op), which creates that empty set
 *   exactly as the original removal's first call does) and not in the permanent set (read through
 *   ToeAttributeInstanceAccessor: a removal by a same-UUID modifier of another class can leave a permanent modifier there
 *   without its map entry). Then the removal is left out and only setDirty's first half is done: the recompute mark (also
 *   through the accessor), so every value is worked out as before, even after another mod put a modifier straight into an
 *   operation set without marking the attribute. Only the change notice goes: no attribute packet. Instances of another
 *   class, an instance without the accessor, a modifier without an id or an operation: the original runs.</li>
 * <li>tonsofenchants_attribute_lookup_once (exact): PU.AttributeHelper.getPlayerCurrentAttribute asks
 *   entity.getAttribute(attribute) three times for one pair (the instance, its value or 0.0). {@link #currentAttribute}
 *   asks once and builds the same pair: AttributeMap's lookup returns the same instance on every call (the first call
 *   creates it when the entity's attribute supplier has the attribute; then it is in the map), and null on every call when
 *   it has not.</li>
 * <li>tonsofenchants_frostbite_server_only (exact): Frostbite.doWhenFrostbit scans every entity within 10 blocks (3
 *   vertically) of every player on every player tick, on both sides, for the persistent "Frostbitten" flag that only the
 *   enchantment's post-attack hook sets, and that runs only where the hit is dealt: on the server. Client entities never
 *   carry the flag, so on the client the scan can only create empty client-side ForgeData compounds. The config check
 *   stays first (it is before the call the switch wraps); then a player on a client level skips the scan.</li>
 * <li>tonsofenchants_player_tick_end_only (change, off by default): the 17 PlayerTickEvent listeners of the mod check no
 *   phase, so each runs at the start and at the end of every player tick. With the switch on, the start phase returns at
 *   once: each listener runs once per tick, at the end.</li>
 * </ul>
 * Runtime flags: -Dbons_and_furious.tonsofenchantsAbsentModifierRemovalSkip / .tonsofenchantsAttributeLookupOnce /
 * .tonsofenchantsFrostbiteServerOnly / .tonsofenchantsPlayerTickEndOnly =false.
 */
public final class ToeLean {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean removalSkip = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.tonsofenchantsAbsentModifierRemovalSkip", "true"));
    public static volatile boolean lookupOnce = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.tonsofenchantsAttributeLookupOnce", "true"));
    public static volatile boolean frostbiteServerOnly = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.tonsofenchantsFrostbiteServerOnly", "true"));
    public static volatile boolean tickEndOnly = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.tonsofenchantsPlayerTickEndOnly", "true"));
    private static volatile boolean announcedRemoval, announcedLookup, announcedFrostbite, announcedTick;

    private ToeLean() {
    }

    /**
     * True when instance.removeModifier(modifier) would change no modifier (see the class comment); the instance then has
     * the recompute mark the original's setDirty gives it, without the change notice.
     */
    public static boolean removeIsNoOp(AttributeInstance instance, AttributeModifier modifier) {
        if (!removalSkip || instance == null || modifier == null || instance.getClass() != AttributeInstance.class
                || !(instance instanceof ToeAttributeInstanceAccessor raw)) return false;
        AttributeModifier.Operation op = modifier.m_22217_();
        UUID id = modifier.m_22209_();
        if (op == null || id == null || instance.m_22111_(id) != null) return false;
        Set<AttributeModifier> set = instance.m_22104_(op);         // getModifiers(op): what the original's first call does
        if (set.contains(modifier) || raw.bons$tonsofenchantsCoo49Permanent().contains(modifier)) return false;
        raw.bons$tonsofenchantsCoo49Dirty(true);
        if (!announcedRemoval) {
            announcedRemoval = true;
            LOGGER.info("Bons and Furious: tonsofenchants_absent_modifier_removal_skip applies (Tons Of Enchants no longer reports an attribute changed when it removes a modifier the player does not have)");
        }
        return true;
    }

    /** The pair getPlayerCurrentAttribute builds, with one attribute lookup instead of three. */
    public static PU.OptionalPair<AttributeInstance, Double> currentAttribute(LivingEntity entity, Attribute attribute) {
        AttributeInstance instance = entity.m_21051_(attribute);
        if (!announcedLookup) {
            announcedLookup = true;
            LOGGER.info("Bons and Furious: tonsofenchants_attribute_lookup_once applies (Tons Of Enchants' attribute helper looks the attribute up once)");
        }
        return new PU.OptionalPair<>(instance, instance == null ? 0.0 : instance.m_22135_());
    }

    /** False when the Frostbite scan can be left out: the player is on a client level (see the class comment). */
    public static boolean frostbiteScan(LivingEntity player) {
        if (!frostbiteServerOnly || player == null) return true;
        Level level = player.m_9236_();
        if (level == null || !level.f_46443_) return true;
        if (!announcedFrostbite) {
            announcedFrostbite = true;
            LOGGER.info("Bons and Furious: tonsofenchants_frostbite_server_only applies (the Frostbite entity scan runs on the server only)");
        }
        return false;
    }

    /** True when a listener must return at once: the switch is on and this is the START phase of the player tick. */
    public static boolean skipStartPhase(TickEvent.PlayerTickEvent event) {
        if (!tickEndOnly || event == null || event.phase != TickEvent.Phase.START) return false;
        if (!announcedTick) {
            announcedTick = true;
            LOGGER.info("Bons and Furious: tonsofenchants_player_tick_end_only applies (Tons Of Enchants' player tick enchantments run once per tick, at its end)");
        }
        return true;
    }
}
