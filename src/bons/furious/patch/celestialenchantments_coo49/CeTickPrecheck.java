package bons.furious.patch.celestialenchantments_coo49;

import java.util.Collections;
import java.util.Map;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch celestialenchantments_tick_enchant_precheck (Celestial Enchantments 1.3.7, both sides). Celestial
 * Enchantments is LGPL-2.1; none of its code is carried. Ours, idea from Collections Of Optimizations
 * (celestialenchantments.skipUnenchantedTick and celestialenchantments.leanSlotEnchScan), idea text only.
 *
 * What Celestial Enchantments does: CEGeneralEventHandler.onLivingTick runs for every living entity on every tick on both
 * sides and, on every fourth tick (tickCount % 4 == 0), builds IEnchUtils.getAllEnch(entity) (a HashMap over all six
 * equipment slots: getEnch(entity, EquipmentSlot.values())) and calls its LivingTickEnch enchantments, then, per slot of
 * EquipmentSlot.values(), IEnchUtils.getEnch(entity, new EquipmentSlot[]{slot}) (an array and a HashMap each) and calls
 * its ToolTickEnch enchantments. merge adds an entry only for a stack whose isEnchanted() is true; a stack without a tag
 * is never enchanted.
 *
 * What the switch does, only past the handler's "% 4" test (the other three ticks are not touched), with three Redirects
 * (no wrapper, no callback object):
 *  - {@link #allEnch} (getAllEnch): when no slot's stack carries a tag, an empty map without the call (nothing could be
 *    in it); else the shipped call;
 *  - {@link #slots} (the EquipmentSlot.values() of the per-slot loop): no slots when the getAllEnch map of this call was
 *    empty (the per-slot maps are its parts, and nothing ran in between: no LivingTickEnch was called), else values();
 *  - {@link #slotEnch} (the per-slot getEnch): an empty map for a slot whose stack carries no tag, else the shipped call.
 * The calls the handler makes (which enchantments, levels, stacks, in order) are the same; only reads of getItemBySlot and
 * getTag are added (the pack's only hook on getItemBySlot, Better Combat's, only reads). "This call" is told by the event
 * object, one per handler call: the getAllEnch redirect leaves the event in a field when its map is empty and the slot
 * redirect compares that field with its own event, so another thread's handler call (the client's and the server's in
 * one game) can only make it say "not empty" (the shipped loop).
 *
 * Flag: -Dbons_and_furious.celestialenchantmentsTickEnchantPrecheck=false runs the handler as shipped.
 */
public final class CeTickPrecheck {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.celestialenchantmentsTickEnchantPrecheck", "true"));
    private static final EquipmentSlot[] SLOTS = EquipmentSlot.values();
    private static final EquipmentSlot[] NO_SLOTS = new EquipmentSlot[0];
    /** The event of the last handler call whose getAllEnch map was empty (benign race: see above). */
    private static Object emptyEvent;
    /** Fourth ticks without the getAllEnch call, per-slot loops left out and slots answered empty (plain counters for probes and the harness). */
    public static long skippedTicks, skippedLoops, emptySlots;
    private static volatile boolean announced;

    private CeTickPrecheck() {
    }

    /** Before the handler's getAllEnch(entity) on a fourth tick: true (an empty map without the call) when no slot's stack
     *  carries a tag. */
    public static boolean untaggedTick(LivingEntity entity) {
        if (!enabled) return false;
        for (EquipmentSlot s : SLOTS) if (entity.m_6844_(s).m_41783_() != null) return false;
        skippedTicks++;
        announce();
        return true;
    }

    /** After getAllEnch (or its empty answer): remembers {@code event} when the map is empty. */
    @SuppressWarnings("rawtypes")
    public static void allEnchDone(Object event, Map m) {
        emptyEvent = enabled && m != null && m.isEmpty() ? event : null;
    }

    @SuppressWarnings("rawtypes")
    public static Map none() {
        return Collections.emptyMap();
    }

    /** The per-slot loop's EquipmentSlot.values() of {@code event}'s call: none after an empty getAllEnch map. */
    public static EquipmentSlot[] slots(Object event) {
        if (enabled && event != null && emptyEvent == event) {
            emptyEvent = null;
            skippedLoops++;
            announce();
            return NO_SLOTS;
        }
        return EquipmentSlot.values();
    }

    /** The per-slot getEnch(entity, {slot}) call: true (an empty map) when that slot's stack carries no tag. */
    public static boolean slotEnch(LivingEntity entity, EquipmentSlot[] slots) {
        if (!enabled || slots == null || slots.length != 1 || entity.m_6844_(slots[0]).m_41783_() != null) return false;
        emptySlots++;
        return true;
    }

    private static void announce() {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: celestialenchantments_tick_enchant_precheck applies (Celestial Enchantments' tick scan skips entities with nothing for it)");
        }
    }
}
