package bons.furious.patch.mahoutsukai_coo49;

import java.util.Map;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious fix mahoutsukai_insight_target_purge (Mahou Tsukai 1.20.1-v1.34.81, All Rights Reserved; server). Ours;
 * no Mahou Tsukai code is carried. Idea: Collections Of Optimizations (mahoutsukai.purgeInsightTargets), idea text only.
 *
 * InsightEyesPotion keeps a public static HashMap<Player, Boolean> hadTargets ("this player is being shown an Insight Eyes
 * readout"). Its only user is InsightEyesPotion.insightEyesPlayerTick, which ServerHandler.tickCheck calls for players on
 * server levels every ten ticks: it puts the player while a target is shown and removes it on the first such tick without
 * one (sending the "readout off" packet). A player who logs out while a target is shown is never ticked again, so the key
 * (the ServerPlayer object, with its connection, inventory and level) stays for the JVM's life; in single player that
 * pins the integrated server's player and through it its world after quit-to-title.
 *
 * The fix: InsightEyesPotion's static initializer hands the map over (MahouInsightMapMixin) and two listeners keep it
 * small: PlayerLoggedOutEvent (PlayerList.remove: logout, quit-to-title, every player at server stop) removes the leaving
 * player's entry, and ServerStoppedEvent empties what is left. Why every answer stays the same: HashMap compares Player
 * keys by Entity.equals / hashCode (the entity id); a player that logged out is never ticked again, and server entity ids
 * are never handed out again in a JVM (a returning player is a new ServerPlayer with a new id), so insightEyesPlayerTick
 * gets the same containsKey / get answers for every player it can still tick. A respawned player keeps its id, so its
 * entry (under the old object) is still found and removed at its logout. Both listeners run on the server thread, the
 * map's only user. -Dbons_and_furious.mahoutsukaiInsightTargetPurge=false leaves the map alone; if the listeners cannot
 * be registered, one WARN and the map stays as Mahou Tsukai keeps it.
 */
public final class MahouInsightMap {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static final String KEY = "mahoutsukai_insight_target_purge";
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mahoutsukaiInsightTargetPurge", "true"));
    /** The mod's map, handed over by its static initializer (null until then). */
    static volatile Map<?, ?> map;
    /** For harnesses (plain fields): entries removed at logout, entries removed at server stop. */
    public static long removedAtLogout, removedAtStop;
    private static volatile boolean registered, announced;

    private MahouInsightMap() {
    }

    /** From InsightEyesPotion.<clinit> RETURN (our mixin): keep the map and register the listeners. Never throws. */
    public static void track(Map<?, ?> hadTargets) {
        map = hadTargets;
        register();
    }

    private static synchronized void register() {
        if (registered) return;
        registered = true;
        try {
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, PlayerEvent.PlayerLoggedOutEvent.class, MahouInsightMap::onLoggedOut);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, ServerStoppedEvent.class, MahouInsightMap::onStopped);
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: {} could not register its listeners ({}); Mahou Tsukai's insight target map stays as the mod keeps it", KEY, t.toString());
        }
    }

    /** PlayerLoggedOutEvent: the leaving player's entry goes. */
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Map<?, ?> m = map;
        if (!enabled || m == null || event == null) return;
        Player player = event.getEntity();
        if (player == null || !m.containsKey(player)) return;
        m.remove(player);
        removedAtLogout++;
        announce();
    }

    /** ServerStoppedEvent: nothing can be ticked any more; whatever is left goes. */
    public static void onStopped(ServerStoppedEvent event) {
        Map<?, ?> m = map;
        if (!enabled || m == null || m.isEmpty()) return;
        removedAtStop += m.size();
        m.clear();
        announce();
    }

    private static void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: {} applies (Mahou Tsukai's insight target map no longer keeps players that left)", KEY);
    }
}
