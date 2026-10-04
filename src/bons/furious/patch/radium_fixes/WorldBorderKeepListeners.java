package bons.furious.patch.radium_fixes;

import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch radium_world_border_keep_listeners (Radium Re-Reforged 0.14.3, both sides). No Radium code here.
 *
 * Fix. Radium's mixin.world.block_entity_ticking.world_border (on by default, applied in this pack) caches, per ticking
 * block entity (LevelChunk$BoundTickingBlockEntity), whether the block entity is inside the world border and subscribes
 * the ticker to its level's WorldBorder through util.world_border_listener: one WorldBorderListenerOnceMulti per border
 * holds the subscribed tickers. Its seven listener methods all end with "forget every subscriber"
 * (WeakHashMap.clear()), the right thing after a SHAPE change (center, size, size lerp, area replaced at the end of a
 * lerp), where each ticker resets its cache and subscribes again at its next tick. But the four SIZE-UNRELATED events
 * (warning time m_5904_, warning distance m_5903_, damage per block m_6315_, damage buffer m_6313_) also clear the map,
 * while the tickers' listener methods for those events do nothing (WorldBorderListenerOnce's empty defaults), so every
 * ticker keeps its cached answer but is no longer subscribed. The next real shape change then reaches nobody: a block
 * entity that was inside keeps ticking after the border shrank past it, and one that was outside of a stationary border
 * never ticks again after the border grew over it. Vanilla asks WorldBorder.isWithinBounds on every tick.
 *
 * With the switch the four size-unrelated events still run every subscriber's (empty) handler but keep the subscribers,
 * so the next shape change reaches them and they reset as Radium intends; every cached answer then equals the live
 * vanilla test. Shape-change events are untouched. Triggered by /worldborder damage|warning (or a mod calling those
 * setters; none in this pack does); our worlds keep the default border, so the stale answer is not visible here.
 */
public final class WorldBorderKeepListeners {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.radiumWorldBorderKeepListeners=false turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.radiumWorldBorderKeepListeners", "true"));
    /** Counters for probes: size-unrelated events that kept a non-empty subscriber set, and the subscribers they kept. */
    public static final AtomicLong KEPT_EVENTS = new AtomicLong(), KEPT_LISTENERS = new AtomicLong();
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private WorldBorderKeepListeners() {
    }

    /** One size-unrelated border event left {@code subscribers} block-entity tickers subscribed. */
    public static void kept(int subscribers) {
        if (subscribers == 0) return;
        KEPT_EVENTS.incrementAndGet();
        KEPT_LISTENERS.addAndGet(subscribers);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: radium_world_border_keep_listeners kept {} block-entity tickers subscribed to a "
                    + "world border change that does not move the border", subscribers);
        }
    }
}
