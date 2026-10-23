package bons.furious.patch.stale_player_maps;

import bons.furious.patch.stale_player_maps.StalePlayerKeys.Kind;
import bons.furious.patch.stale_player_maps.StalePlayerKeys.Tracked;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.bus.api.EventPriority;

/**
 * Client half of group stale_player_maps (since 1.0.45), loaded only on a client (see {@link StalePlayerKeys}). Ours.
 *
 *  T6: a client level that is REPLACED (dimension change: Minecraft.setLevel posts LevelEvent.Unload for the old level while
 *      the connection already holds the new one) - identity-map entries of player objects in that level go: the old level is
 *      never ticked or rendered again, and the old LocalPlayer is never flagged removed. On a disconnect the connection still
 *      holds the unloading level (and an integrated server may still be shutting down while the client keeps ticking), so
 *      nothing happens there; T3 follows.
 *  T3: the first client tick (END) with no client level and no integrated server after a session: every client-side and
 *      both-side entry goes (see StalePlayerKeys.sessionEnded).
 */
public final class StalePlayerKeysClient {
    private static volatile boolean sessionActive;

    private StalePlayerKeysClient() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LevelEvent.Unload.class, StalePlayerKeysClient::onUnload);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, ClientTickEvent.Post.class, StalePlayerKeysClient::onClientTick);
    }

    /** Client thread only: is an integrated server running next to this client (then two threads share both-side maps)? */
    static boolean integratedServerRunning() {
        Minecraft mc = Minecraft.getInstance();
        return mc == null || mc.getSingleplayerServer() != null;
    }

    public static void onUnload(LevelEvent.Unload event) {
        LevelAccessor level = event.getLevel();
        if (level == null || !level.isClientSide()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener connection = mc == null ? null : mc.getConnection();
        if (connection == null || connection.getLevel() == level) return;   // a disconnect, not a replacement: T3 follows
        levelReplaced(level);
    }

    /**
     * T6, also called by the harness: identity-map entries of player objects in the replaced level go; inert-remote ID maps
     * also lose their remote players of that level (never the local player's id, which the next LocalPlayer keeps; never
     * while an integrated server shares the map).
     */
    public static void levelReplaced(LevelAccessor level) {
        int localId = localPlayerId();
        boolean shared = integratedServerRunning();
        for (Tracked t : StalePlayerKeys.TRACKED) {
            if (!t.on() || !StalePlayerKeys.mayWrite(t, true)) continue;
            if (t.kind == Kind.ID && (shared || !t.inert())) continue;
            try {
                int n;
                if (t.lock != null) {
                    synchronized (t.lock) {
                        n = removeLevel(t, level, localId);
                    }
                } else {
                    n = removeLevel(t, level, localId);
                }
                if (n > 0) {
                    StalePlayerKeys.PURGED.addAndGet(n);
                    StalePlayerKeys.announce(t);
                }
            } catch (Throwable e) {
                StalePlayerKeys.fail(t, e);
            }
        }
    }

    private static int removeLevel(Tracked t, LevelAccessor level, int localId) {
        int before = t.map.size();
        if (t.kind == Kind.IDENTITY) t.map.keySet().removeIf(k -> k instanceof Entity e && e.level() == level);
        else t.map.keySet().removeIf(k -> k instanceof Entity e && e.level() == level && e.getId() != localId);
        return before - t.map.size();
    }

    /** Client thread: the entity id of the current LocalPlayer, or Integer.MIN_VALUE when there is none. */
    static int localPlayerId() {
        Minecraft mc = Minecraft.getInstance();
        return mc == null || mc.player == null ? Integer.MIN_VALUE : mc.player.getId();
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            sessionActive = true;
            return;
        }
        if (sessionActive && mc.getSingleplayerServer() == null) {
            sessionActive = false;
            StalePlayerKeys.sessionEnded();
        }
    }
}
