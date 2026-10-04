package bons.furious.patch.l2library_fix_c2;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious fix l2library_stop_tracking_noop (L2 Library 2.5.1-slim, LGPL-2.1, shipped inside Ars Delight; server).
 * No L2 Library code is carried.
 *
 * L2 Library syncs "tracked" mob effects (TRACKED set or tag l2library:tracked_effects) to clients. Its
 * EffectSyncEvents.onPlayerStopTracking sends, for every tracked effect on the entity, an "effect removed" packet with
 * toTrackingPlayers(entity). Forge 47.4.16 fires PlayerEvent.StopTracking from ServerEntity.removePairing, which runs
 * only after ChunkMap.TrackedEntity has removed the leaving player from seenBy (and after it was sent the entity-remove
 * packet), so the packet never reaches the player who stops tracking; it reaches every player who still sees the entity,
 * and their clients drop the effect (icons, render overlays) although it is still active: a desync. When the entity itself
 * is removed, every viewer gets those packets just before or after dropping the entity, which changes nothing. So the
 * handler's packets are wrong or pointless in every case, and the fix skips the handler. The leaving player's client is
 * unchanged (it never received them); the remaining viewers keep the correct state; start-tracking still sends the
 * effects to a player who comes back into range.
 *
 * In this pack no effect is tracked (the tag is empty and no jar uses TRACKED), so nothing observable changes here; the fix
 * matters for packs with L2 content mods.
 *
 * Runtime flag: -Dbons_and_furious.l2libraryStopTrackingNoop=false runs the original handler.
 */
public final class StopTrackingNoop {
    /** Runtime switch (the config switch acts when the class is transformed). */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.l2libraryStopTrackingNoop", "true"));
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;
    /** Stop-tracking events skipped (for probes and the harness). */
    public static volatile long skipped;

    private StopTrackingNoop() {
    }

    /** True when the stop-tracking handler should not run. */
    public static boolean skip() {
        if (!enabled) return false;
        skipped++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: l2library_stop_tracking_noop: L2 Library no longer tells the remaining viewers that a tracked effect ended when one player stops tracking an entity");
        }
        return true;
    }
}
