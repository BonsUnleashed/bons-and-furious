package bons.furious.patch.radium_fixes;

import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch radium_world_border_keep_listeners (Radium, LGPL-3.0; 1.21.1 tested build
 * radium-mc1.21.1-0.13.1+git.4994e83; both sides). No Radium code here.
 *
 * Fix. Radium's mixin.world.block_entity_ticking.world_border (on by default) caches, per ticking block entity
 * (LevelChunk$BoundTickingBlockEntity), whether the block entity is inside the world border and subscribes the ticker to
 * its level's WorldBorder through util.world_border_listener: one WorldBorderListenerOnceMulti per border holds the
 * subscribed tickers. Its eight listener methods all end with "forget every subscriber" (WeakHashMap.clear()), the right
 * thing after a SHAPE change (center, size, size lerp, area replaced at the end of a lerp), where each ticker resets its
 * cache (lithium$onWorldBorderShapeChange) and subscribes again at its next tick. But the four SIZE-UNRELATED events
 * (warning time onBorderSetWarningTime, warning distance onBorderSetWarningBlocks, damage per block
 * onBorderSetDamagePerBlock, damage buffer onBorderSetDamageSafeZOne) also clear the map, while the tickers' listener
 * methods for those events do nothing (WorldBorderListenerOnce's empty defaults), so every ticker keeps its cached answer
 * but is no longer subscribed. The next real shape change then reaches nobody: a block entity that was inside keeps
 * ticking after the border shrank past it, and one that was outside of a stationary border never ticks again after the
 * border grew over it. Vanilla asks WorldBorder.isWithinBounds on every tick.
 *
 * With the switch the four size-unrelated events still run every subscriber's (empty) handler but keep the subscribers,
 * so the next shape change reaches them and they reset as Radium intends; every cached answer then equals the live
 * vanilla test (the cache caches only in monotonic states: stationary, or growing+inside / shrinking+outside).
 * Shape-change events are untouched. WorldBorder.setAbsoluteMaxSize notifies no listener in vanilla 1.21.1 either
 * (unchanged, as on 1.20.1). Triggered by /worldborder damage|warning (or a mod calling those setters).
 *
 * Ported to 1.21.1: Radium 0.13.1's WorldBorderListenerOnceMulti, WorldBorderListenerOnce, WorldBorderMixin and the
 * ticker mixin (DirectBlockEntityTickInvokerMixin) have the same logic as Radium Re-Reforged 0.14.3 (decompiled side by
 * side); only names changed (Mojang handler names, the reset callback is lithium$onWorldBorderShapeChange). Vanilla
 * 1.21.1's four setters still only set a field and notify every listener.
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
