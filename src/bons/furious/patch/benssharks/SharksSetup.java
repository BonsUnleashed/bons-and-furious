package bons.furious.patch.benssharks;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import bons.furious.guard.Guards;
import net.mcreator.sharks.entity.GreaterAxodileEntity;
import net.mcreator.sharks.init.BenssharksModMobEffects;
import net.mcreator.sharks.procedures.BleedingImmunityProcedure;
import net.mcreator.sharks.procedures.SeekingHandlerProcedure;
import net.mcreator.sharks.procedures.SharkBleedProcedure;
import net.mcreator.sharks.procedures.SmoothTurns2Procedure;
import net.mcreator.sharks.procedures.SmoothTurns3Procedure;
import net.mcreator.sharks.procedures.SmoothTurns5Procedure;
import net.mcreator.sharks.procedures.SmoothTurnsProcedure;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.EventBus;
import net.minecraftforge.eventbus.ListenerList;
import net.minecraftforge.eventbus.api.EventListenerHelper;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * benssharks_tick_listeners (Ben's Sharks 1.3.0): runs once, on the main thread, at the end of common setup (all mods
 * constructed, every Ben's listener registered), and only when the switch applies (its fingerprints matched, so its mixins
 * are in Ben's and the vanilla classes). Each group is switched on only if every prerequisite checks out: the hooks are
 * really merged into their classes and Ben's listeners are exactly the 62 + 6 of the tested build. Otherwise Ben's own
 * listeners stay registered and that part of Ben's Sharks runs as shipped. Formerly the Setup of Bons Sharks Fix 1.0.0.
 */
public final class SharksSetup {
    private SharksSetup() {}

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    static final String KEY = "benssharks_tick_listeners";
    static final String[] KEYS = {KEY, "benssharks_work_queue_idle", "benssharks_item_animation_idle", "benssharks_prey_search_merge"};
    static final String E = "net.mcreator.sharks.entity.";
    static final String[] BASE_TICK = {"AngelsharkEntity", "AxodileEntity", "BarracudaEntity", "BaskingSharkEntity",
            "BlacktipReefSharkEntity", "BlueSharkEntity", "BonnetheadSharkEntity", "BullSharkEntity", "CookiecutterSharkEntity",
            "GoblinSharkEntity", "GreenlandSharkEntity", "KrillEntity", "LandSharkEntity", "LemonSharkEntity",
            "LeopardSharkEntity", "MakoSharkEntity", "MegalodonEntity", "MegamouthSharkEntity", "NurseSharkEntity",
            "PilotFishEntity", "RemoraEntity", "SardineEntity", "SawsharkEntity", "SeaLionEntity", "SharkMinionEntity",
            "ShoalEntity", "ShrakEntity", "ThalassogerEntity", "ThresherSharkEntity", "TigerSharkEntity", "WhaleSharkEntity",
            "WhitetipSharkEntity"};

    record Found(String cls, EventPriority prio, IEventListener listener) {}

    static String summary = "not run";
    static int livingBefore = -1, livingAfter = -1, levelBefore = -1, levelAfter = -1, forgeBus = -1;

    /** From SetupHook (common setup, enqueued work). */
    static void runIfApplied() {
        Guards.Decision d = Guards.decide(KEY);
        if (d.state() != Guards.State.APPLY) {
            summary = KEY + " " + d.state().name().toLowerCase(java.util.Locale.ROOT) + " (" + d.detail() + ")";
            return;
        }
        try {
            run();
        } catch (Throwable t) {
            // never take the game down: whatever was not switched on stays as shipped
            summary = "setup failed (" + t + "); Ben's Sharks runs as shipped where not yet switched";
            LOGGER.warn("Bons and Furious: {}: {}", KEY, summary);
        }
    }

    private static void run() {
        ListenerList living = EventListenerHelper.getListenerList(LivingEvent.LivingTickEvent.class);
        ListenerList level = EventListenerHelper.getListenerList(TickEvent.LevelTickEvent.class);
        forgeBus = forgeBusId(living);
        if (forgeBus < 0) {
            summary = "could not find Ben's LivingTickEvent listeners on the Forge bus; Ben's Sharks runs as shipped";
            LOGGER.warn("Bons and Furious: {}: {}", KEY, summary);
            return;
        }
        List<Found> livingFound = found(living.getListeners(forgeBus));
        List<Found> levelFound = found(level.getListeners(forgeBus));
        livingBefore = living.getListeners(forgeBus).length;
        levelBefore = level.getListeners(forgeBus).length;

        List<String> notes = new ArrayList<>();
        notes.add(enableLiving(living, livingFound));
        notes.add(enableLevel(level, levelFound));
        if (SharkState.LIVING || SharkState.LEVEL) {
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TickEvent.LevelTickEvent.class, LevelTicks::onLevelTick);
            MinecraftForge.EVENT_BUS.addListener(SharksSetup::serverStarted);
            MinecraftForge.EVENT_BUS.addListener(SharksSetup::serverStopping);
        }
        livingAfter = living.getListeners(forgeBus).length;
        levelAfter = level.getListeners(forgeBus).length;
        summary = String.join("; ", notes) + String.format(" (Forge bus %d: LivingTickEvent listeners %d -> %d, LevelTickEvent %d -> %d)",
                forgeBus, livingBefore, livingAfter, levelBefore, levelAfter);
        LOGGER.info("Bons and Furious: {}: {}", KEY, summary);
    }

    /** The Forge bus's id (the field EventBus keeps it in, as the Placebo switch reads it); else the bus holding Ben's listeners. */
    private static int forgeBusId(ListenerList list) {
        try {
            if (MinecraftForge.EVENT_BUS instanceof EventBus bus) {
                Field id = EventBus.class.getDeclaredField("busID");
                id.setAccessible(true);
                return id.getInt(bus);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.debug("Bons and Furious: {}: Forge bus id not readable ({}); searching", KEY, e.toString());
        }
        for (int id = 0; id < 4096; id++) {
            IEventListener[] arr;
            try {
                arr = list.getListeners(id);
            } catch (RuntimeException e) {
                break;
            }
            for (IEventListener l : arr) if (owner(l) != null) return id;
        }
        return -1;
    }

    private static final Pattern OWNER = Pattern.compile("(net\\.mcreator\\.sharks\\.[\\w.$]+)");

    /** Owner class of an @SubscribeEvent handler, from ASMEventHandler's public toString ("ASM: class x.Y method(desc)"). */
    private static String owner(IEventListener l) {
        if (l instanceof EventPriority) return null;
        Matcher m = OWNER.matcher(String.valueOf(l));
        return m.find() ? m.group(1) : null;
    }

    private static List<Found> found(IEventListener[] arr) {
        List<Found> out = new ArrayList<>();
        EventPriority prio = null;
        for (IEventListener l : arr) {
            if (l instanceof EventPriority p) {
                prio = p;
                continue;
            }
            String o = owner(l);
            if (o != null) out.add(new Found(o, prio, l));
        }
        return out;
    }

    /** True when a handler of ours (an injector named bons$...) has been merged into the class. Loads the class if needed. */
    static boolean merged(String className, String handler) {
        try {
            Class<?> c = Class.forName(className, false, SharksSetup.class.getClassLoader());
            for (Method m : c.getDeclaredMethods()) if (m.getName().endsWith("$" + handler)) return true;
        } catch (Throwable t) {
            LOGGER.debug("Bons and Furious: {}: could not check {} ({})", KEY, className, t.toString());
        }
        return false;
    }

    private static String enableLiving(ListenerList list, List<Found> found) {
        Set<String> classes = new HashSet<>();
        for (Found f : found) classes.add(f.cls);
        if (found.size() != Table.LIVING_LISTENERS || classes.size() != Table.LIVING_LISTENERS) {
            return "per-entity listeners kept as shipped (found " + found.size() + ", expected " + Table.LIVING_LISTENERS + ")";
        }
        List<String> missing = new ArrayList<>();
        for (String c : BASE_TICK) if (!merged(E + c, "bons$sharkTick")) missing.add(c);
        if (!merged(E + "GreaterAxodileEntity", "bons$sharkTick")) missing.add("GreaterAxodileEntity.tick");
        if (!merged("net.minecraft.world.entity.animal.Dolphin", "bons$sharksDolphinTick")) missing.add("Dolphin");
        if (!merged("net.minecraft.world.entity.LivingEntity", "bons$sharksEffectAdded")) missing.add("LivingEntity.onEffectAdded");
        if (!merged("net.mcreator.sharks.potion.BleedingMobEffect", "bons$sharksBleedingTick")) missing.add("BleedingMobEffect");
        if (!LevelStateHolder.class.isAssignableFrom(Level.class)) missing.add("Level");
        if (!missing.isEmpty()) return "per-entity listeners kept as shipped (hooks not applied: " + missing + ")";

        Map<String, Proc> table = new HashMap<>();
        Table.fill(table);
        List<Proc> order = new ArrayList<>();
        List<Bleed.Step> bleed = new ArrayList<>();
        boolean armor = false, dolphin = false;
        for (Found f : found) {
            if (Table.EMPTY.contains(f.cls)) continue;
            String sp = Table.SPECIAL.get(f.cls);
            if (sp != null) {
                switch (sp) {
                    case "bleed" -> bleed.add(f.cls.endsWith("SharkBleedProcedure")
                            ? new Bleed.Step(SharkBleedProcedure::onEntityTick, true)
                            : new Bleed.Step(BleedingImmunityProcedure::onEntityTick, false));
                    case "armor" -> armor = true;
                    case "dolphin" -> dolphin = true;
                    default -> {
                        return "per-entity listeners kept as shipped (unknown special " + sp + ")";
                    }
                }
                continue;
            }
            Proc p = table.get(f.cls);
            if (p == null) return "per-entity listeners kept as shipped (unclassified listener " + f.cls + ")";
            try {
                p.resolve();
            } catch (ReflectiveOperationException | RuntimeException e) {
                return "per-entity listeners kept as shipped (could not read " + f.cls + " id list: " + e + ")";
            }
            order.add(p);
        }
        if (!armor || !dolphin || bleed.size() != 2) return "per-entity listeners kept as shipped (special handlers missing)";
        Bleed.BLEEDING = BenssharksModMobEffects.BLEEDING.get();
        Bleed.ORDER = bleed.toArray(new Bleed.Step[0]);
        Dispatch.ORDER = order.toArray(new Proc[0]);
        Dispatch.ARMOR = true;
        Dispatch.DOLPHIN = true;
        for (Found f : found) list.unregister(forgeBus, f.listener);
        if (!found(list.getListeners(forgeBus)).isEmpty()) {
            LOGGER.warn("Bons and Furious: {}: some of Ben's LivingTickEvent listeners are still registered after removal", KEY);
        }
        SharkState.LIVING = true;
        return String.format("removed %d per-entity tick listeners (%d empty); %d original handlers now run only for Ben's entities, "
                + "plus barracuda-side armour check, dolphin hook and bleeding pass", found.size(), Table.EMPTY.size(), order.size());
    }

    private static String enableLevel(ListenerList list, List<Found> found) {
        if (found.size() != 6) return "world-tick listeners kept as shipped (found " + found.size() + ", expected 6)";
        if (!merged("net.minecraft.server.level.ServerLevel$EntityCallbacks", "bons$sharksTrackingStart")
                || !merged("net.minecraft.server.level.ServerLevel$EntityCallbacks", "bons$sharksTrackingEnd")
                || !LevelStateHolder.class.isAssignableFrom(Level.class)) {
            return "world-tick listeners kept as shipped (entity-tracking hook not applied)";
        }
        try {
            Method m = SeekingHandlerProcedure.class.getDeclaredMethod("tickTrident", ServerLevel.class, ThrownTrident.class);
            m.setAccessible(true);
            LevelTicks.TICK_TRIDENT = MethodHandles.lookup().unreflect(m);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "world-tick listeners kept as shipped (SeekingHandler.tickTrident not reachable: " + e + ")";
        }
        List<LevelTicks.LevelProc> order = new ArrayList<>();
        for (Found f : found) {
            String key = Table.LEVEL_KEYS.get(f.cls);
            if (key == null) return "world-tick listeners kept as shipped (unknown listener " + f.cls + ")";
            switch (key) {
                case "ST1" -> order.add(LevelTicks.smooth(Table.ST1, Table.ST1_WATER, SmoothTurnsProcedure::applySmoothMotion));
                case "ST2" -> order.add(LevelTicks.smooth(Table.ST2, Table.ST2_WATER, SmoothTurns2Procedure::applySmoothMotion));
                case "ST3" -> order.add(LevelTicks.smooth(Table.ST3, Table.ST3_WATER, SmoothTurns3Procedure::applyAxodileMotion));
                case "ST5" -> order.add(LevelTicks.smooth(Table.ST5, Table.ST5_WATER,
                        e -> SmoothTurns5Procedure.applySmoothTurning((GreaterAxodileEntity) e)));
                case "SEEK" -> order.add(LevelTicks.SEEK);
                default -> { }   // ST6: empty body
            }
        }
        LevelTicks.ORDER = order.toArray(new LevelTicks.LevelProc[0]);
        for (Found f : found) list.unregister(forgeBus, f.listener);
        SharkState.LEVEL = true;
        return "removed 6 world-tick listeners (4 walked every entity of every dimension each tick); smooth turns and "
                + "seeking tridents now run over tracked per-world sets";
    }

    /** Re-count Ben's listeners still on the two tick events (self-test). */
    static int[] recount() {
        if (forgeBus < 0) return new int[] {-1, -1};
        return new int[] {found(EventListenerHelper.getListenerList(LivingEvent.LivingTickEvent.class).getListeners(forgeBus)).size(),
                found(EventListenerHelper.getListenerList(TickEvent.LevelTickEvent.class).getListeners(forgeBus)).size()};
    }

    private static void serverStarted(ServerStartedEvent event) {
        int[] left = recount();
        boolean ok = (!SharkState.LIVING || left[0] == 0) && (!SharkState.LEVEL || left[1] == 0);
        LOGGER.info("Bons and Furious: {}: server started; Ben's listeners still on LivingTickEvent / LevelTickEvent = {} / {}; "
                + "living={} level={}; {}", KEY, left[0], left[1], SharkState.LIVING, SharkState.LEVEL,
                ok ? "in effect" : "CHECK: Ben's listeners remain");
    }

    private static void serverStopping(ServerStoppingEvent event) {
        LOGGER.info("Bons and Furious: {}: counters at stop: {}", KEY, Stats.snapshot());
    }
}
