package bons.furious.patch.stale_player_maps;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious, group stale_player_maps (since 1.0.45): the shared layer of the five stale_player_map_purge_* fixes
 * (Unique Dungeons, TreeChop, Celestisynth, Brutality, More Artifacts). Ours; no code of any target is carried. Idea:
 * Collections Of Optimizations (vanilla.purgeStalePlayerMaps), idea text only; the event-driven, id-aware design is ours.
 *
 * Several mods keep static maps keyed by Player objects and never remove a key, so every player object that ever reaches
 * them stays alive (its inventory, connection, advancements, and on the client its whole ClientLevel). Each target class
 * hands its maps over from its static initializer (a mixin per mod, guarded per mod); this class then keeps them small:
 *
 * Two kinds of map, because Entity.equals/hashCode compare only the entity id:
 *  - ID maps (HashMap keyed by Player): every lookup answers by entity id. A respawned or dimension-changed player has the
 *    old player's id (PlayerList.respawn and ClientPacketListener.handleRespawn copy it with setId), so stock hands the old
 *    entry to the new object while the OLD object stays the key. Such an entry may never be dropped while its id can still
 *    come back, so these maps only ever see:
 *      T4 key swap: when a player object joins a level and the map holds an equal key, the key object is replaced by the
 *         joining object (remove + put, the same value). Every lookup answers exactly as before (same id, same value); the
 *         old player object is no longer held by the map.
 *      T1 logout purge (server): when a player leaves a server level with removal reason UNLOADED_WITH_PLAYER (logout and
 *         server stop) its entry goes. Server entity ids come from one counter per JVM and are never handed out again, so no
 *         later player can equal that key. A dimension change (CHANGED_DIMENSION, the same object comes back) and a respawn
 *         (DISCARDED, the successor has the same id) remove nothing.
 *      T3 session end (client): when the client has no level and no integrated server any more, the client's (and a stopped
 *         integrated server's) entries go. Nothing can look them up then; a player of a LATER session with a colliding id
 *         (another server or a restarted one hands out ids from 0 again) no longer inherits an old session's entry - the only
 *         answer that changes, and it is the bug's own symptom.
 *  - IDENTITY maps (IdentityHashMap keyed by client players): a removed player object is never looked up again (readers
 *    take players from the current level), so its entry goes as soon as it leaves its level (T5), when its level is replaced
 *    by a dimension change (T6, this also catches the old LocalPlayer, which is never flagged removed), and at session end.
 *
 * Threads: a map used by one side is changed only from that side's events (its own thread). A map both sides use is
 * changed under the lock of its accessor wrapper (Unique Dungeons) or, when it has none (More Artifacts), only while one
 * thread can touch it (a dedicated server, or a client without an integrated server); otherwise its purge waits for T3.
 * FakePlayers never join a level and keep stock behaviour. Every listener catches its own failures: a map whose handling
 * fails is left as stock from then on (one WARN).
 */
public final class StalePlayerKeys {
    static final Logger LOGGER = LogManager.getLogger("Bons and Furious");

    public enum Kind { ID, IDENTITY }

    public enum Side { SERVER, CLIENT, BOTH }

    /** One map of a target mod. */
    public static final class Tracked {
        final String key, name;
        final Map<Object, Object> map;
        final Kind kind;
        final Side side;
        final Object lock;
        final BooleanSupplier enabled;
        /**
         * ID maps only, client only (null = never): true while a departed REMOTE player's entry may go at once because its
         * value never holds state a fresh value would not (Unique Dungeons: remote players' StackCooldowns never get a cooldown
         * on the client; if that player comes back, the new empty value answers exactly as the old empty one). Asked at use:
         * the premise is guarded by its own client-only key (its members exist only in the client jar).
         */
        final BooleanSupplier inertRemote;
        volatile boolean failed;

        Tracked(String key, String name, Map<Object, Object> map, Kind kind, Side side, Object lock, BooleanSupplier enabled, BooleanSupplier inertRemote) {
            this.key = key;
            this.name = name;
            this.map = map;
            this.kind = kind;
            this.side = side;
            this.lock = lock;
            this.enabled = enabled;
            this.inertRemote = inertRemote;
        }

        boolean on() {
            return !failed && enabled.getAsBoolean();
        }

        boolean inert() {
            return inertRemote != null && inertRemote.getAsBoolean();
        }

        public String toString() {
            return name;
        }
    }

    static final List<Tracked> TRACKED = new CopyOnWriteArrayList<>();
    /** True on a dedicated server (no client thread exists). Not final: the harness checks both kinds of JVM. */
    static volatile boolean dedicated = dist(true);
    /** True on a client (the client listeners are registered). */
    static volatile boolean client = dist(false);
    public static final AtomicLong SWAPS = new AtomicLong(), PURGED = new AtomicLong();
    /** Server entity ids purged at logout (T1); shadow mode checks that no lookup ever asks for one again. */
    static final Set<Integer> RETIRED_SERVER_IDS = ConcurrentHashMap.newKeySet();
    private static final Set<String> ANNOUNCED = ConcurrentHashMap.newKeySet();
    private static volatile boolean registered;

    private StalePlayerKeys() {
    }

    /** From a target class's static initializer (our mixin): hand over one map. Registers the listeners once. */
    public static void track(String key, String name, Map<?, ?> map, Kind kind, Side side, Object lock, BooleanSupplier enabled) {
        track(key, name, map, kind, side, lock, enabled, null);
    }

    @SuppressWarnings("unchecked")
    public static void track(String key, String name, Map<?, ?> map, Kind kind, Side side, Object lock, BooleanSupplier enabled, BooleanSupplier inertRemote) {
        if (map == null) return;
        TRACKED.add(new Tracked(key, name, (Map<Object, Object>) map, kind, side, lock, enabled, kind == Kind.ID ? inertRemote : null));
        register();
    }

    private static boolean dist(boolean dedicatedServer) {
        try {
            return FMLEnvironment.dist != null && (dedicatedServer ? FMLEnvironment.dist.isDedicatedServer() : FMLEnvironment.dist.isClient());
        } catch (Throwable t) {
            return false;
        }
    }

    /** Runs inside a target class's static initializer: a failure here must never fail that class (the maps then stay as stock). */
    static synchronized void register() {
        if (registered) return;
        registered = true;
        try {
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, EntityJoinLevelEvent.class, StalePlayerKeys::onJoin);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityLeaveLevelEvent.class, StalePlayerKeys::onLeave);
            if (client) StalePlayerKeysClient.register();
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: stale_player_map_purge could not register its listeners ({}); the player maps stay as the mods keep them", t.toString());
        }
    }

    /** May an event of this side change this map now (see the class comment on threads)? */
    static boolean mayWrite(Tracked t, boolean clientSide) {
        switch (t.side) {
            case SERVER:
                return !clientSide;
            case CLIENT:
                return clientSide;
            default:
                if (t.lock != null) return true;
                if (dedicated) return !clientSide;
                return clientSide && !StalePlayerKeysClient.integratedServerRunning();
        }
    }

    /** T4: a player object joins a level; ID maps holding an equal key now hold this object instead (same value). */
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        boolean clientSide = event.getLevel().m_5776_();
        for (Tracked t : TRACKED) {
            if (t.kind != Kind.ID || !t.on() || !mayWrite(t, clientSide)) continue;
            try {
                if (t.lock != null) {
                    synchronized (t.lock) {
                        swap(t, player);
                    }
                } else {
                    swap(t, player);
                }
            } catch (Throwable e) {
                fail(t, e);
            }
        }
    }

    static void swap(Tracked t, Player player) {
        Map<Object, Object> m = t.map;
        if (!m.containsKey(player)) return;
        Object value = m.remove(player);
        m.put(player, value);
        SWAPS.incrementAndGet();
        announce(t);
    }

    /**
     * T1 (server logout) and T5 (client: the object left its level: identity maps; and inert-remote ID maps when it is not
     * the local player - the local player's old object leaves after its successor took over the key, with the same id).
     */
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getLevel().m_5776_()) {
            int localId = Integer.MIN_VALUE;
            boolean localKnown = false;
            for (Tracked t : TRACKED) {
                if (!t.on() || !mayWrite(t, true)) continue;
                if (t.kind == Kind.ID && t.inertRemote == null) continue;
                try {
                    if (t.kind == Kind.ID) {
                        // with an integrated server the map is shared: a guest's entry is also the server's (same id);
                        // the premise (its own client-only key) is asked only where the purge can act
                        if (StalePlayerKeysClient.integratedServerRunning() || !t.inert()) continue;
                        if (!localKnown) {
                            localId = StalePlayerKeysClient.localPlayerId();
                            localKnown = true;
                        }
                        if (player.m_19879_() == localId) continue;
                    }
                    if (t.lock != null) {
                        synchronized (t.lock) {
                            purge(t, player);
                        }
                    } else {
                        purge(t, player);
                    }
                } catch (Throwable e) {
                    fail(t, e);
                }
            }
            return;
        }
        if (player.m_146911_() != Entity.RemovalReason.UNLOADED_WITH_PLAYER) return;   // dimension change, respawn: the id lives on
        if (UniqueDungeonsMaps.SHADOW) RETIRED_SERVER_IDS.add(player.m_19879_());
        for (Tracked t : TRACKED) {
            if (t.kind != Kind.ID || !t.on() || !mayWrite(t, false)) continue;
            if (t.side == Side.BOTH && !dedicated) continue;   // an integrated server's client may still hold this id: T3 purges it
            try {
                if (t.lock != null) {
                    synchronized (t.lock) {
                        purge(t, player);
                    }
                } else {
                    purge(t, player);
                }
            } catch (Throwable e) {
                fail(t, e);
            }
        }
    }

    static void purge(Tracked t, Object key) {
        if (!t.map.containsKey(key)) return;
        t.map.remove(key);
        PURGED.incrementAndGet();
        announce(t);
    }

    /** T3 (client, session over): every entry of the client-side and both-side maps goes; no player can look them up. */
    static void sessionEnded() {
        for (Tracked t : TRACKED) {
            if (t.side == Side.SERVER || !t.on()) continue;
            try {
                int n;
                if (t.lock != null) {
                    synchronized (t.lock) {
                        n = t.map.size();
                        t.map.clear();
                    }
                } else {
                    n = t.map.size();
                    t.map.clear();
                }
                if (n > 0) {
                    PURGED.addAndGet(n);
                    announce(t);
                }
            } catch (Throwable e) {
                fail(t, e);
            }
        }
    }

    static void fail(Tracked t, Throwable e) {
        if (!t.failed) {
            t.failed = true;
            LOGGER.warn("Bons and Furious: {}: handling {} failed ({}); that map is left as the mod keeps it from now on", t.key, t.name, e.toString());
        }
    }

    static void announce(Tracked t) {
        if (ANNOUNCED.add(t.key))
            LOGGER.info("Bons and Furious: {} applies (player maps of {} no longer keep players that left; first: {})", t.key, t.key.substring("stale_player_map_purge_".length()), t.name);
    }

    /** For the harness and the shadow checks: the tracked maps. */
    public static List<Tracked> tracked() {
        return TRACKED;
    }
}
