package bons.furious.patch.stale_player_maps;

import bons.furious.patch.stale_player_maps.StalePlayerKeys.Kind;
import bons.furious.patch.stale_player_maps.StalePlayerKeys.Tracked;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;

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
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LevelEvent.Unload.class, StalePlayerKeysClient::onUnload);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, TickEvent.ClientTickEvent.class, StalePlayerKeysClient::onClientTick);
    }

    /** Client thread only: is an integrated server running next to this client (then two threads share both-side maps)? */
    static boolean integratedServerRunning() {
        Minecraft mc = Minecraft.m_91087_();
        return mc == null || mc.m_91092_() != null;
    }

    public static void onUnload(LevelEvent.Unload event) {
        LevelAccessor level = event.getLevel();
        if (level == null || !level.m_5776_()) return;
        Minecraft mc = Minecraft.m_91087_();
        ClientPacketListener connection = mc == null ? null : mc.m_91403_();
        if (connection == null || connection.m_105147_() == level) return;   // a disconnect, not a replacement: T3 follows
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
        if (t.kind == Kind.IDENTITY) t.map.keySet().removeIf(k -> k instanceof Entity e && e.m_9236_() == level);
        else t.map.keySet().removeIf(k -> k instanceof Entity e && e.m_9236_() == level && e.m_19879_() != localId);
        return before - t.map.size();
    }

    /** Client thread: the entity id of the current LocalPlayer, or Integer.MIN_VALUE when there is none. */
    static int localPlayerId() {
        Minecraft mc = Minecraft.m_91087_();
        return mc == null || mc.f_91074_ == null ? Integer.MIN_VALUE : mc.f_91074_.m_19879_();
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.m_91087_();
        if (mc.f_91073_ != null) {
            sessionActive = true;
            return;
        }
        if (sessionActive && mc.m_91092_() == null) {
            sessionActive = false;
            StalePlayerKeys.sessionEnded();
        }
    }
}
