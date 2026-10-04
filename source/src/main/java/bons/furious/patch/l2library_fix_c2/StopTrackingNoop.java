package bons.furious.patch.l2library_fix_c2;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious fix l2library_stop_tracking_noop (L2 Library, LGPL-2.1; 1.21.1 tested build: L2 Core 3.0.8+15 as
 * shipped inside L2 Library 3.0.8, l2library-3.0.8.jar!META-INF/jarjar/l2core-3.0.8+15.jar; server). No L2 code is
 * carried.
 *
 * L2 syncs "tracked" mob effects (tag l2core:tracked_effects on 1.21.1) to clients. Its
 * EffectSyncEvents.onPlayerStopTracking sends, for every tracked effect still active on the entity, an "effect removed"
 * packet to the entity's tracking players. NeoForge 21.1 fires PlayerEvent.StopTracking from ServerEntity.removePairing,
 * which runs only after ChunkMap.TrackedEntity has removed the leaving player from seenBy (removePlayer, updatePlayer) and
 * after it was sent the entity-remove packet, so the packet never reaches the player who stops tracking; it reaches every
 * player who still sees the entity, and their clients drop the effect (icons, render overlays) although it is still
 * active: a desync. When the entity itself is removed, ChunkMap.removeEntity takes its TrackedEntity out of entityMap
 * before broadcastRemoved, so the handler's sends find no tracked entity and send nothing. So the handler's packets are
 * wrong or not sent in every case, and the fix skips the handler. The leaving player's client is unchanged (it never
 * received them); the remaining viewers keep the correct state; start-tracking still sends the effects to a player who
 * comes back into range.
 *
 * Ported to 1.21.1: EffectSyncEvents moved to L2 Core (dev.xkmc.l2core.events) and takes Holder<MobEffect>; L2 Serial
 * 3.1.1 now sends with PacketDistributor.sendToPlayersTrackingEntityAndSelf, so the stock handler also told a player
 * target's own client that its still-active effects ended; the fix stops those packets too. The handler and the
 * NeoForge order are otherwise the same as on Forge 1.20.1.
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
            LOGGER.info("Bons and Furious: l2library_stop_tracking_noop: L2 no longer tells the remaining viewers that a tracked effect ended when one player stops tracking an entity");
        }
        return true;
    }
}
