package bons.furious.patch.brutality_coo49;

import net.minecraftforge.event.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch brutality_tick_counter_phase (Brutality 0.8.5, MIT; both sides; since 1.0.49; kind change,
 * gameplay fix, off by default). Idea: Collections Of Optimizations (brutality.fixDoubleTickCounters), idea text only.
 *
 * ServerTickHandler.onServerTick and ClientTickHandler.onClientTick have no phase check, so Forge's START and END posts of
 * every tick both count: Brutality's server tick counter (read by the Sine and Cosine charms) and client tick counter
 * (the same charms and its GUI animation) run twice as fast as ticks, and the tick sync packet the server sends to every
 * player "every 20 counts" goes out every 10 ticks.
 *
 * What changes with the switch on: both handlers return at once unless the event's phase is END, so each counter counts
 * one per tick (the speed the readers were written for) and the sync packet goes out every 20 ticks. Nothing is saved;
 * set it the same on the server and on its clients (the client takes the server's counter at every sync).
 *
 * -Dbons_and_furious.brutalityTickCounterPhase=false runs both handlers as shipped (emergency off).
 */
public final class BrutalityTicks {
    public static final String KEY = "brutality_tick_counter_phase";
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.brutalityTickCounterPhase", "true"));
    private static volatile boolean announced;

    private BrutalityTicks() {
    }

    /** True when the handler must return at once: the switch's flag is on and this is not the END post of the tick. */
    public static boolean skip(TickEvent.Phase phase) {
        if (!enabled || phase == TickEvent.Phase.END) return false;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: {} applies (Brutality's tick counters count the END phase of each tick only)", KEY);
        }
        return true;
    }
}
