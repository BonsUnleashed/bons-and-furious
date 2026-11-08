package bons.furious.patch.ironsspellbooks_coo49;

import java.util.Map;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious fix irons_spellbooks_synced_data_session_clear (Iron's Spells 'n Spellbooks, All Rights Reserved: no
 * Iron's code is carried; 1.21.1 tested build 1.21.1-3.16.3; client). Ours. Idea: Collections Of Optimizations
 * (ironsspellbooks.clearStaleSyncedData), idea text only. Loaded only on a client (its mixin is in the client list).
 *
 * ClientMagicData keeps a private static HashMap<Integer, SyncedSpellData> playerSyncedDataLookup: Iron's player sync
 * packet puts the entry of a player's entity id (SyncPlayerDataPacket.handle -> enqueueWork -> handlePlayerSyncedData),
 * getSyncedSpellData reads it for every Player (getOrDefault), and nothing ever removes an entry: Iron's own logout handler
 * (ClientPlayerEvents.onPlayerLogOut) resets other state only. The map grows for the whole time the game runs, and because
 * every world or server hands out entity ids from low numbers again, a player of the next session with a reused id is drawn
 * with the old occupant's state (a casting pose, a spin-attack trail, frustum culling skipped) until a packet for that
 * player arrives.
 *
 * The fix: ClientMagicData's static initializer hands the map over (IronsSyncedDataMixin) and two listeners empty it, on
 * ClientPlayerNetworkEvent.LoggingOut (the session is over: nothing can look it up) and on .LoggingIn (posted from
 * ClientPacketListener.handleLogin on the client thread, before any later packet of the new session is handled, so no entry
 * of the new session is ever lost; it also catches a session that ended without a logout event). Inside a session nothing
 * changes. Entries built from packets hold no entity, so nothing else is kept alive by them. Both listeners run on the
 * client thread, which is where Iron's handles its sync packets (enqueueWork).
 * -Dbons_and_furious.ironsSpellbooksSyncedDataSessionClear=false leaves the map alone; if the listeners cannot be
 * registered, one WARN and the map stays as Iron's keeps it.
 */
public final class IronsSyncedData {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static final String KEY = "irons_spellbooks_synced_data_session_clear";
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.ironsSpellbooksSyncedDataSessionClear", "true"));
    /** The mod's map, handed over by its static initializer (null until then). */
    static volatile Map<?, ?> map;
    /** For harnesses (plain field): entries removed at a session edge. */
    public static long cleared;
    private static volatile boolean registered, announced;

    private IronsSyncedData() {
    }

    /** From ClientMagicData.<clinit> RETURN (our mixin): keep the map and register the listeners. Never throws. */
    public static void track(Map<?, ?> playerSyncedDataLookup) {
        map = playerSyncedDataLookup;
        register();
    }

    private static synchronized void register() {
        if (registered) return;
        registered = true;
        try {
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, ClientPlayerNetworkEvent.LoggingOut.class, IronsSyncedData::onLoggingOut);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, ClientPlayerNetworkEvent.LoggingIn.class, IronsSyncedData::onLoggingIn);
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: {} could not register its listeners ({}); Iron's synced player data stays as the mod keeps it", KEY, t.toString());
        }
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        clear();
    }

    /** Empties the map (a session edge). */
    static void clear() {
        Map<?, ?> m = map;
        if (!enabled || m == null || m.isEmpty()) return;
        cleared += m.size();
        m.clear();
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: {} applies (Iron's synced player spell data is emptied when you leave or join a world)", KEY);
        }
    }
}
