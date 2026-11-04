package bons.furious.patch.simplyswordsoverhaul_coo49;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch simplyswordsoverhaul_logout_dash_restore (Simply Swords Overhaul 1.0.4 with Simply Swords
 * 1.56.0; MIT; server side; since 1.0.49). Gameplay fix, off by default. Ours; no Simply Swords Overhaul code is carried.
 * Idea: Collections Of Optimizations (simplyswordsoverhaul.restoreDashStateOnLogout), idea text only.
 *
 * Simply Swords Overhaul's Whisperwind, Storm's Edge and Emberlash dashes change the player and undo it from timed tasks:
 * Whisperwind turns gravity off and sets the SimplySwordsWhisperwindDashing flag (and SimplySwordsWhisperwindMobKilled on
 * a kill) in the player's persistent data, its end task (14 ticks later, only while the flag is set) clears both, turns
 * gravity back on and drops the player's dash hit list; Storm's Edge turns gravity off in the use and back on at the
 * landing (8 ticks) or 6 ticks later in the air; Emberlash turns it off in the air 4 ticks in and on again at 10. A player
 * who leaves in between is saved as the dash left them: gravity off (they hover after joining again) and, for
 * Whisperwind, still flagged as dashing (the next use ends a dash that is not happening and puts the sword on its full
 * cooldown). The tasks themselves keep running on the departed player object.
 *
 * The handlers' Scheduler.schedule calls hand their tasks to task(): the same task, wrapped to keep a per-player session
 * (tasks pending, Whisperwind tasks pending, and whether gravity is off because Simply Swords Overhaul's own code turned
 * it off: each task, and each handler setNoGravity call reported through gravity(), is seen with the gravity before and
 * after it; a change made by anything else between two of them makes the current state someone else's). onLoggedOut
 * (Forge fires it before the player list saves the player) acts only for a player with tasks pending: gravity back on
 * if Simply Swords Overhaul's own code turned it off and it is still off; with a Whisperwind task pending and the dashing
 * flag set, the two flags removed and the hit list dropped, as the dash end does. The player's remaining tasks then do
 * nothing. Gravity that was already off when a dash began, or that another mod turned off during it, is left as it is.
 * A session ends when its last task has run, at logout and at server stop. Runtime flag
 * -Dbons_and_furious.simplyswordsoverhaulLogoutDashRestore=false.
 */
public final class SsoDash {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.simplyswordsoverhaulLogoutDashRestore", "true"));
    public static final int WHISPERWIND = 1, STORMS_EDGE = 2, EMBERLASH = 3;
    static final String DASHING = "SimplySwordsWhisperwindDashing", MOB_KILLED = "SimplySwordsWhisperwindMobKilled";
    /** Players with Simply Swords Overhaul dash tasks pending (server thread; locked for safety). */
    private static final Map<Player, Session> SESSIONS = new IdentityHashMap<>();
    /** The listeners are registered (statistics and the harness). */
    public static volatile boolean installed;
    /** Tasks wrapped, task runs left out after a logout, logouts that restored something (statistics; plain, racy). */
    public static long wrapped, skipped, restored;
    private static boolean tried;
    private static volatile boolean announced;

    private SsoDash() {
    }

    static final class Session {
        final Player player;
        int pending, whisperwind;
        /** Gravity is off because Simply Swords Overhaul's own code turned it off, and nothing else touched it since. */
        boolean gravityOff;
        /** The player's no-gravity state right after the last Simply Swords Overhaul action we saw. */
        boolean lastSeen;
        volatile boolean left;

        Session(Player player, boolean seen) {
            this.player = player;
            this.lastSeen = seen;
        }

        /** One Simply Swords Overhaul action (a handler's setNoGravity call or a task) seen: gravity before and after it. */
        void seen(boolean before, boolean after) {
            if (before != lastSeen) gravityOff = false;     // changed by someone else since our last look: theirs now
            if (after != before) gravityOff = after;
            lastSeen = after;
        }
    }

    /** In place of the task a dash handler passes to Scheduler.schedule. */
    public static Runnable task(Player player, Runnable task, int period, int kind) {
        if (!enabled || task == null || period > 0 || !(player instanceof ServerPlayer)) return task;
        Session s;
        synchronized (SESSIONS) {
            s = SESSIONS.get(player);
            if (s == null) {
                s = new Session(player, player.m_20068_());
                SESSIONS.put(player, s);
            }
            s.pending++;
            if (kind == WHISPERWIND) s.whisperwind++;
        }
        wrapped++;
        return new Task(s, task, kind == WHISPERWIND);
    }

    /** After a dash handler's own Player.setNoGravity call; before = isNoGravity() just before it. */
    public static void gravity(Player player, boolean before) {
        if (!enabled || !(player instanceof ServerPlayer)) return;
        boolean after = player.m_20068_();
        synchronized (SESSIONS) {
            Session s = SESSIONS.get(player);
            if (s == null) {
                if (after == before || !after) return;
                s = new Session(player, before);
                SESSIONS.put(player, s);
            }
            s.seen(before, after);
        }
    }

    static final class Task implements Runnable {
        final Session session;
        final Runnable task;
        final boolean whisperwind;
        boolean counted;

        Task(Session session, Runnable task, boolean whisperwind) {
            this.session = session;
            this.task = task;
            this.whisperwind = whisperwind;
        }

        @Override
        public void run() {
            Session s = session;
            if (s.left) {
                skipped++;
                done();
                return;
            }
            boolean before = s.player.m_20068_(), ran = false;
            try {
                task.run();
                ran = true;
            } finally {
                synchronized (SESSIONS) {
                    boolean after = s.player.m_20068_();
                    if (ran) s.seen(before, after);
                    else {
                        s.gravityOff = false;               // a task that threw: nothing of it is undone at logout
                        s.lastSeen = after;
                    }
                }
                done();
            }
        }

        private void done() {
            synchronized (SESSIONS) {
                if (counted) return;
                counted = true;
                Session s = session;
                s.pending--;
                if (whisperwind) s.whisperwind--;
                if (s.pending <= 0 && SESSIONS.get(s.player) == s) SESSIONS.remove(s.player);
            }
        }
    }

    /** Called once from Scheduler's static initialiser (Simply Swords Overhaul's mod constructor). */
    public static synchronized void install() {
        if (tried) return;
        tried = true;
        try {
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayerEvent.PlayerLoggedOutEvent.class, SsoDash::onLoggedOut);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ServerStoppedEvent.class, SsoDash::onServerStopped);
            installed = true;
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: simplyswordsoverhaul_logout_dash_restore could not register its listeners ({}); a player leaving during a dash is saved as before", t.toString());
        }
    }

    /** PlayerLoggedOutEvent (before the player is saved): what the pending dash would still restore, restored now. */
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!enabled) return;
        Player player = event.getEntity();
        Session s;
        synchronized (SESSIONS) {
            s = SESSIONS.remove(player);
        }
        if (s == null) return;
        s.left = true;
        boolean changed = false;
        if (s.gravityOff && player.m_20068_()) {
            player.m_20242_(false);
            changed = true;
        }
        if (s.whisperwind > 0) {
            CompoundTag data = player.getPersistentData();
            if (data.m_128471_(DASHING)) {
                data.m_128473_(DASHING);
                if (data.m_128471_(MOB_KILLED)) data.m_128473_(MOB_KILLED);
                SsoMaps.forgetDashHits(player.m_20148_());
                changed = true;
            }
        }
        if (changed) {
            restored++;
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: simplyswordsoverhaul_logout_dash_restore ended a Simply Swords Overhaul dash for a player who left during it");
            }
        }
    }

    /** ServerStoppedEvent: sessions of players the logout did not reach (none normally) end; their tasks do nothing. */
    public static void onServerStopped(ServerStoppedEvent event) {
        synchronized (SESSIONS) {
            for (Session s : SESSIONS.values()) s.left = true;
            SESSIONS.clear();
        }
    }
}
