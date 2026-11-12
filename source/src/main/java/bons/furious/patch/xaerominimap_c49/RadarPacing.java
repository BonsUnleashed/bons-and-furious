package bons.furious.patch.xaerominimap_c49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import xaero.common.minimap.MinimapProcessor;
import xaero.hud.minimap.radar.RadarSession;
import xaero.hud.minimap.radar.state.RadarList;

/**
 * Bons and Furious switches xaerominimap_hidden_radar_skip and xaerominimap_radar_after_frame (Xaero's Minimap 26.6.0 for
 * NeoForge 1.21.1, tested build; client; since 1.0.49). Kind: change (trade-offs, off by default). Xaero's Minimap is All
 * Rights Reserved: none of its code is carried; the wrappers only decide when its own radar update runs.
 *
 * MinimapProcessor.onClientTick (from ClientEvents.handleClientTickStart, NeoForge's ClientTickEvent.Pre) rebuilds the
 * radar's entity lists every client tick (RadarSession.update: every loaded entity through the radar category rules),
 * whether or not the minimap is drawn, and inside the tick of the frame.
 *
 * xaerominimap_hidden_radar_skip (Idea: Collections Of Optimizations (xaerominimap.skipHiddenRadar), idea text only).
 * While no screen is open and the minimap was not drawn in the last frame (F1, toggled off, no minimap item, debug
 * screen), the tick's update is left out; the first one left out empties every radar list (no entity is kept from a world
 * the player no longer sees), and the first minimap draw after that runs the update with the last tick's world, camera
 * entity and player before drawing. Updates without a world (leaving it) always run.
 * Trade-off: while the minimap is hidden its radar lists are empty (only other mods reading them through Xaero's API can
 * see it); the first frame it is drawn again builds them from that frame's entity positions instead of the last tick's.
 *
 * xaerominimap_radar_after_frame (Idea: Collections Of Optimizations (xaerominimap.deferRadarUpdate), idea text only).
 * The tick's update is moved behind the frame: onClientTick keeps the world, camera entity and player, and right after the
 * frame is shown (Minecraft.runTick, after Window.updateDisplay, before the frame rate limit's wait) the update runs once
 * with the last kept values. Updates without a world run at once and drop a kept one.
 * Trade-off: the radar dots of the frame that runs a client tick are those of the tick before (one frame late), built
 * from the entities after the tick moved them.
 *
 * With both switches applied the hidden test comes first (a hidden minimap neither updates nor queues an update).
 * -Dbons_and_furious.xaerominimapHiddenRadarSkip=false / xaerominimapRadarAfterFrame=false: emergency off (the config keys
 * decide whether the mixins are applied at all).
 */
public final class RadarPacing {
    public static volatile boolean hiddenEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.xaerominimapHiddenRadarSkip", "true"));
    public static volatile boolean deferEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.xaerominimapRadarAfterFrame", "true"));
    /** Counters for the harness and the rig (render thread). */
    public static long skipped, cleared, caughtUp, deferred, deferredRuns, dropped;

    static int frame;
    static int drawnFrame = -1;
    private static int depth;
    private static boolean skipping, catchUp;
    private static RadarSession lastSession, pendingSession;
    private static ClientLevel lastWorld, pendingWorld;
    private static Entity lastEntity, pendingEntity;
    private static Player lastPlayer, pendingPlayer;
    private static volatile Boolean hiddenApplied, deferApplied;
    private static volatile boolean hiddenAnnounced, deferAnnounced;

    private RadarPacing() {
    }

    /** GameRenderer.render HEAD (the hidden switch): a frame starts. */
    public static void frame() {
        frame++;
        // the world was left or changed while the minimap was hidden: nothing will run the catch-up on it, so it must not stay reachable (bug sweep 2026-11-10)
        if (lastSession != null && Minecraft.getInstance().level != lastWorld) {
            skipping = catchUp = false;
            forgetLast();
        }
    }

    /** MinimapRenderer.renderMinimap HEAD (the hidden switch): the minimap is drawn in this frame. */
    public static void drawing() {
        drawnFrame = frame;
        if (!catchUp) return;
        catchUp = false;
        skipping = false;
        RadarSession s = lastSession;
        ClientLevel w = lastWorld;
        Entity e = lastEntity;
        Player p = lastPlayer;
        forgetLast();
        caughtUp++;
        s.update(w, e, p);
        EmptyElementSkip.listsChanged();
    }

    /**
     * The tick's `radarSession.update(world, renderEntity, player)` in MinimapProcessor.onClientTick; both switches wrap it
     * (kind: 0 hidden, 1 after frame) and the outer wrapper decides for both.
     */
    public static void tick(int kind, RadarSession session, ClientLevel world, Entity renderEntity, Player player, Operation<Void> original) {
        if (depth != 0) {                    // the inner wrapper: the outer one has decided to run it
            original.call(session, world, renderEntity, player);
            return;
        }
        depth++;
        try {
            decide(session, world, renderEntity, player, original);
        } finally {
            depth--;
        }
    }

    private static void decide(RadarSession session, ClientLevel world, Entity renderEntity, Player player, Operation<Void> original) {
        boolean hidden = hiddenEnabled && hiddenApplied(), defer = deferEnabled && deferApplied();
        if (world == null || !hidden && !defer) {
            if (pendingSession != null) {
                dropped++;
                forgetPending();
            }
            if (world == null) {
                skipping = catchUp = false;
                forgetLast();
            }
            original.call(session, world, renderEntity, player);
            return;
        }
        if (hidden && !screenOpen() && drawnFrame != frame) {
            if (!hiddenAnnounced) announce(0);
            if (!skipping) {
                skipping = true;
                for (RadarList list : session.getState().getRadarLists()) list.clearEntities();
                cleared++;
            }
            if (pendingSession != null) {
                dropped++;
                forgetPending();
            }
            lastSession = session;
            lastWorld = world;
            lastEntity = renderEntity;
            lastPlayer = player;
            catchUp = true;
            skipped++;
            return;
        }
        skipping = catchUp = false;
        forgetLast();
        if (defer) {
            if (!deferAnnounced) announce(1);
            pendingSession = session;
            pendingWorld = world;
            pendingEntity = renderEntity;
            pendingPlayer = player;
            deferred++;
            return;
        }
        original.call(session, world, renderEntity, player);
    }

    /** Minecraft.runTick, right after Window.updateDisplay (the after-frame switch): the kept update runs once. */
    public static void afterFrame() {
        RadarSession s = pendingSession;
        if (s == null) return;
        ClientLevel w = pendingWorld;
        Entity e = pendingEntity;
        Player p = pendingPlayer;
        forgetPending();
        deferredRuns++;
        s.update(w, e, p);
        EmptyElementSkip.listsChanged();
    }

    private static void forgetLast() {
        lastSession = null;
        lastWorld = null;
        lastEntity = null;
        lastPlayer = null;
    }

    private static void forgetPending() {
        pendingSession = null;
        pendingWorld = null;
        pendingEntity = null;
        pendingPlayer = null;
    }

    static boolean screenOpen() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.screen != null;
    }

    /** Which of the two wrappers Mixin merged into MinimapProcessor (each belongs to its own switch). */
    static boolean hiddenApplied() {
        Boolean b = hiddenApplied;
        if (b == null) inspect();
        return hiddenApplied;
    }

    static boolean deferApplied() {
        Boolean b = deferApplied;
        if (b == null) inspect();
        return deferApplied;
    }

    private static synchronized void inspect() {
        if (hiddenApplied != null && deferApplied != null) return;
        boolean h = false, d = false;
        try {
            for (Method m : MinimapProcessor.class.getDeclaredMethods()) {
                String n = m.getName();
                h |= n.contains("bons$xaerominimapC49HiddenTick");
                d |= n.contains("bons$xaerominimapC49DeferTick");
            }
        } catch (Throwable t) {
            h = d = false;
        }
        hiddenApplied = h;
        deferApplied = d;
    }

    private static synchronized void announce(int which) {
        String line;
        if (which == 0) {
            if (hiddenAnnounced) return;
            hiddenAnnounced = true;
            line = "xaerominimap_hidden_radar_skip applies (the radar lists are not rebuilt while the minimap is hidden and no screen is open)";
        } else {
            if (deferAnnounced) return;
            deferAnnounced = true;
            line = "xaerominimap_radar_after_frame applies (the radar lists are rebuilt right after the frame that runs the client tick)";
        }
        LogManager.getLogger("Bons and Furious").info("Bons and Furious: " + line);
    }
}
