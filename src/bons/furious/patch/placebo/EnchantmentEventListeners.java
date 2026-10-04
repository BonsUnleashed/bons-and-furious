package bons.furious.patch.placebo;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.EventBus;
import net.minecraftforge.eventbus.ListenerList;
import net.minecraftforge.eventbus.api.EventListenerHelper;
import net.minecraftforge.eventbus.api.IEventListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch placebo_enchantment_event_skip (Placebo 8.6.3, MIT; both sides).
 *
 * Placebo's coremods make every enchantment-level query of every item stack (Forge routes vanilla's
 * EnchantmentHelper.getItemEnchantmentLevel to ItemStack.getEnchantmentLevel, empty stacks included, plus every
 * getAllEnchantments) go through PlaceboEventFactory: a new HashMap, a GetEnchantmentLevelEvent and a post on Forge's
 * event bus, so that mods (Apotheosis' gems and affixes) can add levels. When nothing listens for that event, the post
 * calls nobody and the answer is the value Placebo put in.
 *
 * The check is the one the bus itself makes: the event class's ListenerList, asked for this bus's listeners (parent
 * Event listeners folded in, as post sees them). When it is empty, the single-enchantment query returns the level it
 * was given, and the all-enchantments query returns its fresh HashMap copy without building or posting the event (the
 * copy is kept: callers iterate it, and a caller's map type or iteration order must not change). A listener registered
 * at any time is seen on the next query; any problem resolving the bus turns the switch off with one WARN line.
 *
 * -Dbons_and_furious.placeboEnchantmentEventSkip=false always posts; -Dbons_and_furious.placeboEnchantmentEventSkip.shadow=true
 * (verification runs only) also posts on every skipped single-enchantment query and counts answers that differ
 * (SHADOW_CHECKS / SHADOW_MISMATCHES).
 */
public final class EnchantmentEventListeners {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.placeboEnchantmentEventSkip", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.placeboEnchantmentEventSkip.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static ListenerList list;
    private static int busId = -1;
    private static volatile boolean ready, failed, announced;

    private EnchantmentEventListeners() {
    }

    /** True when no listener would receive a GetEnchantmentLevelEvent posted on MinecraftForge.EVENT_BUS. */
    public static boolean none() {
        if (!enabled || failed) return false;
        if (!ready) resolve();
        if (failed) return false;
        return list.getListeners(busId).length == 0;
    }

    private static synchronized void resolve() {
        if (ready || failed) return;
        try {
            Class<?> event = Class.forName("dev.shadowsoffire.placebo.events.GetEnchantmentLevelEvent", true, EnchantmentEventListeners.class.getClassLoader());
            if (!(MinecraftForge.EVENT_BUS instanceof EventBus bus)) throw new IllegalStateException("the Forge bus is a " + MinecraftForge.EVENT_BUS.getClass().getName());
            Field id = EventBus.class.getDeclaredField("busID");
            id.setAccessible(true);
            busId = id.getInt(bus);
            list = EventListenerHelper.getListenerList(event);
            ready = true;
            if (!announced) {
                announced = true;
                // who listens right now (a listener on Event itself, which some libraries register, receives every event)
                IEventListener[] now = list.getListeners(busId);
                StringBuilder who = new StringBuilder();
                for (int i = 0; i < Math.min(5, now.length); i++) who.append(i == 0 ? ": " : ", ").append(now[i]);
                if (now.length > 5) who.append(", ...");
                LOGGER.info("Bons and Furious: placebo_enchantment_event_skip applies (Placebo's enchantment-level event is built and posted only when something listens); {} listener(s) at the first query{}{}",
                        now.length, who, SHADOW ? " - shadow verification on" : "");
            }
        } catch (Throwable t) {
            failed = true;
            LOGGER.warn("Bons and Furious: placebo_enchantment_event_skip stands down ({}); Placebo posts its event as before", t.toString());
        }
    }

    public static void shadow(int skipped, int posted) {
        SHADOW_CHECKS.incrementAndGet();
        if (skipped != posted && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
            LOGGER.warn("Bons and Furious: placebo_enchantment_event_skip shadow mismatch: {} vs {}", skipped, posted);
        }
    }
}
