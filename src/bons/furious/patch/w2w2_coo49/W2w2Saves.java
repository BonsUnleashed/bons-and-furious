package bons.furious.patch.w2w2_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49 switch w2w2_waypoint_save_coalesce (Xaero's Map - Waystones Compatibility 1.1, client; kind
 * change, off by default; helper of XaeronSaveMixin; no code of that mod carried).
 *
 * What the mod does. Every waystone update the client receives (Waystones fires one WaystoneUpdateReceivedEvent per update
 * message) runs XaeronCompatibility.addWaypoint, which adds or edits the waypoint in Xaero's Minimap's current waypoint
 * world and then saves that whole waypoint world to disk (ModSettings.saveWaypoints) on the client thread. N updates in one
 * tick are N full writes of the same file.
 *
 * What the switch does. The save is remembered instead (the waypoint world the call named, with the settings object and the
 * call itself); the waypoint list still changes at once, as before. Each remembered waypoint world is saved once, in the
 * order it was first remembered, at the end of the client tick, and also right before the player logs out and before a
 * client level unloads (HIGHEST-priority listeners), so no save is lost to a disconnect. A save that throws an IOException
 * prints its stack trace, as the mod does; any other exception is thrown after the other remembered saves have run.
 * Trade-off (off by default): fewer intermediate writes; the file is written at the end of the client tick instead of at
 * each update, so a game crash inside that tick loses that tick's waypoint changes. Calls off the client thread, or before
 * the listeners could be registered, save at once as before. Runtime flag bons_and_furious.w2w2WaypointSaveCoalesce.
 */
public final class W2w2Saves {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.w2w2WaypointSaveCoalesce", "true"));
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean listening, noListener, announced;
    /** Remembered saves in first-remembered order (one per waypoint world object); client thread only. */
    private static final List<Pending> PENDING = new ArrayList<>();
    /** Saves left out so far (statistics for the harness and the INFO line). */
    public static long deferred, flushed;

    private record Pending(Object settings, Object world, Operation<Void> save) {}

    private W2w2Saves() {
    }

    /**
     * In place of {@code settings.saveWaypoints(world)} inside XaeronCompatibility.addWaypoint: true when the save was
     * remembered (the caller then skips it), false when it must run now.
     */
    public static boolean defer(Object settings, Object world, Operation<Void> save) {
        if (!enabled || world == null) return false;
        if (!listening) listen();
        if (noListener || !onClientThread()) return false;
        for (Pending p : PENDING) {
            if (p.world() == world && p.settings() == settings) {
                deferred++;
                return true;
            }
        }
        PENDING.add(new Pending(settings, world, save));
        deferred++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: w2w2_waypoint_save_coalesce applies (waystone waypoint saves are written once at the end of the client tick)");
        }
        return true;
    }

    /** Saves every remembered waypoint world once, in first-remembered order (end of client tick, logout, level unload). */
    public static void flush() {
        if (PENDING.isEmpty()) return;
        List<Pending> now = new ArrayList<>(PENDING);
        PENDING.clear();
        Throwable first = null;
        for (Pending p : now) {
            try {
                p.save().call(p.settings(), p.world());
                flushed++;
            } catch (Throwable t) {
                if (t instanceof IOException io) io.printStackTrace();   // what the mod does with a failed save
                else if (first == null) first = t;
            }
        }
        if (first instanceof RuntimeException re) throw re;
        if (first instanceof Error er) throw er;
        if (first != null) throw new IllegalStateException(first);
    }

    static boolean onClientThread() {
        try {
            Minecraft mc = Minecraft.m_91087_();
            return mc != null && mc.m_18695_();
        } catch (Throwable t) {
            return false;
        }
    }

    private static synchronized void listen() {
        if (listening) return;
        try {
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, TickEvent.ClientTickEvent.class, e -> {
                if (e.phase == TickEvent.Phase.END) flush();
            });
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, ClientPlayerNetworkEvent.LoggingOut.class, e -> flush());
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, LevelEvent.Unload.class, e -> {
                if (e.getLevel() != null && e.getLevel().m_5776_()) flush();
            });
        } catch (Throwable t) {
            noListener = true;
            LOGGER.warn("Bons and Furious: w2w2_waypoint_save_coalesce cannot register its tick listener ({}); waypoint saves run at once as before", t.toString());
        }
        listening = true;
    }

    /** For the harness: remembered saves now. */
    public static int pending() {
        return PENDING.size();
    }
}
