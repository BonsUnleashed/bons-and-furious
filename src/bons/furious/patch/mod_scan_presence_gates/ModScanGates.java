package bons.furious.patch.mod_scan_presence_gates;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.entity.PartEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49 presence gates of group mod_scan_presence_gates (both sides; Minecraft 1.20.1 + Forge 47.4.28).
 * Helper of the group's mixins; no code of the target mods or of Minecraft is carried.
 *
 * Entity searches of mods whose result can only matter when an entity of one rare class exists:
 *   borninchaos_minion_scan_presence_gate   Born in Chaos 1.7.5: four item procedures run every tick for every carrier
 *                                           (BonescallerStaff / MissionaryHat / Pumpkinstaffa / SpiderBite ...Procedure.execute,
 *                                           both sides) search every Entity in a box of radius 5 / 2.5 / 6.5 / 3, sort the
 *                                           list by distance and tame the carrier's minions in it. The loop's only effect is
 *                                           for ControlledBabySkeletonEntity + ControlledSpiritualAssistantEntity (Bonescaller
 *                                           Staff), ControlledBabySkeletonEntity (Missionary Hat), MrPumpkinControlledEntity
 *                                           (Pumpkin Staff), BabySpiderControlledEntity (Spider Bite): for every other entity it
 *                                           does nothing, and the search's predicate (always true) and the sort's distance key
 *                                           have no side effect. While the level holds none of the site's classes, the search
 *                                           is marked UNUSED: its walk over the entity sections is left out, the call itself
 *                                           runs (Valkyrien Skies' box hooks, the profiler's "getEntities" count, Forge's part
 *                                           pass), and the loop finds nothing to act on, as it would in the full list.
 *   cataclysm_deepling_ride_presence_gate   L_Ender's Cataclysm 3.16 (3.31 identical) AbstractDeepling$RidingCoralssus: canUse
 *                                           (every other tick per Deepling, server) first searches Coralssus_Entity in its box
 *                                           +-15 (getClosestCoralssus_Entity), then answers false when the Deepling is a
 *                                           passenger, when nothing was found or when its moistness is 300 or less. (1) While
 *                                           the Deepling is a passenger or has moistness <= 300 the found Coralssus is never
 *                                           read: canUse marks that search UNUSED (the answer is false either way). (2) While
 *                                           the level holds no Coralssus_Entity the search (in canUse, start and tick) is
 *                                           marked EMPTY: its walk could visit no entity. isPassenger and getMoistness are
 *                                           plain reads; canUse calls them anyway.
 *   cnc_caribou_dash_presence_gate          Critters n' Crawlers 2.2.5 CaribouDashDamageProcedure (LivingTickEvent: every living
 *                                           entity, every tick, both sides) searches CaribouEntity in a 0.5 block box around the
 *                                           entity; while the level holds no CaribouEntity the search is answered as it ends
 *                                           when it finds nothing (the profiler count and a new empty list, see caribouScan).
 * vanilla_entity_class_count_layer counts those classes (registration bons_and_furious.mod_scan_presence_gates.entity_classes
 * .json; subclasses count with their class); with an instance anywhere in the level (or counts the layer is not sure of)
 * every search runs in full. Requires vanilla_entity_class_count_layer and vanilla_entity_walk_skip (reached through
 * EntityPresenceLink's method handles); without them every search runs in full.
 * Runtime flags (-D...=false turns one off): bons_and_furious.borninchaosMinionScanPresenceGate,
 * .cataclysmDeeplingRidePresenceGate, .cncCaribouDashPresenceGate.
 * SHADOW MODE: -Dbons_and_furious.modScanGates.shadow=true never marks; where a gate would act the original runs and is
 * checked (no entity of the site's classes in the Born in Chaos list, no non-part entity in an EMPTY search's list, canUse
 * false for a riding / dry Deepling); SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20.
 */
public final class ModScanGates {
    public static final int BIC_BONESCALLER = 0, BIC_MISSIONARY = 1, BIC_PUMPKIN = 2, BIC_SPIDER = 3, DEEPLING = 4, CARIBOU = 5;
    public static final String BABY_SKELETON = "net.mcreator.borninchaosv.entity.ControlledBabySkeletonEntity",
            SPIRITUAL_ASSISTANT = "net.mcreator.borninchaosv.entity.ControlledSpiritualAssistantEntity",
            MR_PUMPKIN = "net.mcreator.borninchaosv.entity.MrPumpkinControlledEntity",
            BABY_SPIDER = "net.mcreator.borninchaosv.entity.BabySpiderControlledEntity",
            CORALSSUS = "com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.Coralssus_Entity",
            CARIBOU_ENTITY = "net.imasillylittleguy.cnc.entity.CaribouEntity";
    /** Per site: the classes the site acts on (Born in Chaos) or searches (Deepling, Caribou). */
    static final String[][] CLASSES = {{BABY_SKELETON, SPIRITUAL_ASSISTANT}, {BABY_SKELETON}, {MR_PUMPKIN}, {BABY_SPIDER}, {CORALSSUS}, {CARIBOU_ENTITY}};
    static final String BIC_KEY = "borninchaos_minion_scan_presence_gate";
    static final String[] KEYS = {BIC_KEY, BIC_KEY, BIC_KEY, BIC_KEY, "cataclysm_deepling_ride_presence_gate", "cnc_caribou_dash_presence_gate"};

    public static volatile boolean borninchaos = flag("borninchaosMinionScanPresenceGate");
    public static volatile boolean deepling = flag("cataclysmDeeplingRidePresenceGate");
    public static volatile boolean caribou = flag("cncCaribouDashPresenceGate");
    /** Shadow mode (see the class comment). */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.modScanGates.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Statistics for probes (plain fields): searches marked EMPTY / UNUSED, caribou searches answered without the call. */
    public static long markedEmpty, markedUnused, answered;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    /** Per site: the class the search names (checked by name once, then by identity). */
    private static final Class<?>[] SEEN = new Class<?>[CLASSES.length];
    private static volatile int announced;

    private ModScanGates() {
    }

    private static boolean flag(String name) {
        return !"false".equalsIgnoreCase(System.getProperty("bons_and_furious." + name, "true"));
    }

    /** The layer's indices, resolved on first use (the layer reads its registration files when it first initialises). */
    private static final class Indices {
        static final int[][] OF = new int[CLASSES.length][];

        static {
            for (int i = 0; i < CLASSES.length; i++) {
                OF[i] = new int[CLASSES[i].length];
                for (int k = 0; k < CLASSES[i].length; k++) OF[i][k] = EntityPresenceLink.trackedIndex(CLASSES[i][k]);
            }
        }
    }

    /** The level holds none of the site's classes (false = there is one, or the layer is not sure). */
    private static boolean noneOf(int site, Level level) {
        for (int index : Indices.OF[site]) if (!EntityPresenceLink.none(level, index)) return false;
        return true;
    }

    /** The search names exactly the site's class (by name, remembered by identity). */
    private static boolean names(int site, Class<?> searched) {
        if (searched == null) return false;
        if (SEEN[site] == searched) return true;
        if (!searched.getName().equals(CLASSES[site][0])) return false;
        SEEN[site] = searched;
        return true;
    }

    private static void announce(int bit, String key, String what) {
        if ((announced & (1 << bit)) != 0) return;
        synchronized (ModScanGates.class) {
            if ((announced & (1 << bit)) != 0) return;
            announced |= 1 << bit;
        }
        LOGGER.info("Bons and Furious: {}: {}{}", key, what, SHADOW ? " (SHADOW MODE: the search runs in full and is checked)" : "");
    }

    /** An entity of one of the site's classes (by name through the class hierarchy: the layer's rule). */
    private static boolean isSiteClass(int site, Object e) {
        for (Class<?> k = e.getClass(); k != null; k = k.getSuperclass())
            for (String n : CLASSES[site]) if (k.getName().equals(n)) return true;
        return false;
    }

    private static void mismatch(String key, String what) {
        if (SHADOW_MISMATCHES.incrementAndGet() <= 20) LOGGER.warn("Bons and Furious: {} SHADOW MISMATCH {}: {}", key, SHADOW_MISMATCHES.get(), what);
    }

    /**
     * Born in Chaos: getEntitiesOfClass(Entity.class, box, e -> true) of procedure {@code site}, whose loop acts only on the
     * site's classes. While the level holds none of them the search is marked UNUSED.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List minionScan(int site, Object level, Class<?> searched, Object box, Object predicate, Operation<List> original) {
        if (!borninchaos || searched != Entity.class || !(level instanceof Level l) || !noneOf(site, l)) return original.call(level, searched, box, predicate);
        announce(0, BIC_KEY, "the items' minion searches walk no entity sections while the level holds none of the minions they tame");
        if (SHADOW) {
            List found = original.call(level, searched, box, predicate);
            SHADOW_CHECKS.incrementAndGet();
            for (Object e : found) {
                if (e != null && isSiteClass(site, e)) {
                    mismatch(BIC_KEY, "the layer says " + level + " holds no minion of procedure " + site + ", the search found " + e);
                    break;
                }
            }
            return found;
        }
        if (!EntityPresenceLink.markUnused(BIC_KEY, level, Entity.class)) return original.call(level, searched, box, predicate);
        markedUnused++;
        try {
            return original.call(level, searched, box, predicate);
        } finally {
            EntityPresenceLink.unmark();
        }
    }

    /**
     * Cataclysm RidingCoralssus.canUse (WrapMethod): {@code ridesOrDry} = the Deepling is a passenger or its moistness is
     * 300 or less (read by the mixin); then the found Coralssus is never read and canUse is false, so the search is marked
     * UNUSED. {@code coralssus} is the searched class.
     */
    public static boolean deeplingCanUse(boolean ridesOrDry, Object level, Class<?> coralssus, Operation<Boolean> original) {
        if (!ridesOrDry || !deepling || !(level instanceof Level)) return original.call();
        announce(6, KEYS[DEEPLING], "a riding or dry Deepling's Coralssus search walks no entity sections (its canUse is false either way)");
        if (SHADOW) {
            boolean result = original.call();
            SHADOW_CHECKS.incrementAndGet();
            if (result) mismatch(KEYS[DEEPLING], "a riding or dry Deepling's canUse answered true in " + level);
            return result;
        }
        if (!EntityPresenceLink.markUnused(KEYS[DEEPLING], level, coralssus)) return original.call();
        markedUnused++;
        try {
            return original.call();
        } finally {
            EntityPresenceLink.unmark();
        }
    }

    @SuppressWarnings("rawtypes")
    private static List shadowEmpty(int site, List found, Object level) {
        SHADOW_CHECKS.incrementAndGet();
        for (Object e : found) {
            if (e instanceof PartEntity) continue;   // the walk never yields part entities; Forge's pass after it does
            mismatch(KEYS[site], "the layer says " + level + " holds no " + CLASSES[site][0] + ", the search found " + e);
            break;
        }
        return found;
    }

    /** getEntitiesOfClass(Class, AABB) naming the site's class (the Deepling's Coralssus search): EMPTY while none. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List scan(boolean on, int site, Object level, Class<?> searched, Object box, Operation<List> original) {
        if (!on || !names(site, searched) || !(level instanceof Level l) || !noneOf(site, l)) return original.call(level, searched, box);
        announce(site == DEEPLING ? 7 : 8, KEYS[site], "the search walks no entity sections while the level holds no " + CLASSES[site][0].substring(CLASSES[site][0].lastIndexOf('.') + 1));
        if (SHADOW) return shadowEmpty(site, original.call(level, searched, box), level);
        if (!EntityPresenceLink.markEmpty(KEYS[site], level, searched, Indices.OF[site][0])) return original.call(level, searched, box);
        markedEmpty++;
        try {
            return original.call(level, searched, box);
        } finally {
            EntityPresenceLink.unmark();
        }
    }

    /**
     * The caribou search, getEntitiesOfClass(CaribouEntity, a 0.5 block box, e -> true), made for every living entity every
     * tick: while the level holds no CaribouEntity it is answered the way the search ends when it finds nothing, without
     * the call: the profiler's "getEntities" count and a new empty ArrayList. Exact: the walk could visit no entity; Forge's
     * part pass cannot add one (a part entity is never a CaribouEntity); Valkyrien Skies' box hooks only move or reject a
     * box (a 0.5 block box is never too big), so the call has no other effect. Answering is cheaper here than marking the
     * walk (the box is so small that the walk itself costs little).
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List caribouScan(Object level, Class<?> searched, Object box, Object predicate, Operation<List> original) {
        if (!caribou || !names(CARIBOU, searched) || !(level instanceof Level l) || !noneOf(CARIBOU, l)) return original.call(level, searched, box, predicate);
        announce(8, KEYS[CARIBOU], "the search is answered without walking while the level holds no CaribouEntity");
        if (SHADOW) return shadowEmpty(CARIBOU, original.call(level, searched, box, predicate), level);
        answered++;
        l.m_46473_().m_6174_("getEntities");
        return new ArrayList();
    }
}
