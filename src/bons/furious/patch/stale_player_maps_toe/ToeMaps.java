package bons.furious.patch.stale_player_maps_toe;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious fix stale_player_map_purge_tonsofenchants (Tons of Enchants 1.0, MIT; server). Ours; no Tons of Enchants
 * code is carried. Idea: Collections Of Optimizations (vanilla.purgeStalePlayerMaps), idea text only.
 *
 * Tons of Enchants' Excavator enchantment keeps a static HashMap<Player, Boolean> ("is this player excavating") that it
 * writes at the start and the end of every excavation (destroyBlocks: put true, put false) and reads in its BreakEvent
 * handler (excavate: containsKey, get); nothing ever removes a key, so every player who ever excavated stays in the
 * server's memory (inventory, connection, advancements) until a restart, and after a respawn the old player object stays
 * the key. The map is handed to the shared layer of the stale_player_map_purge_* fixes (StalePlayerKeys in the
 * stale_player_maps group, reached by name so this group builds on its own) as a server ID map: when a player object
 * joins a level the key that equals it (same entity id: a respawned player) is swapped to the new object with the same
 * value, and a player's entry goes when it leaves with UNLOADED_WITH_PLAYER (logout, server stop; server entity ids are
 * never handed out again). The BreakEvent fires only on the server, so only the server's map is ever used.
 *
 * Why every answer stays the same: HashMap compares Player keys with Entity.equals / hashCode (the entity id). A swap
 * keeps the key's id and value; a purged id can never be asked again. Players who can still excavate get the same
 * containsKey / get answers, so Excavator does the same thing.
 *
 * -Dbons_and_furious.stalePlayerMapPurgeTonsofenchants=false leaves the map alone. If the shared layer cannot be reached,
 * one WARN and the map stays as Tons of Enchants keeps it.
 */
public final class ToeMaps {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static final String KEY = "stale_player_map_purge_tonsofenchants";
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.stalePlayerMapPurgeTonsofenchants", "true"));
    /** For the harness: whether the map reached the shared layer. */
    public static volatile boolean tracked;

    private ToeMaps() {
    }

    /** From Excavator.<clinit> RETURN (our mixin): hand the map over. Never throws (it runs in a static initializer). */
    public static void track(Map<?, ?> excavating) {
        try {
            ClassLoader cl = ToeMaps.class.getClassLoader();
            Class<?> layer = Class.forName("bons.furious.patch.stale_player_maps.StalePlayerKeys", true, cl);
            @SuppressWarnings({"unchecked", "rawtypes"})
            Class<Enum> kind = (Class<Enum>) Class.forName("bons.furious.patch.stale_player_maps.StalePlayerKeys$Kind", true, cl);
            @SuppressWarnings({"unchecked", "rawtypes"})
            Class<Enum> side = (Class<Enum>) Class.forName("bons.furious.patch.stale_player_maps.StalePlayerKeys$Side", true, cl);
            MethodHandle track = MethodHandles.publicLookup().findStatic(layer, "track",
                    MethodType.methodType(void.class, String.class, String.class, Map.class, kind, side, Object.class, BooleanSupplier.class));
            @SuppressWarnings("unchecked")
            Object id = Enum.valueOf(kind, "ID");
            @SuppressWarnings("unchecked")
            Object server = Enum.valueOf(side, "SERVER");
            BooleanSupplier on = () -> enabled;
            track.invoke(KEY, "Tons of Enchants Excavator.map", excavating, id, server, null, on);
            tracked = true;
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: {} cannot reach the shared player-map layer ({}); Tons of Enchants' excavator map stays as the mod keeps it", KEY, t.toString());
        }
    }
}
